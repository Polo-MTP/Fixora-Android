package com.fixora.app.feature.servicerequest.ui.client

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.fixora.app.BuildConfig
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.SystemBarsAppearance
import com.fixora.app.core.ui.message
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.payment.ui.PaymentSheet
import com.fixora.app.feature.servicerequest.domain.CancelRules
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import com.fixora.app.feature.servicerequest.ui.trip.TripMap
import com.fixora.app.feature.servicerequest.ui.trip.formatDateTime
import com.fixora.app.feature.servicerequest.ui.trip.formatKm
import com.fixora.app.feature.servicerequest.ui.trip.formatTime

/**
 * Seguimiento del cliente (ANDROID-22), en lugar de «Solicitud aceptada»: el mapa con el técnico
 * moviéndose y su ruta, cuánto le falta, en qué va, sus datos, el chat y cancelar. Al llegar suena
 * un aviso.
 */
@Composable
internal fun ClientTrackingContent(
    state: ClientRequestUiState,
    request: ServiceRequest,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onCancel: () -> Unit,
    onApproveAdjustment: () -> Unit,
    onRejectAdjustment: () -> Unit,
) {
    SystemBarsAppearance(lightIcons = false)
    val context = LocalContext.current
    var sheetHeight by remember { mutableIntStateOf(0) }
    val sheetDp = with(LocalDensity.current) { sheetHeight.toDp() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Mist50),
    ) {
        TripMap(
            styleUrl = BuildConfig.MAP_STYLE_URL,
            destination = request.position,
            technician = state.technicianPosition,
            route = state.route?.path.orEmpty(),
            topPadding = 120.dp,
            bottomPadding = sheetDp,
            modifier = Modifier.fillMaxSize(),
        )

        // Arriba: regresar y en qué va.
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
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(180f),
                )
            }
            Text(
                statusLabel(request.status),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = White,
                modifier = Modifier
                    .weight(1f)
                    .shadow(8.dp, RoundedCornerShape(18.dp))
                    .background(Navy900, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        // Abajo: cuánto falta, el técnico, los ajustes y las acciones.
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.55f)
                    .verticalScroll(rememberScrollState()),
            ) {
                TrackingHeadline(state, request)
                TechnicianCard(request)
                StatusSteps(request.status)
                if (request.adjustments.isNotEmpty()) {
                    AdjustmentSection(
                        request = request,
                        responding = state.respondingAdjustmentId != null,
                        onApprove = onApproveAdjustment,
                        onReject = onRejectAdjustment,
                    )
                }
            }
            if (!state.showCancelSheet) {
                state.actionError?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall, color = EmergencyRed) }
            }
            state.paymentError?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall, color = EmergencyRed) }
            // Chat con el técnico (ANDROID-23); llamar queda como respaldo.
            FixoraPrimaryButton(text = stringResource(R.string.chat_open_technician), onClick = onOpenChat)
            request.branchPhone?.let { phone ->
                SecondaryButton(
                    stringResource(R.string.client_request_call),
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
                        } catch (_: ActivityNotFoundException) {
                            // Sin app de teléfono (por ejemplo una tableta) no hay a dónde llamar.
                        }
                    },
                )
            }
            FixoraTextLink(
                text = stringResource(R.string.client_tracking_cancel),
                onClick = { if (!state.isCanceling) onCancel() },
                color = EmergencyRed,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Lo más importante arriba: cuánto falta mientras va en camino, o en qué va si no. */
@Composable
private fun TrackingHeadline(state: ClientRequestUiState, request: ServiceRequest) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                Text(stringResource(R.string.client_tracking_on_the_way), style = MaterialTheme.typography.bodyMedium, color = Slate500)
            } else {
                Text(stringResource(R.string.trip_calculating), style = MaterialTheme.typography.titleLarge, color = Navy900)
            }
            RequestStatus.Arrived -> {
                Text(
                    stringResource(R.string.client_tracking_arrived_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                )
                Text(stringResource(R.string.client_tracking_arrived_text), style = MaterialTheme.typography.bodyMedium, color = Slate500)
            }
            else -> {
                Text(
                    stringResource(R.string.client_request_accepted_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                )
                val scheduled = request.scheduledAt
                Text(
                    if (scheduled != null) {
                        stringResource(R.string.client_tracking_scheduled_for, formatDateTime(scheduled))
                    } else {
                        stringResource(R.string.client_tracking_preparing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500,
                )
            }
        }
    }
}

/** Ya aceptada, cancelar pide el motivo: se lo manda al técnico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CancelAcceptedSheet(
    isCanceling: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    PaymentSheet(onDismiss = { if (!isCanceling) onDismiss() }) {
        Text(stringResource(R.string.client_tracking_cancel_title), style = MaterialTheme.typography.titleLarge, color = Navy900)
        Text(stringResource(R.string.client_tracking_cancel_hint), style = MaterialTheme.typography.bodyMedium, color = Slate500)
        AuthTextField(
            value = reason,
            onValueChange = { reason = it.take(CancelRules.REASON_MAX_LENGTH) },
            label = stringResource(R.string.trip_cancel_reason),
            placeholder = stringResource(R.string.client_tracking_cancel_placeholder),
            minLines = 3,
            error = stringResource(R.string.trip_cancel_reason_required).takeIf { showErrors && reason.isBlank() },
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EmergencyRed) }
        FixoraPrimaryButton(
            text = stringResource(R.string.client_tracking_cancel_confirm),
            loading = isCanceling,
            onClick = {
                showErrors = true
                if (reason.isNotBlank()) onConfirm(reason)
            },
        )
    }
}

/**
 * Aviso de que el técnico llegó: un sonido de notificación y una vibración corta cada vez que
 * [alerts] sube. Al abrir la pantalla con el técnico ya en el domicilio no suena.
 */
@Composable
internal fun ArrivalAlertEffect(alerts: Int) {
    val context = LocalContext.current
    LaunchedEffect(alerts) {
        if (alerts == 0) return@LaunchedEffect
        runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }
        vibrator(context)?.vibrate(VibrationEffect.createWaveform(ArrivalVibration, -1))
    }
}

private val ArrivalVibration = longArrayOf(0, 300, 150, 300)

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService<Vibrator>()
    }
