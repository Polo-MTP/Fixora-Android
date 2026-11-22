package com.fixora.app.feature.requestform.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.SystemBarsAppearance
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.map.ui.serviceIcon
import com.fixora.app.feature.providerprofile.domain.ProviderProfile
import com.fixora.app.feature.providerprofile.domain.ProviderService
import com.fixora.app.feature.requestform.domain.AddressChoice
import com.fixora.app.feature.requestform.domain.RequestFormRules
import com.fixora.app.feature.requestform.domain.TimeSlot
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Spanish = Locale.forLanguageTag("es-MX")
private val SlotFormat = DateTimeFormatter.ofPattern("H:mm", Spanish)

/** Formulario de solicitud y agendar cita (ANDROID-15, Figma «Fixora - Solicitar servicio»). */
@Composable
fun RequestFormScreen(
    onBack: () -> Unit,
    onSent: (requestId: String) -> Unit,
    onAddAddress: () -> Unit,
    viewModel: RequestFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sentMessage = stringResource(R.string.request_form_sent)
    LaunchedEffect(state.createdRequestId) {
        state.createdRequestId?.let {
            Toast.makeText(context, sentMessage, Toast.LENGTH_SHORT).show()
            onSent(it)
        }
    }

    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        cameraUri?.takeIf { taken }?.let { viewModel.onPhotosPicked(listOf(it)) }
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(RequestFormRules.MAX_PHOTOS)) { uris ->
        viewModel.onPhotosPicked(uris)
    }
    val noCamera = stringResource(R.string.profile_photo_no_camera)

    RequestFormContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onService = viewModel::onServiceSelected,
        onProblem = viewModel::onProblemSelected,
        onDetails = viewModel::onDetailsChange,
        onPickPhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onTakePhoto = {
            val uri = newRequestPhotoUri(context)
            cameraUri = uri
            try {
                takePicture.launch(uri)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, noCamera, Toast.LENGTH_SHORT).show()
            }
        },
        onRemovePhoto = viewModel::onPhotoRemoved,
        onWhenMode = viewModel::onWhenModeChange,
        onDay = viewModel::onDaySelected,
        onSlot = viewModel::onSlotSelected,
        onRetrySlots = viewModel::retrySlots,
        onAddress = viewModel::onAddressSelected,
        onCurrentLocation = viewModel::onUseCurrentLocation,
        onAddAddress = onAddAddress,
        onSend = viewModel::send,
    )
}

@Composable
private fun RequestFormContent(
    state: RequestFormUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onService: (String) -> Unit,
    onProblem: (String) -> Unit,
    onDetails: (String) -> Unit,
    onPickPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    onWhenMode: (WhenMode) -> Unit,
    onDay: (LocalDate) -> Unit,
    onSlot: (TimeSlot) -> Unit,
    onRetrySlots: () -> Unit,
    onAddress: (String) -> Unit,
    onCurrentLocation: () -> Unit,
    onAddAddress: () -> Unit,
    onSend: () -> Unit,
) {
    SystemBarsAppearance(lightIcons = false)
    var choosingAddress by rememberSaveable { mutableStateOf(false) }
    var choosingPhotoSource by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Header(onBack)
        val provider = state.provider
        val loadError = state.loadError
        when {
            provider != null -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                ) {
                    ProviderCard(provider, state.service, state.distanceKm)
                    if (provider.services.size > 1) {
                        Section(stringResource(R.string.request_form_service)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                provider.services.forEach { service ->
                                    Chip("${service.name} · $${service.basePrice.removeSuffix(".00")}", service.id == state.serviceId) { onService(service.id) }
                                }
                            }
                        }
                    }
                    if (state.service != null) {
                        Section(stringResource(R.string.request_form_problem)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.suggestions.forEach { suggestion -> Chip(suggestion, suggestion == state.problem) { onProblem(suggestion) } }
                            }
                            DetailsField(state.details, onDetails)
                        }
                    }
                    Section(stringResource(R.string.request_form_photos), trailing = stringResource(R.string.request_form_optional)) {
                        Photos(state, onAdd = { choosingPhotoSource = true }, onRemove = onRemovePhoto)
                        if (state.photoUnreadable) Text(stringResource(R.string.add_work_photo_unreadable), style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                    }
                    Section(stringResource(R.string.request_form_when)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(stringResource(R.string.request_form_when_now), state.whenMode == WhenMode.Now) { onWhenMode(WhenMode.Now) }
                            Chip(stringResource(R.string.request_form_when_schedule), state.whenMode == WhenMode.Scheduled) { onWhenMode(WhenMode.Scheduled) }
                        }
                        if (state.whenMode == WhenMode.Scheduled) Schedule(state, onDay, onSlot, onRetrySlots)
                    }
                    Section(stringResource(R.string.request_form_address)) {
                        AddressRow(state, onChange = { choosingAddress = true })
                        if (state.currentAddressFailed) {
                            Text(stringResource(R.string.request_form_current_failed), style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                        }
                    }
                }
                Footer(state, onSend)
            }
            loadError != null -> Column(Modifier.padding(20.dp)) { LoadErrorNotice(loadError, onRetry = onRetry) }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
    }

    if (choosingAddress) {
        AddressSheet(
            state = state,
            onDismiss = { choosingAddress = false },
            onSelect = {
                choosingAddress = false
                onAddress(it)
            },
            onCurrent = {
                choosingAddress = false
                onCurrentLocation()
            },
            onAdd = {
                choosingAddress = false
                onAddAddress()
            },
        )
    }
    if (choosingPhotoSource) {
        AlertDialog(
            onDismissRequest = { choosingPhotoSource = false },
            title = { Text(stringResource(R.string.request_form_add_photo)) },
            confirmButton = {
                TextButton(onClick = {
                    choosingPhotoSource = false
                    onPickPhotos()
                }) { Text(stringResource(R.string.profile_photo_pick)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosingPhotoSource = false
                    onTakePhoto()
                }) { Text(stringResource(R.string.profile_photo_take)) }
            },
        )
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Mist50)
                .clickable(role = Role.Button, onClick = onBack),
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_forward),
                contentDescription = stringResource(R.string.auth_back),
                tint = Navy900,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(180f),
            )
        }
        Text(
            stringResource(R.string.request_form_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Navy900,
        )
    }
}

