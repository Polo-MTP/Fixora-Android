package com.fixora.app.feature.servicerequest.ui.trip

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fixora.app.BuildConfig
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.ErrorRed
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
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.availability.location.FusedLocationSource
import com.fixora.app.feature.map.domain.MapRules
import com.fixora.app.feature.servicerequest.data.TripStep
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import com.fixora.app.feature.servicerequest.ui.incoming.IncomingRequestViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Traslado al cliente (ANDROID-20), como el diseño «Navegación en camino»: el mapa a toda la
 * pantalla con la ruta, y abajo cuánto falta, a quién va y el botón para avanzar. Al terminar
 * muestra el resumen del servicio.
 */
@Composable
fun TripScreen(
    onBack: () -> Unit,
    viewModel: TripViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var confirmingFinish by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> viewModel.onLocationPermissionResult(results.values.any { it }) }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val request = state.request
    when {
        request == null -> Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(Mist50)
                .padding(24.dp),
        ) {
            val error = state.loadError
            if (error != null) LoadErrorNotice(error, onRetry = viewModel::retry)
            else CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        request.status == RequestStatus.Completed -> TripSummary(request, onDone = onBack)
        else -> TripNavigation(
            state = state,
            request = request,
            onBack = onBack,
            onAdvance = { if (state.nextStep == TripStep.Finish) confirmingFinish = true else viewModel.advance() },
            onEnableLocation = { permissionLauncher.launch(FusedLocationSource.LOCATION_PERMISSIONS) },
            onOpenSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
            onOtherApp = {
                request.position?.let { target ->
                    val label = Uri.encode(request.street ?: request.clientName)
                    val uri = "geo:${target.latitude},${target.longitude}?q=${target.latitude},${target.longitude}($label)".toUri()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                        // Sin ninguna app de mapas instalada: no hay a dónde mandarlo.
                    }
                }
            },
        )
    }

    if (confirmingFinish) {
        AlertDialog(
            onDismissRequest = { confirmingFinish = false },
            title = { Text(stringResource(R.string.trip_finish_title)) },
            text = { Text(stringResource(R.string.trip_finish_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingFinish = false
                    viewModel.advance()
                }) { Text(stringResource(R.string.trip_finish_confirm), color = Orange500) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingFinish = false }) { Text(stringResource(R.string.trip_finish_cancel), color = Navy800) }
            },
            containerColor = White,
        )
    }
}

@Composable
private fun TripNavigation(
    state: TripUiState,
    request: ServiceRequest,
    onBack: () -> Unit,
    onAdvance: () -> Unit,
    onEnableLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOtherApp: () -> Unit,
) {
    SystemBarsAppearance(lightIcons = false)
    var sheetHeight by remember { mutableIntStateOf(0) }
    val sheetDp = with(LocalDensity.current) { sheetHeight.toDp() }

    Box(Modifier.fillMaxSize().background(Mist50)) {
        TripMap(
            styleUrl = BuildConfig.MAP_STYLE_URL,
            destination = request.position,
            technician = state.technician,
            route = state.route?.path.orEmpty(),
            topPadding = 120.dp,
            bottomPadding = sheetDp,
            modifier = Modifier.fillMaxSize(),
        )

        // Arriba: regresar y hacia dónde va.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(6.dp, CircleShape)
                    .background(White, CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
            ) {
                Icon(
                    painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = stringResource(R.string.auth_back),
                    tint = Navy900,
                    modifier = Modifier.size(22.dp).rotate(180f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .shadow(8.dp, RoundedCornerShape(18.dp))
                    .background(Navy900, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(painterResource(R.drawable.ic_place), contentDescription = null, tint = White, modifier = Modifier.size(24.dp))
                Column {
                    Text(
                        stringResource(destinationTitle(request.status)),
                        style = MaterialTheme.typography.labelMedium,
                        color = White.copy(alpha = 0.7f),
                    )
                    Text(
                        request.street ?: request.zone.ifBlank { request.clientName },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = White,
                        maxLines = 1,
                    )
                }
            }
        }

        // Abajo: la hoja con lo que falta, el cliente y el botón para avanzar.
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .background(Mist200, CircleShape),
            )
            Headline(state, request)
            ClientRow(request)
            if (state.needsLocation) LocationNotice(state.locationDenied, onEnableLocation, onOpenSettings)
            if (state.nextStep == TripStep.Start || state.nextStep == TripStep.Arrive) {
                OtherAppButton(enabled = request.position != null, onClick = onOtherApp)
            }
            if (state.noPosition) Text(stringResource(R.string.trip_no_position), style = MaterialTheme.typography.bodySmall, color = ErrorRed)
            state.actionError?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
            state.nextStep?.let { step ->
                FixoraPrimaryButton(
                    text = stringResource(
                        when (step) {
                            TripStep.Start -> R.string.trip_start
                            TripStep.Arrive -> R.string.trip_arrived
                            TripStep.Finish -> R.string.trip_finish
                        },
                    ),
                    onClick = onAdvance,
                    loading = state.isAdvancing,
                )
            }
        }
    }
}

private fun destinationTitle(status: RequestStatus): Int = when (status) {
    RequestStatus.OnTheWay -> R.string.trip_heading_to
    RequestStatus.Arrived -> R.string.trip_at
    else -> R.string.trip_destination
}

