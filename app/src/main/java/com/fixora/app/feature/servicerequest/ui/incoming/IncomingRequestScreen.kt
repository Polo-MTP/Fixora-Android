package com.fixora.app.feature.servicerequest.ui.incoming

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.PhotoViewer
import com.fixora.app.core.ui.SystemBarsAppearance
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.map.ui.serviceIcon
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import java.util.Locale

private val EmergencyRed = Color(0xFFE53935)
private val EmergencyRedDark = Color(0xFFC62828)
private val AvatarBlue = Color(0xFFDCE8FB)

/**
 * Alarma de solicitud entrante a pantalla completa (ANDROID-13, Figma «Fixora Técnico - Alerta de
 * Emergencia»). Suena hasta que se responde; la de emergencia va en rojo y con ondas que salen del
 * aro, la normal en azul marino.
 */
@Composable
fun IncomingRequestScreen(
    onAccepted: (requestId: String) -> Unit,
    onClose: () -> Unit,
    viewModel: IncomingRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.outcome) {
        when (state.outcome) {
            IncomingOutcome.Accepted -> state.request?.let { onAccepted(it.id) }
            IncomingOutcome.Rejected -> onClose()
            null -> Unit
        }
    }
    AlarmSoundEffect(active = state.isRinging)
    IncomingRequestContent(
        state = state,
        onAccept = viewModel::accept,
        onReject = viewModel::reject,
        onRetry = viewModel::retry,
        onClose = onClose,
    )
}

@Composable
private fun IncomingRequestContent(
    state: IncomingRequestUiState,
    onAccept: () -> Unit,
    onReject: (String) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    SystemBarsAppearance(lightIcons = true)
    val request = state.request
    val emergency = request?.isEmergency == true
    val animated = state.isRinging

    Box(
        Modifier
            .fillMaxSize()
            .background(headerBrush(emergency, animated)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(state, emergency, animated)
            AnimatedVisibility(
                visible = request != null || state.loadError != null,
                enter = slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(300)),
                modifier = Modifier.weight(1f),
            ) {
                Sheet(state, onAccept, onReject, onRetry, onClose)
            }
        }
        if (request == null && state.loadError == null) {
            CircularProgressIndicator(color = White, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Rojo de emergencia que late mientras suena; azul marino para las normales. */
@Composable
private fun headerBrush(emergency: Boolean, animated: Boolean): Brush {
    if (!emergency) return Brush.verticalGradient(listOf(Navy900, Navy600))
    val transition = rememberInfiniteTransition(label = "fondo")
    val pulse by transition.animateColor(
        initialValue = EmergencyRed,
        targetValue = if (animated) EmergencyRedDark else EmergencyRed,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "rojo",
    )
    return Brush.verticalGradient(listOf(pulse, EmergencyRedDark))
}

@Composable
private fun Header(state: IncomingRequestUiState, emergency: Boolean, animated: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 28.dp),
    ) {
        KindPill(emergency, animated)
        CountdownRing(state.secondsLeft, emergency, animated)
        val request = state.request
        if (request != null) {
            val parts = listOfNotNull(
                state.distanceKm?.let { String.format(Locale.US, "%.1f km", it) },
                state.distanceKm?.let { stringResource(R.string.incoming_eta, IncomingRequestViewModel.etaMinutes(it)) },
                request.zone.substringBefore(',').takeIf { it.isNotBlank() },
            )
            if (parts.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_place), contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = White)
                }
            }
        }
    }
}

@Composable
private fun KindPill(emergency: Boolean, animated: Boolean) {
    val transition = rememberInfiniteTransition(label = "etiqueta")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (emergency && animated) 0.55f else 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "parpadeo",
    )
    val color = if (emergency) EmergencyRed else Navy900
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(White)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_siren), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            stringResource(if (emergency) R.string.incoming_kind_emergency else R.string.incoming_kind_request),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = color,
        )
    }
}

