package com.fixora.app.feature.servicerequest.ui.client

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy600
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.SystemBarsAppearance
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.map.ui.serviceIcon
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest

private val EmergencyRed = Color(0xFFD32F2F)
private val EndedSlate = Color(0xFF475569)

/** Pantalla de espera y estado de la solicitud del cliente (ANDROID-17). */
@Composable
fun ClientRequestScreen(
    onHome: () -> Unit,
    onResent: (requestId: String) -> Unit,
    onChooseAnother: (categoryId: String?) -> Unit,
    viewModel: ClientRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.resentRequestId) { state.resentRequestId?.let(onResent) }
    ClientRequestContent(
        state = state,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onResend = viewModel::resend,
        onChooseAnother = { onChooseAnother(state.request?.categoryId) },
        onHome = onHome,
    )
}

@Composable
private fun ClientRequestContent(
    state: ClientRequestUiState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onResend: () -> Unit,
    onChooseAnother: () -> Unit,
    onHome: () -> Unit,
) {
    SystemBarsAppearance(lightIcons = true)
    val request = state.request
    val phase = state.phase
    val emergency = request?.isEmergency == true
    val background = when (phase) {
        ClientPhase.Accepted, ClientPhase.Completed -> Brush.verticalGradient(listOf(SuccessGreen, Color(0xFF166B44)))
        ClientPhase.Rejected, ClientPhase.Expired, ClientPhase.Canceled -> Brush.verticalGradient(listOf(EndedSlate, Color(0xFF334155)))
        else -> if (emergency) Brush.verticalGradient(listOf(EmergencyRed, Color(0xFFB71C1C))) else Brush.verticalGradient(listOf(Navy900, Navy600))
    }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Header(state)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(White)
                .navigationBarsPadding(),
        ) {
            val loadError = state.loadError
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp),
            ) {
                when {
                    request != null -> {
                        if (phase == ClientPhase.Accepted || phase == ClientPhase.Completed) TechnicianCard(request)
                        if (phase == ClientPhase.Accepted || phase == ClientPhase.Completed) StatusSteps(request.status)
                        RequestSummary(request)
                    }
                    loadError != null -> LoadErrorNotice(loadError, onRetry = onRetry)
                    else -> CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                state.actionError?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall, color = EmergencyRed) }
            }
            Actions(
                state = state,
                onCancel = { confirmCancel = true },
                onResend = onResend,
                onChooseAnother = onChooseAnother,
                onHome = onHome,
            )
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.client_request_cancel_title)) },
            text = { Text(stringResource(R.string.client_request_cancel_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    onCancel()
                }) { Text(stringResource(R.string.client_request_cancel_confirm), color = EmergencyRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.client_request_keep_waiting)) }
            },
        )
    }
}

@Composable
private fun Header(state: ClientRequestUiState) {
    val request = state.request
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
    ) {
        when (state.phase) {
            ClientPhase.Waiting, null -> {
                Pill(stringResource(if (request?.isEmergency == true) R.string.client_request_kind_emergency else R.string.client_request_kind_waiting))
                Countdown(state.secondsLeft)
                if (request != null) {
                    Text(
                        text = if (request.isEmergency) {
                            pluralStringResource(R.plurals.client_request_notified, request.dispatchesSent, request.dispatchesSent)
                        } else {
                            stringResource(R.string.client_request_sent_to, request.branchName ?: stringResource(R.string.client_request_the_provider))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ClientPhase.Accepted -> EndHeader(R.drawable.ic_verified, stringResource(R.string.client_request_accepted_title), statusLabel(request?.status))
            ClientPhase.Completed -> EndHeader(R.drawable.ic_verified, stringResource(R.string.client_request_completed_title), stringResource(R.string.client_request_completed_hint))
            ClientPhase.Rejected -> EndHeader(
                R.drawable.ic_close,
                stringResource(R.string.client_request_rejected_title),
                if (request?.isEmergency == true) {
                    stringResource(R.string.client_request_rejected_emergency)
                } else {
                    stringResource(R.string.client_request_rejected_hint, request?.branchName ?: stringResource(R.string.client_request_the_provider))
                },
            )
            ClientPhase.Expired -> EndHeader(R.drawable.ic_schedule, stringResource(R.string.client_request_expired_title), stringResource(R.string.client_request_expired_hint))
            ClientPhase.Canceled -> EndHeader(R.drawable.ic_close, stringResource(R.string.client_request_canceled_title), stringResource(R.string.client_request_canceled_hint))
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = Navy900,
        modifier = Modifier
            .clip(CircleShape)
            .background(White)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

/** Aro que se vacía en los 5 minutos que tiene el proveedor para aceptar, con una onda suave. */
@Composable
private fun Countdown(secondsLeft: Long?) {
    val progress by animateFloatAsState(
        targetValue = secondsLeft?.let { (it.toFloat() / ClientRequestViewModel.RESPONSE_WINDOW_SECONDS).coerceIn(0f, 1f) } ?: 1f,
        animationSpec = tween(900, easing = LinearEasing),
        label = "progreso",
    )
    val wave by rememberInfiniteTransition(label = "onda").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "onda",
    )
    val label = secondsLeft?.let { "%d:%02d".format(it / 60, it % 60) } ?: "—"
    val description = stringResource(R.string.client_request_time_left, label)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = 72.dp.toPx()
            drawCircle(White.copy(alpha = 0.25f * (1f - wave)), radius = radius + wave * 26.dp.toPx(), style = Stroke(2.dp.toPx()))
            val stroke = 8.dp.toPx()
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arc = Size(radius * 2, radius * 2)
            drawArc(White.copy(alpha = 0.25f), -90f, 360f, false, topLeft, arc, style = Stroke(stroke))
            drawArc(White, -90f, 360f * progress, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = White)
            Text(stringResource(R.string.client_request_to_accept), style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun EndHeader(@DrawableRes icon: Int, title: String, subtitle: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .background(White.copy(alpha = 0.18f), CircleShape),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = White, modifier = Modifier.size(32.dp))
    }
    Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = White, textAlign = TextAlign.Center)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
}

@Composable
private fun statusLabel(status: RequestStatus?): String = stringResource(
    when (status) {
        RequestStatus.OnTheWay -> R.string.client_request_step_on_the_way_now
        RequestStatus.Arrived -> R.string.client_request_step_arrived_now
        RequestStatus.Scheduled -> R.string.client_request_step_scheduled_now
        else -> R.string.client_request_step_accepted_now
    },
)

@Composable
private fun TechnicianCard(request: ServiceRequest) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Mist200, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Navy900),
        ) {
            val name = request.technicianName.orEmpty()
            Text(
                name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = White,
            )
            request.technicianPhotoUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.client_request_your_technician), style = MaterialTheme.typography.labelSmall, color = Slate500)
            Text(
                request.technicianName ?: stringResource(R.string.client_request_technician_pending),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Navy900,
            )
            request.branchName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Slate500) }
        }
    }
}