@Composable
private fun Headline(state: TripUiState, request: ServiceRequest) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (request.isEmergency) {
            Text(
                stringResource(R.string.trip_emergency),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = ErrorRed,
            )
        }
        val route = state.route
        when (request.status) {
            RequestStatus.OnTheWay -> if (route != null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.trip_minutes, (route.durationSeconds / 60).coerceAtLeast(1)),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Navy900,
                    )
                    val km = formatKm(route.distanceMeters / 1000.0)
                    Text(
                        route.arrivesAt?.let { stringResource(R.string.trip_km_arrival, km, formatTime(it)) }
                            ?: stringResource(R.string.trip_km, km),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            } else {
                Text(stringResource(R.string.trip_calculating), style = MaterialTheme.typography.titleLarge, color = Navy900)
            }
            RequestStatus.Arrived -> {
                Text(stringResource(R.string.trip_arrived_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Navy900)
                Text(stringResource(R.string.trip_arrived_text), style = MaterialTheme.typography.bodyMedium, color = Slate500)
            }
            else -> {
                Text(stringResource(R.string.trip_ready_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Navy900)
                val distance = state.technician?.let { here -> request.position?.let { MapRules.distanceKm(here, it) } }
                val text = when {
                    request.scheduledAt != null -> stringResource(R.string.trip_scheduled_for, formatDateTime(request.scheduledAt))
                    distance != null -> stringResource(R.string.trip_estimate, formatKm(distance), IncomingRequestViewModel.etaMinutes(distance))
                    else -> null
                }
                text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Slate500) }
            }
        }
    }
}

@Composable
private fun ClientRow(request: ServiceRequest) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = Mist200)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(Mist50, CircleShape)
                    .border(1.dp, Mist200, CircleShape),
            ) {
                Text(initials(request.clientName), style = MaterialTheme.typography.titleSmall, color = Navy800)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    request.clientName.ifBlank { stringResource(R.string.request_client) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                )
                val address = listOfNotNull(request.street, request.zone.takeIf { it.isNotBlank() }).joinToString(", ")
                if (address.isNotEmpty()) Text(address, style = MaterialTheme.typography.bodySmall, color = Slate500)
                request.addressReference?.let {
                    Text(stringResource(R.string.trip_reference, it), style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
            }
            if (request.categoryName.isNotBlank()) {
                Text(request.categoryName, style = MaterialTheme.typography.labelMedium, color = Navy800)
            }
        }
        HorizontalDivider(color = Mist200)
    }
}

@Composable
private fun OtherAppButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Mist200, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(painterResource(R.drawable.ic_map), contentDescription = null, tint = Navy800, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.trip_other_app), style = MaterialTheme.typography.titleSmall, color = Navy900)
    }
}

/** Sin permiso de ubicación no puede salir ni el cliente verlo llegar. */
@Composable
private fun LocationNotice(denied: Boolean, onEnable: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Orange500, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, tint = Orange500, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.trip_location_title), style = MaterialTheme.typography.titleSmall, color = Navy900)
        }
        Text(
            stringResource(if (denied) R.string.trip_location_denied else R.string.trip_location_text),
            style = MaterialTheme.typography.bodySmall,
            color = Navy900,
        )
        Text(
            stringResource(if (denied) R.string.trip_location_settings else R.string.trip_location_enable),
            style = MaterialTheme.typography.titleSmall,
            color = Orange500,
            modifier = Modifier.clickable(role = Role.Button, onClick = if (denied) onOpenSettings else onEnable),
        )
    }
}

/** Al finalizar: lo que se hizo, para quién y cuánto tardó. */
@Composable
private fun TripSummary(request: ServiceRequest, onDone: () -> Unit) {
    SystemBarsAppearance(lightIcons = false)
    Column(
        Modifier
            .fillMaxSize()
            .background(Mist50)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(SuccessGreen.copy(alpha = 0.12f), CircleShape),
                ) {
                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.trip_summary_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                )
                Text(
                    stringResource(R.string.trip_summary_subtitle, request.id),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500,
                )
            }

            SummaryCard {
                SummaryRow(R.drawable.ic_service_general, R.string.request_service, request.categoryName.ifBlank { "—" })
                SummaryRow(R.drawable.ic_nav_account, R.string.request_client, request.clientName.ifBlank { "—" })
                val address = listOfNotNull(request.street, request.zone.takeIf { it.isNotBlank() }).joinToString(", ")
                if (address.isNotEmpty()) SummaryRow(R.drawable.ic_place, R.string.trip_summary_address, address)
                (request.agreedPrice ?: request.basePrice)?.let {
                    SummaryRow(R.drawable.ic_tune, R.string.request_base_price, "$" + it.removeSuffix(".00"))
                }
            }

            SummaryCard {
                request.acceptedAt?.let { SummaryRow(R.drawable.ic_schedule, R.string.trip_summary_accepted, formatTime(it)) }
                request.arrivedAt?.let { SummaryRow(R.drawable.ic_vehicle, R.string.trip_summary_arrived, formatTime(it)) }
                request.completedAt?.let { SummaryRow(R.drawable.ic_check_circle, R.string.trip_summary_finished, formatTime(it)) }
                val onSite = request.arrivedAt?.let { start -> request.completedAt?.let { end -> Duration.between(start, end) } }
                onSite?.let { SummaryRow(R.drawable.ic_schedule, R.string.trip_summary_on_site, formatDuration(it)) }
            }
        }
        FixoraPrimaryButton(
            text = stringResource(R.string.trip_summary_done),
            onClick = onDone,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun SummaryCard(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(16.dp))
            .border(1.dp, Mist200, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun SummaryRow(@DrawableRes icon: Int, label: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = Navy800, modifier = Modifier.size(20.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = Slate500, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Navy900)
    }
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private fun formatKm(km: Double): String = String.format(Locale.US, "%.1f", km)

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateTimeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es-MX"))

private fun formatTime(instant: Instant): String = TimeFormat.format(instant.atZone(ZoneId.systemDefault()))

private fun formatDateTime(instant: Instant): String = DateTimeFormat.format(instant.atZone(ZoneId.systemDefault()))

private fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
