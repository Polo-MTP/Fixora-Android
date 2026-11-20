package com.fixora.app.feature.servicerequest.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.ui.PhotoViewer
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.map.ui.serviceIcon
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Solicitud abierta desde un aviso (ANDROID-13): qué se pide, dónde y en qué estado va. Aceptar o
 * rechazar con la cuenta regresiva se agrega en «Detalle de solicitud y aceptar o rechazar».
 */
@Composable
fun ServiceRequestScreen(
    onBack: () -> Unit,
    viewModel: ServiceRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val request = state.request
    AccountScaffold(
        title = request?.let { stringResource(R.string.request_title, it.id) } ?: stringResource(R.string.request_title_loading),
        onBack = onBack,
    ) {
        val error = state.error
        when {
            request != null -> RequestContent(request)
            error != null -> LoadErrorNotice(error, onRetry = viewModel::retry)
            else -> CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun RequestContent(request: ServiceRequest) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(request.status)
        if (request.isEmergency) Pill(stringResource(R.string.request_urgent), ErrorRed)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .background(Mist50, CircleShape)
                .border(1.dp, Mist200, CircleShape),
        ) {
            Icon(painterResource(serviceIcon("", request.categoryName)), contentDescription = null, tint = Navy800, modifier = Modifier.size(24.dp))
        }
        Text(
            request.categoryName.ifBlank { stringResource(R.string.request_service) },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Navy900,
        )
    }
    if (request.description.isNotEmpty()) {
        Text(request.description, style = MaterialTheme.typography.bodyLarge, color = Navy900)
    }
    if (request.photoUrls.isNotEmpty()) Photos(request.photoUrls)

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Mist200, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        if (request.clientName.isNotEmpty()) {
            val rating = request.clientRating.takeIf { it > 0 }?.let { String.format(Locale.US, " · %.1f ★", it) }.orEmpty()
            Info(R.drawable.ic_nav_account, R.string.request_client, request.clientName + rating)
        }
        if (request.zone.isNotEmpty()) Info(R.drawable.ic_place, R.string.request_zone, request.zone)
        request.basePrice?.let { Info(R.drawable.ic_tune, R.string.request_base_price, "$" + it.removeSuffix(".00")) }
        request.scheduledAt?.let { Info(R.drawable.ic_schedule, R.string.request_scheduled_for, formatDateTime(it)) }
        request.createdAt?.let { Info(R.drawable.ic_schedule, R.string.request_received, formatDateTime(it)) }
        if (request.status == RequestStatus.Pending && request.scheduledAt == null) {
            request.expiresAt?.let { Info(R.drawable.ic_schedule, R.string.request_answer_before, formatTime(it)) }
        }
        request.technicianName?.let { Info(R.drawable.ic_vehicle, R.string.request_technician, it) }
    }
}

@Composable
private fun Photos(urls: List<String>) {
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    viewerIndex?.let { PhotoViewer(photos = urls, startIndex = it, onDismiss = { viewerIndex = null }) }
    val openLabel = stringResource(R.string.photo_viewer_open)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        urls.forEachIndexed { index, url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mist200)
                    .clickable(onClickLabel = openLabel) { viewerIndex = index },
            )
        }
    }
}

@Composable
private fun Info(@DrawableRes icon: Int, @StringRes label: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = Slate500, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = Slate500)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Navy900)
        }
    }
}

@Composable
private fun StatusPill(status: RequestStatus) {
    val (text, color) = when (status) {
        RequestStatus.Pending -> R.string.request_status_pending to Navy800
        RequestStatus.Accepted -> R.string.request_status_accepted to SuccessGreen
        RequestStatus.Scheduled -> R.string.request_status_scheduled to Navy800
        RequestStatus.OnTheWay -> R.string.request_status_on_the_way to SuccessGreen
        RequestStatus.Arrived -> R.string.request_status_arrived to SuccessGreen
        RequestStatus.Completed -> R.string.request_status_completed to Slate500
        RequestStatus.Canceled -> R.string.request_status_canceled to ErrorRed
        RequestStatus.Expired -> R.string.request_status_expired to Slate500
        RequestStatus.Unknown -> R.string.request_status_unknown to Slate500
    }
    Pill(stringResource(text), color)
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private val TimeFormat = DateTimeFormatter.ofPattern("H:mm", Locale.forLanguageTag("es-MX"))
private val DateTimeFormat = DateTimeFormatter.ofPattern("d MMM, H:mm", Locale.forLanguageTag("es-MX"))

private fun formatTime(instant: Instant): String = TimeFormat.format(instant.atZone(ZoneId.systemDefault()))

private fun formatDateTime(instant: Instant): String = DateTimeFormat.format(instant.atZone(ZoneId.systemDefault()))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ServiceRequestPreview() {
    FixoraTheme {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RequestContent(
                ServiceRequest(
                    id = "15", status = RequestStatus.Pending, isEmergency = true,
                    description = "Se rompió la tubería del baño y está saliendo mucha agua.",
                    categoryName = "Plomería", clientName = "Roberto Martínez", clientRating = 4.6,
                    zone = "Juárez, Cuauhtémoc", basePrice = "350.00", photoUrls = emptyList(),
                    createdAt = Instant.now(), expiresAt = Instant.now().plusSeconds(300), scheduledAt = null,
                    technicianName = null,
                ),
            )
        }
    }
}