/** Aceptada → En camino → Llegó → Terminado, con el paso actual marcado. */
@Composable
private fun StatusSteps(status: RequestStatus) {
    val steps = listOf(
        RequestStatus.Accepted to R.string.client_request_step_accepted,
        RequestStatus.OnTheWay to R.string.client_request_step_on_the_way,
        RequestStatus.Arrived to R.string.client_request_step_arrived,
        RequestStatus.Completed to R.string.client_request_step_completed,
    )
    val current = when (status) {
        RequestStatus.Scheduled -> 0
        else -> steps.indexOfFirst { it.first == status }.coerceAtLeast(0)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { index, (_, label) ->
            val done = index <= current
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(if (done) SuccessGreen else Mist200, CircleShape),
                )
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal),
                    color = if (done) Navy900 else Slate500,
                )
            }
        }
    }
}

@Composable
private fun RequestSummary(request: ServiceRequest) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Mist50)
            .padding(16.dp),
    ) {
        Icon(painterResource(serviceIcon("", request.categoryName)), contentDescription = null, tint = Navy800, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                request.categoryName.ifBlank { stringResource(R.string.request_service) } + " · #" + request.id,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Navy900,
            )
            if (request.description.isNotBlank()) Text(request.description, style = MaterialTheme.typography.bodyMedium, color = Slate500)
            if (request.zone.isNotBlank()) Text(request.zone, style = MaterialTheme.typography.bodySmall, color = Slate500)
        }
        val price = request.agreedPrice ?: request.basePrice
        price?.let { Text("$" + it.removeSuffix(".00"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Navy900) }
    }
}

@Composable
private fun Actions(
    state: ClientRequestUiState,
    onCancel: () -> Unit,
    onResend: () -> Unit,
    onChooseAnother: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 8.dp),
    ) {
        when (state.phase) {
            ClientPhase.Waiting -> SecondaryButton(
                stringResource(R.string.client_request_cancel),
                onClick = onCancel,
                loading = state.isCanceling,
                color = EmergencyRed,
            )
            ClientPhase.Rejected, ClientPhase.Expired -> {
                if (state.canResend) {
                    FixoraPrimaryButton(text = stringResource(R.string.client_request_resend), onClick = onResend, loading = state.isResending)
                }
                SecondaryButton(stringResource(R.string.client_request_choose_another), onClick = onChooseAnother)
            }
            ClientPhase.Accepted -> {
                val phone = state.request?.branchPhone
                if (phone != null) {
                    FixoraPrimaryButton(
                        text = stringResource(R.string.client_request_call),
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
                            } catch (_: ActivityNotFoundException) {
                                // Sin app de teléfono (por ejemplo una tableta) no hay a dónde llamar.
                            }
                        },
                    )
                }
                SecondaryButton(stringResource(R.string.client_request_home), onClick = onHome)
            }
            ClientPhase.Canceled, ClientPhase.Completed, null -> SecondaryButton(stringResource(R.string.client_request_home), onClick = onHome)
        }
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, loading: Boolean = false, color: Color = Navy900) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Mist200, RoundedCornerShape(18.dp))
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick),
    ) {
        if (loading) {
            CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun WaitingPreview() {
    FixoraTheme {
        ClientRequestContent(
            state = ClientRequestUiState(
                isLoading = false,
                secondsLeft = 272,
                request = ServiceRequest(
                    id = "52", status = RequestStatus.Pending, isEmergency = false, description = "Fuga de agua. Gotea el lavabo",
                    categoryName = "Plomería", clientName = "", clientRating = 0.0, zone = "Torreón Jardín, Torreón",
                    basePrice = "380.00", photoUrls = emptyList(), createdAt = null, expiresAt = null, scheduledAt = null,
                    technicianName = null, branchName = "Plomería Laguna",
                ),
            ),
            onRetry = {}, onCancel = {}, onResend = {}, onChooseAnother = {}, onHome = {},
        )
    }
}
