package com.fixora.app.feature.servicerequest.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.profile.domain.Vehicle
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest

/**
 * Tarjeta del técnico (ANDROID-27): foto, nombre, insignia de datos verificados y el vehículo con
 * sus placas, para que el cliente lo reconozca al llegar. Son los datos que quedaron fijos al
 * aceptar (API-55), aunque el técnico edite su perfil después.
 */
@Composable
internal fun TechnicianCard(request: ServiceRequest) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Mist200, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Navy900),
            ) {
                Text(initials(request.technicianName.orEmpty()), style = MaterialTheme.typography.titleMedium, color = White)
                request.technicianPhotoUrl?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.client_request_your_technician), style = MaterialTheme.typography.labelSmall, color = Slate500)
                Text(
                    request.technicianName ?: stringResource(R.string.client_request_technician_pending),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                )
                request.branchName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Slate500) }
                if (request.technicianVerified) VerifiedBadge()
            }
        }
        request.technicianVehicle?.let { vehicle ->
            HorizontalDivider(color = Mist200)
            VehicleRow(vehicle)
        }
    }
}

@Composable
private fun VerifiedBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Icon(painterResource(R.drawable.ic_verified), contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
        Text(
            stringResource(R.string.technician_card_verified),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = SuccessGreen,
        )
    }
}

/** El vehículo y, a la derecha y en grande, las placas: es lo que se busca en la calle. */
@Composable
private fun VehicleRow(vehicle: Vehicle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_vehicle), contentDescription = null, tint = Navy800, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.technician_card_vehicle), style = MaterialTheme.typography.labelSmall, color = Slate500)
            Text(
                vehicle.description.ifBlank { stringResource(R.string.technician_card_vehicle_unknown) },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Navy900,
            )
        }
        val plateDescription = stringResource(R.string.technician_card_plate, vehicle.plate)
        Text(
            vehicle.plate,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
            color = Navy900,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Mist50)
                .border(1.5.dp, Navy900, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .semantics { contentDescription = plateDescription },
        )
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

@Preview(widthDp = 390)
@Composable
private fun TechnicianCardPreview() {
    FixoraTheme {
        Box(Modifier.background(White).padding(16.dp)) {
            TechnicianCard(
                ServiceRequest(
                    id = "31", status = RequestStatus.OnTheWay, isEmergency = false, description = "", categoryName = "Plomería",
                    clientName = "", clientRating = 0.0, zone = "", position = Coordinates(25.54, -103.41), basePrice = null,
                    photoUrls = emptyList(), createdAt = null, expiresAt = null, scheduledAt = null,
                    technicianName = "Juan Pérez", branchName = "Plomería Laguna",
                    technicianVehicle = Vehicle("FRX-123-A", "Nissan", "NP300", "Blanco"), technicianVerified = true,
                ),
            )
        }
    }
}