@Composable
private fun ProviderCard(provider: ProviderProfile, service: ProviderService?, distanceKm: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Mist50)
            .padding(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Navy900),
        ) {
            Icon(painterResource(serviceIcon("", service?.name ?: provider.services.firstOrNull()?.name.orEmpty())), contentDescription = null, tint = White, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(provider.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Navy900, modifier = Modifier.weight(1f, fill = false))
                if (provider.verified) {
                    Icon(painterResource(R.drawable.ic_verified), contentDescription = stringResource(R.string.provider_profile_verified), tint = SuccessGreen, modifier = Modifier.size(18.dp))
                }
            }
            val km = distanceKm?.toDoubleOrNull()
            val line = listOfNotNull(
                distanceKm?.let { "$it km" },
                km?.let { stringResource(R.string.request_form_eta, ((it / 25.0) * 60).toInt().coerceAtLeast(1)) },
                service?.name.takeIf { distanceKm == null },
            )
            if (line.isNotEmpty()) Text(line.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Slate500)
        }
    }
}

@Composable
private fun Section(title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Navy900, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = Slate500)
        }
        content()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) White else Navy900,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Navy900 else White)
            .border(1.dp, if (selected) Navy900 else Mist200, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun DetailsField(text: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .border(1.dp, Mist200, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        if (text.isEmpty()) {
            Text(stringResource(R.string.request_form_details_hint), style = MaterialTheme.typography.bodyLarge, color = Slate500)
        }
        BasicTextField(
            value = text,
            onValueChange = onChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Navy900),
            cursorBrush = SolidColor(Orange500),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Photos(state: RequestFormUiState, onAdd: () -> Unit, onRemove: (Uri) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        state.photos.forEach { uri ->
            Box(Modifier.size(78.dp)) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Mist200),
                )
                val remove = stringResource(R.string.request_form_remove_photo)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Navy900)
                        .clickable(role = Role.Button) { onRemove(uri) }
                        .semantics { contentDescription = remove },
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (state.canAddPhoto) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, Mist200), RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onAdd),
            ) {
                Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null, tint = Slate500, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.request_form_add), style = MaterialTheme.typography.labelSmall, color = Slate500)
            }
        }
    }
}

@Composable
private fun Schedule(state: RequestFormUiState, onDay: (LocalDate) -> Unit, onSlot: (TimeSlot) -> Unit, onRetry: () -> Unit) {
    val today = state.days.firstOrNull()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        state.days.forEach { day ->
            val label = when (day) {
                today -> stringResource(R.string.request_form_today)
                today?.plusDays(1) -> stringResource(R.string.request_form_tomorrow)
                else -> "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Spanish).replaceFirstChar { it.uppercase() }} ${day.dayOfMonth}"
            }
            Chip(label, day == state.selectedDay) { onDay(day) }
        }
    }
    val slotsError = state.slotsError
    when {
        state.isLoadingSlots -> CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        slotsError != null -> LoadErrorNotice(slotsError, onRetry = onRetry)
        state.slots.isEmpty() -> Text(stringResource(R.string.request_form_no_slots), style = MaterialTheme.typography.bodySmall, color = Slate500)
        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.slots.forEach { slot ->
                Chip(SlotFormat.format(slot.startsAt.atZone(ZoneId.systemDefault())), slot == state.selectedSlot) { onSlot(slot) }
            }
        }
    }
}