/** Aro con el tiempo para responder; en emergencia salen ondas mientras suena. */
@Composable
private fun CountdownRing(secondsLeft: Long?, emergency: Boolean, animated: Boolean) {
    val entrance = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 200f)) }
    val progress by animateFloatAsState(
        targetValue = secondsLeft?.let { (it.toFloat() / IncomingRequestViewModel.RESPONSE_WINDOW_SECONDS).coerceIn(0f, 1f) } ?: 1f,
        animationSpec = tween(900, easing = LinearEasing),
        label = "progreso",
    )
    val ripples = rememberInfiniteTransition(label = "ondas")
    val wave by ripples.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (emergency) 1600 else 2600, easing = LinearEasing)),
        label = "onda",
    )
    val label = secondsLeft?.let { "%d:%02d".format(it / 60, it % 60) } ?: "—"
    val description = stringResource(R.string.incoming_time_left_description, label)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(230.dp)
            .scale(entrance.value)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ringRadius = 80.dp.toPx()
            if (animated) {
                // Tres ondas desfasadas que crecen y se desvanecen desde el aro.
                val count = if (emergency) 3 else 1
                repeat(count) { index ->
                    val t = (wave + index.toFloat() / count) % 1f
                    drawCircle(
                        color = White.copy(alpha = 0.28f * (1f - t)),
                        radius = ringRadius + t * 35.dp.toPx(),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            val stroke = 9.dp.toPx()
            val topLeft = Offset(center.x - ringRadius, center.y - ringRadius)
            val arcSize = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2)
            drawArc(White.copy(alpha = 0.28f), -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(White, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = White)
            Text(stringResource(R.string.incoming_to_answer), style = MaterialTheme.typography.bodyMedium, color = White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun Sheet(
    state: IncomingRequestUiState,
    onAccept: () -> Unit,
    onReject: (String) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    var choosingReason by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(White)
            .navigationBarsPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp),
        ) {
            val request = state.request
            val loadError = state.loadError
            when {
                request != null -> RequestSummary(request)
                loadError != null -> LoadErrorNotice(loadError, onRetry = onRetry)
            }
            state.actionError?.let {
                Text(it.message(), style = MaterialTheme.typography.bodySmall, color = EmergencyRed)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 8.dp),
        ) {
            val request = state.request
            if (request == null || state.isClosed) {
                if (request != null) {
                    Text(
                        stringResource(closedMessage(request, state.secondsLeft)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SecondaryButton(stringResource(R.string.incoming_close), onClick = onClose)
            } else {
                FixoraPrimaryButton(
                    text = request.basePrice?.let { stringResource(R.string.incoming_accept_for, it.removeSuffix(".00")) }
                        ?: stringResource(R.string.incoming_accept),
                    onClick = onAccept,
                    loading = state.isAccepting,
                    enabled = !state.isRejecting,
                )
                SecondaryButton(
                    text = stringResource(R.string.incoming_reject),
                    onClick = { choosingReason = true },
                    enabled = !state.isBusy,
                )
            }
        }
    }
    if (choosingReason) {
        RejectReasonSheet(
            onDismiss = { choosingReason = false },
            onConfirm = {
                choosingReason = false
                onReject(it)
            },
        )
    }
}

private fun closedMessage(request: ServiceRequest, secondsLeft: Long?): Int = when {
    request.status == RequestStatus.Canceled -> R.string.incoming_closed_canceled
    request.status == RequestStatus.Pending && secondsLeft == 0L -> R.string.incoming_closed_expired
    request.status == RequestStatus.Expired -> R.string.incoming_closed_expired
    else -> R.string.incoming_closed_taken
}

@Composable
private fun RequestSummary(request: ServiceRequest) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .background(AvatarBlue, CircleShape),
        ) {
            Text(initials(request.clientName), style = MaterialTheme.typography.titleMedium, color = Navy800)
        }
        Column(Modifier.weight(1f)) {
            Text(
                shortName(request.clientName).ifBlank { stringResource(R.string.incoming_client) },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Navy900,
            )
            if (request.clientRating > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(R.drawable.ic_star), contentDescription = null, tint = Orange500, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.incoming_client_rating, String.format(Locale.US, "%.1f", request.clientRating)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                    )
                }
            } else {
                Text(stringResource(R.string.incoming_client_new), style = MaterialTheme.typography.bodySmall, color = Slate500)
            }
        }
    }

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
                request.categoryName.ifBlank { stringResource(R.string.request_service) },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Navy900,
            )
            Text(
                request.description.ifBlank {
                    stringResource(if (request.isEmergency) R.string.incoming_no_details_emergency else R.string.incoming_no_details)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500,
            )
        }
    }
    if (request.photoUrls.isNotEmpty()) Photos(request.photoUrls)
}

@Composable
private fun Photos(urls: List<String>) {
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    viewerIndex?.let { PhotoViewer(photos = urls, startIndex = it, onDismiss = { viewerIndex = null }) }
    val openLabel = stringResource(R.string.photo_viewer_open)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        urls.forEachIndexed { index, url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mist200)
                    .clickable(onClickLabel = openLabel) { viewerIndex = index },
            )
        }
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Mist200, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) Navy900 else Slate500)
    }
}

/** La API pide un motivo para rechazar; se elige de una lista corta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RejectReasonSheet(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val reasons = listOf(
        stringResource(R.string.incoming_reason_busy),
        stringResource(R.string.incoming_reason_far),
        stringResource(R.string.incoming_reason_service),
        stringResource(R.string.incoming_reason_other),
    )
    var selected by rememberSaveable { mutableStateOf(reasons.first()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = White,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.incoming_reason_title), style = MaterialTheme.typography.titleLarge, color = Navy900)
            reasons.forEach { reason ->
                val isSelected = reason == selected
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Navy900,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Navy900 else Mist200, RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { selected = reason }
                        .padding(16.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            FixoraPrimaryButton(text = stringResource(R.string.incoming_reject_confirm), onClick = { onConfirm(selected) })
        }
    }
}

private fun initials(name: String): String {
    val words = name.split(' ').filter { it.isNotBlank() }
    return words.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
}

/** «Misael Torres» → «Misael T.», como en el diseño. */
private fun shortName(name: String): String {
    val words = name.split(' ').filter { it.isNotBlank() }
    if (words.size < 2) return words.firstOrNull().orEmpty()
    return "${words[0]} ${words[1].first()}."
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun IncomingEmergencyPreview() {
    FixoraTheme {
        IncomingRequestContent(
            state = IncomingRequestUiState(
                isLoading = false,
                request = ServiceRequest(
                    id = "23", status = RequestStatus.Pending, isEmergency = true, description = "",
                    categoryName = "Plomería", clientName = "Misael Torres", clientRating = 4.9,
                    zone = "Col. Centro, Cuauhtémoc", basePrice = "350.00", photoUrls = emptyList(),
                    createdAt = null, expiresAt = null, scheduledAt = null, technicianName = null,
                ),
                distanceKm = 1.2,
                secondsLeft = 246,
            ),
            onAccept = {}, onReject = {}, onRetry = {}, onClose = {},
        )
    }
}
