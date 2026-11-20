package com.fixora.app.feature.map.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fixora.app.BuildConfig
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.FusedLocationSource
import com.fixora.app.feature.map.domain.MapFilters
import com.fixora.app.feature.map.domain.MapProvider
import com.fixora.app.feature.map.domain.MapRules
import com.fixora.app.feature.map.domain.ServiceCategory
import java.util.Locale

/** Pestaña Mapa del cliente (ANDROID-10, Figma «Fixora - Mapa»). */
@Composable
fun MapTab(
    launch: MapLaunch?,
    onLaunchHandled: () -> Unit,
    onOpenProvider: (branchId: String, distanceKm: String, categoryId: String?) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(launch?.id) {
        if (launch != null) {
            viewModel.onLaunch(launch)
            onLaunchHandled()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> viewModel.onPermissionResult(results.values.any { it }) }
    LaunchedEffect(Unit) {
        viewModel.requestPermission.collect { permissionLauncher.launch(FusedLocationSource.LOCATION_PERMISSIONS) }
    }

    var bottomPanelHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        ProvidersMap(
            styleUrl = BuildConfig.MAP_STYLE_URL,
            providers = state.visibleProviders,
            category = state.selectedCategory,
            selectedId = state.selectedProviderId,
            userLocation = state.userLocation,
            camera = state.camera,
            bottomPadding = with(LocalDensity.current) { bottomPanelHeight.toDp() },
            onSelect = viewModel::onProviderSelected,
            onCameraIdle = viewModel::onCameraIdle,
            modifier = Modifier.fillMaxSize(),
        )
        MapOverlay(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onQuerySubmit = viewModel::onQuerySubmit,
            onCategorySelected = viewModel::onCategorySelected,
            onFiltersApplied = viewModel::onFiltersApplied,
            onSearchHere = viewModel::onSearchHere,
            onMyLocation = viewModel::onMyLocation,
            onCloseProvider = { viewModel.onProviderSelected(null) },
            onOpenProvider = { onOpenProvider(it.id, formatDecimal(it.distanceKm), state.selectedCategoryId) },
            onRetry = viewModel::retry,
            onBottomPanelHeight = { bottomPanelHeight = it },
        )
    }
}

/** Todo lo que va encima del mapa: buscador, servicios, botones y el panel de abajo. */
@Composable
private fun MapOverlay(
    state: MapUiState,
    onQueryChange: (String) -> Unit,
    onQuerySubmit: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    onFiltersApplied: (MapFilters) -> Unit,
    onSearchHere: () -> Unit,
    onMyLocation: () -> Unit,
    onCloseProvider: () -> Unit,
    onOpenProvider: (MapProvider) -> Unit,
    onRetry: () -> Unit,
    onBottomPanelHeight: (Int) -> Unit,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            SearchBar(
                query = state.query,
                filtersActive = state.filters != MapFilters(),
                onQueryChange = onQueryChange,
                onSubmit = onQuerySubmit,
                onOpenFilters = { showFilters = true },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            CategoryChips(state.categories, state.selectedCategoryId, onCategorySelected)
            if (state.canSearchHere) {
                MapPill(text = stringResource(R.string.map_search_here), onClick = onSearchHere)
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            RoundMapButton(
                icon = R.drawable.ic_my_location,
                description = stringResource(R.string.map_my_location),
                onClick = onMyLocation,
                modifier = Modifier.padding(end = 16.dp, bottom = 12.dp),
            )
            BottomPanel(
                state = state,
                onClose = onCloseProvider,
                onOpenProvider = onOpenProvider,
                onRetry = onRetry,
                onEnableLocation = onMyLocation,
                modifier = Modifier.onSizeChanged { onBottomPanelHeight(it.height) },
            )
        }
    }

    if (showFilters) {
        FiltersSheet(
            current = state.filters,
            onDismiss = { showFilters = false },
            onApply = {
                showFilters = false
                onFiltersApplied(it)
            },
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    filtersActive: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(White)
            .padding(start = 16.dp, end = 6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Navy900, modifier = Modifier.size(22.dp))
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 18.dp),
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.map_search_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Navy900,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Navy900),
                cursorBrush = SolidColor(Navy900),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSubmit()
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconTapTarget(R.drawable.ic_close, stringResource(R.string.map_search_clear)) { onQueryChange("") }
        }
        Box {
            IconTapTarget(R.drawable.ic_tune, stringResource(R.string.map_filters), onClick = onOpenFilters)
            if (filtersActive) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(8.dp)
                        .background(Orange500, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun IconTapTarget(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Navy900, modifier = Modifier.size(22.dp))
    }
}

