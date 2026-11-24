package com.fixora.app.feature.availability.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.message
import com.fixora.app.core.notifications.AlertsSetupCard
import com.fixora.app.feature.availability.data.AvailabilityState
import com.fixora.app.feature.availability.location.FusedLocationSource
import com.fixora.app.feature.home.ui.components.ComingSoonCard
import com.fixora.app.feature.home.ui.components.TabTitle
import com.fixora.app.feature.notifications.ui.NotificationBell
import com.fixora.app.feature.payment.ui.debts.DebtsHomeCard

/**
 * Pestaña Inicio del proveedor: el interruptor En línea / Fuera de línea (ANDROID-9), el aviso si
 * faltan permisos para la alarma de solicitudes (ANDROID-13) y, debajo, lo que vendrá
 * (solicitudes, ingresos y calificación del día).
 */
@Composable
fun ProviderHomeTab(
    onOpenNotifications: () -> Unit,
    onOpenDebts: () -> Unit,
    viewModel: AvailabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> viewModel.onPermissionResult(results.values.any { it }) }

    LaunchedEffect(Unit) {
        viewModel.requestPermission.collect { permissionLauncher.launch(FusedLocationSource.LOCATION_PERMISSIONS) }
    }

    ProviderHomeContent(
        state = state,
        onToggle = viewModel::onToggle,
        onRetry = viewModel::refresh,
        bell = { NotificationBell(onClick = onOpenNotifications) },
        debts = { DebtsHomeCard(asProvider = true, onOpen = onOpenDebts) },
    )
}

@Composable
private fun ProviderHomeContent(
    state: AvailabilityUiState,
    onToggle: (Boolean) -> Unit,
    onRetry: () -> Unit,
    bell: @Composable () -> Unit = {},
    debts: @Composable () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabTitle(stringResource(R.string.home_greeting), Modifier.weight(1f))
            bell()
        }
        if (state.isVisible) AvailabilityCard(state, onToggle, onRetry)
        AlertsSetupCard()
        // Lo que le deben, como «$580 por cobrar · 2 adeudos» del diseño (ANDROID-25).
        debts()
        ComingSoonCard(message = stringResource(R.string.provider_home_soon), icon = R.drawable.ic_nav_home)
    }
}

@Composable
private fun AvailabilityCard(
    state: AvailabilityUiState,
    onToggle: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val availability = state.availability
    val online = state.isOnline
    FixoraCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(if (online) SuccessGreen else Mist200, CircleShape),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (online) R.string.availability_online else R.string.availability_offline),
                        style = MaterialTheme.typography.titleMedium,
                        color = Navy900,
                    )
                    Text(
                        text = stringResource(
                            if (online) R.string.availability_online_hint else R.string.availability_offline_hint,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                    )
                }
                Switch(
                    checked = online,
                    onCheckedChange = onToggle,
                    enabled = availability is AvailabilityState.Loaded && !state.isChanging,
                    colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = SuccessGreen),
                )
            }

            if (availability is AvailabilityState.Loaded && !availability.branchOpen && !online) {
                Text(
                    text = stringResource(R.string.availability_branch_closed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                )
            }
            if (availability is AvailabilityState.Failed) {
                Text(
                    text = stringResource(R.string.availability_load_error, availability.error.message()),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
                FixoraTextLink(stringResource(R.string.account_retry), onClick = onRetry)
            }
            state.problem?.let { problem ->
                Text(
                    text = problem.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun AvailabilityProblem.message(): String = when (this) {
    is AvailabilityProblem.Api -> error.message()
    AvailabilityProblem.LocationDenied -> stringResource(R.string.availability_location_denied)
    AvailabilityProblem.LocationUnavailable -> stringResource(R.string.availability_location_unavailable)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProviderHomeOnlinePreview() {
    FixoraTheme {
        ProviderHomeContent(
            state = AvailabilityUiState(AvailabilityState.Loaded(online = true, branchOpen = true)),
            onToggle = {},
            onRetry = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProviderHomeOfflinePreview() {
    FixoraTheme {
        ProviderHomeContent(
            state = AvailabilityUiState(
                availability = AvailabilityState.Loaded(online = false, branchOpen = false),
                problem = AvailabilityProblem.LocationDenied,
            ),
            onToggle = {},
            onRetry = {},
        )
    }
}
