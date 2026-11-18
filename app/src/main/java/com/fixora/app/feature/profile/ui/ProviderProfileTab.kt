package com.fixora.app.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraCardDivider
import com.fixora.app.core.designsystem.component.FixoraListRow
import com.fixora.app.core.designsystem.component.initialsOf
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.home.ui.components.roleLabel
import com.fixora.app.feature.profile.data.ProfileState
import com.fixora.app.feature.profile.domain.TechnicianProfile
import com.fixora.app.feature.profile.domain.Vehicle
import com.fixora.app.feature.profile.ui.components.ProfilePhoto

/**
 * Pestaña «Perfil» del proveedor, gerente y técnico (ANDROID-7): lo que verá el cliente (foto,
 * biografía y vehículo), los datos de la cuenta y cerrar sesión.
 */
@Composable
fun ProviderProfileTab(
    onOpenPhoto: () -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenPersonalData: () -> Unit,
    onOpenChangePassword: () -> Unit,
    viewModel: ProviderProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ProviderProfileContent(
        state = state,
        onOpenPhoto = onOpenPhoto,
        onOpenVehicle = onOpenVehicle,
        onOpenPersonalData = onOpenPersonalData,
        onOpenChangePassword = onOpenChangePassword,
        onRetry = viewModel::refresh,
        onLogout = viewModel::logout,
    )
}

@Composable
private fun ProviderProfileContent(
    state: ProviderProfileUiState,
    onOpenPhoto: () -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenPersonalData: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    val technician = (state.profile as? ProfileState.Loaded)?.profile
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineLarge, color = Navy900)
        HeaderCard(state, technician)

        when (val profile = state.profile) {
            is ProfileState.Loaded -> TechnicianCard(profile.profile, onOpenPhoto, onOpenVehicle)
            is ProfileState.Failed -> LoadErrorNotice(profile.error, onRetry = onRetry)
            ProfileState.NotTechnician -> Text(
                text = stringResource(R.string.profile_not_technician),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
            ProfileState.Loading -> Unit
        }

        FixoraCard {
            FixoraListRow(
                icon = R.drawable.ic_nav_account,
                title = stringResource(R.string.personal_data_title),
                subtitle = stringResource(R.string.account_personal_subtitle),
                onClick = onOpenPersonalData,
            )
            FixoraCardDivider()
            FixoraListRow(
                icon = R.drawable.ic_lock,
                title = stringResource(R.string.change_password_title),
                onClick = onOpenChangePassword,
            )
        }
        FixoraCard {
            FixoraListRow(
                icon = R.drawable.ic_logout,
                title = stringResource(R.string.home_logout),
                onClick = onLogout,
                tint = ErrorRed,
                showChevron = false,
            )
        }
    }
}

@Composable
private fun HeaderCard(state: ProviderProfileUiState, technician: TechnicianProfile?) {
    FixoraCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            ProfilePhoto(model = technician?.photoUrl, initials = initialsOf(state.fullName, state.email))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.fullName ?: state.email,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                )
                if (state.role.isNotBlank()) {
                    Text(roleLabel(state.role), style = MaterialTheme.typography.labelSmall, color = Slate500)
                }
            }
        }
    }
}

@Composable
private fun TechnicianCard(profile: TechnicianProfile, onOpenPhoto: () -> Unit, onOpenVehicle: () -> Unit) {
    FixoraCard {
        FixoraListRow(
            icon = R.drawable.ic_photo_camera,
            title = stringResource(R.string.profile_photo_title),
            subtitle = profile.bio.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.profile_photo_subtitle_empty),
            onClick = onOpenPhoto,
        )
        FixoraCardDivider()
        FixoraListRow(
            icon = R.drawable.ic_vehicle,
            title = stringResource(R.string.vehicle_title),
            subtitle = profile.vehicle?.let { "${it.plate} · ${it.description}" }
                ?: stringResource(R.string.vehicle_subtitle_empty),
            onClick = onOpenVehicle,
        )
    }
    Text(
        text = stringResource(R.string.profile_visible_hint),
        style = MaterialTheme.typography.labelSmall,
        color = Slate500,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF4F6F9)
@Composable
private fun ProviderProfilePreview() {
    FixoraTheme {
        ProviderProfileContent(
            state = ProviderProfileUiState(
                email = "juan@plomeria.mx",
                role = "proveedor",
                fullName = "Juan Pérez",
                profile = ProfileState.Loaded(
                    TechnicianProfile(
                        employeeId = "1",
                        photoUrl = null,
                        bio = "Plomero con 10 años de experiencia.",
                        vehicle = Vehicle("ABC-123-D", "Nissan", "NP300", "Blanco"),
                    ),
                ),
            ),
            onOpenPhoto = {}, onOpenVehicle = {}, onOpenPersonalData = {}, onOpenChangePassword = {},
            onRetry = {}, onLogout = {},
        )
    }
}