/** «Todos» y un chip por servicio; el elegido va relleno de azul marino. */
@Composable
private fun CategoryChips(
    categories: List<ServiceCategory>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item { CategoryChip(stringResource(R.string.map_category_all), null, selectedId == null) { onSelect(null) } }
        items(categories, key = { it.id }) { category ->
            CategoryChip(category.name, category.iconRes(), selectedId == category.id) { onSelect(category.id) }
        }
    }
}

@Composable
private fun CategoryChip(text: String, @DrawableRes icon: Int?, selected: Boolean, onClick: () -> Unit) {
    val content = if (selected) White else Navy900
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(if (selected) Navy900 else White)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = content)
    }
}

@Composable
private fun MapPill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = White,
        modifier = Modifier
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Navy800)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun RoundMapButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(White)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Navy900, modifier = Modifier.size(24.dp))
    }
}

/** Panel de abajo: el proveedor tocado o, si no hay, cuántos hay y qué hacer. */
@Composable
private fun BottomPanel(
    state: MapUiState,
    onClose: () -> Unit,
    onOpenProvider: (MapProvider) -> Unit,
    onRetry: () -> Unit,
    onEnableLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(White)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 5.dp)
                .background(Mist200, CircleShape),
        )
        val selected = state.selectedProvider
        when {
            selected != null -> {
                ProviderSummary(selected, state.selectedCategory, onClose)
                FixoraPrimaryButton(
                    text = stringResource(R.string.map_provider_open_profile),
                    onClick = { onOpenProvider(selected) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            state.error != null && state.providers.isEmpty() -> LoadErrorNotice(state.error, onRetry = onRetry)
            state.isLoading && state.providers.isEmpty() -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = Navy800, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.map_loading), style = MaterialTheme.typography.bodyMedium, color = Slate500)
            }
            else -> CountSummary(state)
        }
        if (state.usingDefaultCenter && selected == null) {
            Text(
                text = stringResource(R.string.map_location_off),
                style = MaterialTheme.typography.labelSmall,
                color = Navy800,
                modifier = Modifier.clickable(role = Role.Button, onClick = onEnableLocation),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CountSummary(state: MapUiState) {
    val count = state.visibleProviders.size
    if (count == 0) {
        Text(stringResource(R.string.map_providers_empty_title), style = MaterialTheme.typography.titleLarge, color = Navy900)
        Text(
            text = stringResource(R.string.map_providers_empty, state.filters.radiusKm),
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500,
        )
        return
    }
    val title = if (state.filters.availableNow) R.plurals.map_providers_available else R.plurals.map_providers_nearby
    Text(pluralStringResource(title, count, count), style = MaterialTheme.typography.titleLarge, color = Navy900)
    Text(stringResource(R.string.map_providers_hint), style = MaterialTheme.typography.bodyMedium, color = Slate500)
    if (state.total > state.providers.size && state.query.isBlank()) {
        Text(
            text = stringResource(R.string.map_providers_limited, state.providers.size, state.total),
            style = MaterialTheme.typography.labelSmall,
            color = Slate500,
        )
    }
}

/** Datos del negocio tocado en el mapa. */
@Composable
private fun ProviderSummary(provider: MapProvider, category: ServiceCategory?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ProviderAvatar(provider)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = provider.businessName.ifBlank { provider.name },
                style = MaterialTheme.typography.titleMedium,
                color = Navy900,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (provider.independent) stringResource(R.string.map_provider_independent) else provider.name,
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconTapTarget(R.drawable.ic_close, stringResource(R.string.map_provider_close), onClick = onClose)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InfoTag {
            Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = Orange500, modifier = Modifier.size(16.dp))
            Text(
                text = if (provider.reviewsCount == 0) {
                    stringResource(R.string.map_provider_no_reviews)
                } else {
                    stringResource(R.string.map_provider_rating, formatDecimal(provider.rating), provider.reviewsCount)
                },
                style = MaterialTheme.typography.labelLarge,
                color = Navy900,
            )
        }
        InfoTag {
            Text(
                stringResource(R.string.map_provider_distance, formatDecimal(provider.distanceKm)),
                style = MaterialTheme.typography.labelLarge,
                color = Navy900,
            )
        }
        InfoTag {
            Box(Modifier.size(8.dp).background(if (provider.openNow) SuccessGreen else Slate500, CircleShape))
            Text(
                text = stringResource(if (provider.openNow) R.string.map_provider_open else R.string.map_provider_closed),
                style = MaterialTheme.typography.labelLarge,
                color = Navy900,
            )
        }
        if (category != null && provider.basePrice != null) {
            InfoTag {
                Text(
                    stringResource(R.string.map_provider_price, provider.basePrice, category.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = Navy900,
                )
            }
        }
    }
}

@Composable
private fun InfoTag(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(Mist50)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { content() }
}

@Composable
private fun ProviderAvatar(provider: MapProvider) {
    val shape = CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(if (provider.independent) Orange500 else Navy900)
            .border(2.dp, White, shape),
    ) {
        Text(provider.initials, style = MaterialTheme.typography.titleMedium, color = White)
        if (provider.logoUrl != null) {
            AsyncImage(
                model = provider.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Filtros que entiende la API: distancia y solo los abiertos en este momento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(current: MapFilters, onDismiss: () -> Unit, onApply: (MapFilters) -> Unit) {
    var radius by rememberSaveable { mutableIntStateOf(current.radiusKm) }
    var availableNow by rememberSaveable { mutableStateOf(current.availableNow) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = White,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.map_filters),
                    style = MaterialTheme.typography.titleLarge,
                    color = Navy900,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    radius = MapRules.DEFAULT_RADIUS_KM
                    availableNow = false
                }) { Text(stringResource(R.string.map_filters_reset), color = Navy800) }
            }
            Text(stringResource(R.string.map_filters_distance), style = MaterialTheme.typography.titleSmall, color = Navy900)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapRules.RADIUS_OPTIONS_KM.forEach { option ->
                    val selected = option == radius
                    Text(
                        text = stringResource(R.string.map_filters_distance_value, option),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) White else Navy900,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) Navy900 else Mist50)
                            .clickable(role = Role.RadioButton) { radius = option }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.map_filters_available), style = MaterialTheme.typography.titleSmall, color = Navy900)
                    Text(stringResource(R.string.map_filters_available_hint), style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = availableNow,
                    onCheckedChange = { availableNow = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Navy900),
                )
            }
            FixoraPrimaryButton(
                text = stringResource(R.string.map_filters_apply),
                onClick = { onApply(MapFilters(radiusKm = radius, availableNow = availableNow)) },
            )
        }
    }
}

/** Un decimal, con punto: «4.8», «1.2». */
private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MapOverlayPreview() {
    val plumbing = ServiceCategory("1", "Plomería", "plomeria")
    FixoraTheme {
        Box(Modifier.background(Mist200)) {
            MapOverlay(
                state = MapUiState(
                    origin = MapRules.DEFAULT_CENTER,
                    categories = listOf(plumbing, ServiceCategory("2", "Electricidad", "electricidad")),
                    selectedCategoryId = "1",
                    providers = listOf(
                        MapProvider(
                            "1", "Matriz", "Plomería Centro", false, null, Coordinates(19.43, -99.13),
                            1.2, 4.8, 15, "280.00", true,
                        ),
                    ),
                    total = 1,
                    selectedProviderId = "1",
                ),
                onQueryChange = {}, onQuerySubmit = {}, onCategorySelected = {}, onFiltersApplied = {},
                onSearchHere = {}, onMyLocation = {}, onCloseProvider = {}, onOpenProvider = {}, onRetry = {},
                onBottomPanelHeight = {},
            )
        }
    }
}
