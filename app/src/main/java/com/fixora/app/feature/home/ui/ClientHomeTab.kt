package com.fixora.app.feature.home.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraLogo
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.map.domain.ServiceCategory
import com.fixora.app.feature.map.ui.MapLaunch
import com.fixora.app.feature.map.ui.iconRes

private const val SERVICE_COLUMNS = 4
private val MapGround = Color(0xFFE9EEF3)
private val UserBlue = Color(0xFF2563EB)

/** Con más servicios que estos, el resto se ve con «Ver todos». */
private const val COLLAPSED_SERVICES = 8

/** Pestaña Inicio del cliente (ANDROID-12). */
@Composable
fun ClientHomeTab(
    onOpenMap: (MapLaunch) -> Unit,
    viewModel: ClientHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ClientHomeContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = { viewModel.searchLaunch()?.let(onOpenMap) },
        onCategory = { onOpenMap(viewModel.categoryLaunch(it)) },
        onNearby = { onOpenMap(MapLaunch()) },
        onRetry = viewModel::retry,
    )
}

@Composable
private fun ClientHomeContent(
    state: ClientHomeUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCategory: (ServiceCategory) -> Unit,
    onNearby: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        FixoraLogo(color = Navy900, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(20.dp))
        Text(
            text = state.firstName?.let { stringResource(R.string.client_home_greeting, it) }
                ?: stringResource(R.string.client_home_greeting_plain),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Navy900,
        )
        Text(
            stringResource(R.string.client_home_question),
            style = MaterialTheme.typography.bodyLarge,
            color = Slate500,
        )
        Spacer(Modifier.height(16.dp))
        HomeSearchField(state.query, onQueryChange, onSearch)

        Spacer(Modifier.height(28.dp))
        ServicesSection(state, onCategory, onRetry)

        Spacer(Modifier.height(28.dp))
        SectionTitle(stringResource(R.string.client_home_nearby))
        Spacer(Modifier.height(12.dp))
        NearbyMapCard(onClick = onNearby)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = Navy900,
        modifier = modifier,
    )
}

/** Buscar abre el mapa: con el servicio si lo escrito es uno, si no por nombre del negocio. */
@Composable
private fun HomeSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    val focus = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Mist50)
            .border(1.dp, Mist200, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Slate500, modifier = Modifier.size(22.dp))
        Box(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.client_home_search_hint), style = MaterialTheme.typography.bodyLarge, color = Slate500)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Navy900),
                cursorBrush = SolidColor(Navy900),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    onSearch()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ServicesSection(state: ClientHomeUiState, onCategory: (ServiceCategory) -> Unit, onRetry: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canExpand = state.categories.size > COLLAPSED_SERVICES
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.client_home_services), Modifier.weight(1f))
        if (canExpand) {
            Text(
                text = stringResource(if (expanded) R.string.client_home_see_less else R.string.client_home_see_all),
                style = MaterialTheme.typography.labelLarge,
                color = Navy800,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    when {
        state.categories.isNotEmpty() -> {
            val shown = if (expanded || !canExpand) state.categories else state.categories.take(COLLAPSED_SERVICES)
            ServicesGrid(shown, onCategory)
        }
        state.error != null -> LoadErrorNotice(state.error, onRetry = onRetry)
        else -> CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ServicesGrid(categories: List<ServiceCategory>, onCategory: (ServiceCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.chunked(SERVICE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { category ->
                    ServiceTile(category, onClick = { onCategory(category) }, modifier = Modifier.weight(1f))
                }
                repeat(SERVICE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ServiceTile(category: ServiceCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        IconCircle(category.iconRes())
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = Navy900,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ícono azul marino sobre un círculo gris claro; mismo estilo para todos los servicios. */
@Composable
private fun IconCircle(@DrawableRes icon: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .background(Mist50, CircleShape)
            .border(1.dp, Mist200, CircleShape),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Navy800, modifier = Modifier.size(24.dp))
    }
}

/**
 * «Cerca de ti»: un mapa dibujado (calles y marcadores, como en el diseño) que abre la pestaña Mapa.
 * No es un mapa real para no pedir la ubicación ni cargar mapas en el Inicio.
 */
@Composable
private fun NearbyMapCard(onClick: () -> Unit) {
    val label = stringResource(R.string.client_home_map_title)
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MapGround)
            .border(1.dp, Mist200, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val street = 12.dp.toPx()
            drawLine(White, Offset(0f, h * 0.22f), Offset(w, h * 0.3f), strokeWidth = street)
            drawLine(White, Offset(0f, h * 0.66f), Offset(w, h * 0.6f), strokeWidth = street)
            drawLine(White, Offset(w * 0.2f, 0f), Offset(w * 0.14f, h), strokeWidth = street)
            drawLine(White, Offset(w * 0.5f, 0f), Offset(w * 0.56f, h), strokeWidth = street)
            drawLine(White, Offset(w * 0.82f, 0f), Offset(w * 0.78f, h), strokeWidth = street)
            listOf(Offset(0.3f, 0.16f), Offset(0.7f, 0.2f), Offset(0.9f, 0.72f), Offset(0.32f, 0.82f)).forEach {
                val at = Offset(w * it.x, h * it.y)
                drawCircle(White, radius = 9.dp.toPx(), center = at)
                drawCircle(Navy900, radius = 7.dp.toPx(), center = at)
            }
            val me = Offset(w * 0.4f, h * 0.44f)
            drawCircle(UserBlue.copy(alpha = 0.18f), radius = 18.dp.toPx(), center = me)
            drawCircle(White, radius = 9.dp.toPx(), center = me)
            drawCircle(UserBlue, radius = 6.5.dp.toPx(), center = me)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(bottom = 14.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(White)
                .padding(start = 16.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Navy900)
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Navy900, modifier = Modifier.size(18.dp))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ClientHomeTabPreview() {
    FixoraTheme {
        ClientHomeContent(
            state = ClientHomeUiState(
                isLoading = false,
                firstName = "Misael",
                categories = listOf(
                    ServiceCategory("1", "Plomería", "plomeria"),
                    ServiceCategory("2", "Electricidad", "electricidad"),
                    ServiceCategory("3", "Cerrajería", "cerrajeria"),
                    ServiceCategory("4", "Climatización", "climatizacion"),
                    ServiceCategory("5", "Pintura e Impermeabilización", "pintura-impermeabilizacion"),
                    ServiceCategory("6", "Carpintería", "carpinteria"),
                    ServiceCategory("7", "Albañilería", "albanileria"),
                    ServiceCategory("8", "Electrodomésticos", "electrodomesticos"),
                    ServiceCategory("9", "Jardinería", "jardineria"),
                ),
            ),
            onQueryChange = {}, onSearch = {}, onCategory = {}, onNearby = {}, onRetry = {},
        )
    }
}