@Composable
private fun AddressRow(state: RequestFormUiState, onChange: () -> Unit) {
    val saved = state.selectedAddress
    val current = state.currentAddress.takeIf { state.addressChoice == AddressChoice.Current }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(
            painterResource(if (current != null) R.drawable.ic_my_location else R.drawable.ic_nav_home),
            contentDescription = null,
            tint = Navy900,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            when {
                saved != null -> {
                    Text(saved.alias.ifBlank { saved.street }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Navy900)
                    Text("${saved.streetLine}, ${saved.colony}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                current != null -> {
                    Text(stringResource(R.string.request_form_current_location), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Navy900)
                    Text(current.line, style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                state.isResolvingCurrent -> Text(stringResource(R.string.request_form_locating), style = MaterialTheme.typography.bodyMedium, color = Slate500)
                else -> Text(stringResource(R.string.request_form_no_address), style = MaterialTheme.typography.bodyMedium, color = Slate500)
            }
        }
        Text(
            stringResource(if (saved != null || current != null) R.string.request_form_change else R.string.request_form_choose),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Orange500,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onChange)
                .padding(8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSheet(
    state: RequestFormUiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onCurrent: () -> Unit,
    onAdd: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = White,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.request_form_address_title), style = MaterialTheme.typography.titleLarge, color = Navy900)
            AddressOption(
                icon = R.drawable.ic_my_location,
                title = stringResource(R.string.request_form_current_location),
                subtitle = stringResource(R.string.request_form_current_hint),
                selected = state.addressChoice == AddressChoice.Current,
                onClick = onCurrent,
            )
            state.addresses.forEach { address: Address ->
                val id = address.id ?: return@forEach
                AddressOption(
                    icon = R.drawable.ic_nav_home,
                    title = address.alias.ifBlank { address.street },
                    subtitle = "${address.streetLine}, ${address.colony}",
                    selected = state.addressChoice == AddressChoice.Saved(id),
                    onClick = { onSelect(id) },
                )
            }
            AddressOption(
                icon = R.drawable.ic_place,
                title = stringResource(R.string.request_form_add_address),
                subtitle = stringResource(R.string.request_form_add_address_hint),
                selected = false,
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun AddressOption(@DrawableRes icon: Int, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Navy900 else Mist200, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Navy800, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Navy900)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate500)
        }
    }
}

@Composable
private fun Footer(state: RequestFormUiState, onSend: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Mist50), RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.provider_profile_price_label), style = MaterialTheme.typography.bodyLarge, color = Slate500, modifier = Modifier.weight(1f))
            Text(
                state.service?.let { "$" + it.basePrice.removeSuffix(".00") } ?: "—",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Navy900,
            )
        }
        Text(stringResource(R.string.request_form_price_note), style = MaterialTheme.typography.bodySmall, color = Slate500)
        val error = state.sendError
        val missing = state.missing
        when {
            error != null -> Text(error.message(), style = MaterialTheme.typography.bodySmall, color = ErrorRed)
            missing != null -> Text(stringResource(missing.hint()), style = MaterialTheme.typography.bodySmall, color = Slate500)
        }
        FixoraPrimaryButton(
            text = stringResource(if (state.whenMode == WhenMode.Scheduled) R.string.request_form_send_scheduled else R.string.request_form_send),
            onClick = onSend,
            enabled = state.missing == null,
            loading = state.isSending,
        )
    }
}

private fun MissingField.hint(): Int = when (this) {
    MissingField.Service -> R.string.request_form_missing_service
    MissingField.Description -> R.string.request_form_missing_description
    MissingField.Address -> R.string.request_form_missing_address
    MissingField.Slot -> R.string.request_form_missing_slot
}

/** Archivo nuevo en la caché para la foto de la cámara; borra los de veces anteriores. */
private fun newRequestPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    dir.listFiles()?.filter { it.name.startsWith("solicitud-") }?.forEach { it.delete() }
    val file = File(dir, "solicitud-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RequestFormPreview() {
    FixoraTheme {
        RequestFormContent(
            state = RequestFormUiState(
                isLoading = false,
                provider = ProviderProfile(
                    id = "2", name = "Matriz", businessName = "Plomería Hernández", independent = true, description = "",
                    logoUrl = null, verified = true, plates = emptyList(), rating = 4.8, reviewsCount = 3, totalJobs = 3,
                    acceptingRequests = true, colony = "", municipality = "", position = null, schedules = emptyList(),
                    services = listOf(ProviderService("1", "Plomería", "350.00", 60)), works = emptyList(), certificates = emptyList(),
                ),
                distanceKm = "1.2",
                serviceId = "1",
                problem = "Fuga de agua",
            ),
            onBack = {}, onRetry = {}, onService = {}, onProblem = {}, onDetails = {}, onPickPhotos = {}, onTakePhoto = {},
            onRemovePhoto = {}, onWhenMode = {}, onDay = {}, onSlot = {}, onRetrySlots = {}, onAddress = {},
            onCurrentLocation = {}, onAddAddress = {}, onSend = {},
        )
    }
}
