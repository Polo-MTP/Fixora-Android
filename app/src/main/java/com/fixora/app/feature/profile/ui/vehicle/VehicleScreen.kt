package com.fixora.app.feature.profile.ui.vehicle

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.ActiveServiceNotice
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.auth.ui.components.message

/** Vehículo con el que el técnico llega al servicio (ANDROID-7). Al guardar o quitar regresa al perfil. */
@Composable
fun VehicleScreen(
    onBack: () -> Unit,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.isDone) { if (state.isDone) onBack() }

    VehicleContent(
        state = state,
        onBack = onBack,
        onChange = viewModel::onChange,
        onBlur = viewModel::onBlur,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onRetryLoad = viewModel::retryLoad,
    )
}

@Composable
private fun VehicleContent(
    state: VehicleUiState,
    onBack: () -> Unit,
    onChange: (VehicleField, String) -> Unit,
    onBlur: (VehicleField) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun Field(field: VehicleField, @StringRes label: Int, @StringRes placeholder: Int, modifier: Modifier = Modifier) {
        AuthTextField(
            value = state[field].value,
            onValueChange = { onChange(field, it) },
            onFocusLost = { onBlur(field) },
            label = stringResource(label),
            placeholder = stringResource(placeholder),
            error = state.errorOf(field).message(),
            // Con un servicio en curso los datos quedan de solo lectura.
            onClick = if (state.locked) ({}) else null,
            modifier = modifier,
        )
    }

    AccountScaffold(
        title = stringResource(R.string.vehicle_title),
        onBack = onBack,
        bottomBar = {
            FixoraPrimaryButton(
                text = stringResource(R.string.account_save),
                onClick = onSave,
                enabled = state.isLoaded && !state.isDeleting && !state.locked,
                loading = state.isSaving,
            )
        },
    ) {
        if (state.notTechnician) {
            Text(stringResource(R.string.profile_not_technician), style = MaterialTheme.typography.bodySmall, color = Slate500)
            return@AccountScaffold
        }
        state.loadError?.takeIf { !state.isLoaded }?.let { LoadErrorNotice(it, onRetry = onRetryLoad) }

        if (state.locked) {
            ActiveServiceNotice(stringResource(R.string.active_service_vehicle_locked))
        } else {
            Text(stringResource(R.string.vehicle_hint), style = MaterialTheme.typography.bodySmall, color = Slate500)
        }
        Field(VehicleField.Plate, R.string.vehicle_plate, R.string.vehicle_plate_placeholder)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(VehicleField.Brand, R.string.vehicle_brand, R.string.vehicle_brand_placeholder, Modifier.weight(1f))
            Field(VehicleField.Model, R.string.vehicle_model, R.string.vehicle_model_placeholder, Modifier.weight(1f))
        }
        Field(VehicleField.Color, R.string.vehicle_color, R.string.vehicle_color_placeholder)
        state.error?.let { FormErrorText(it.message()) }
        if (state.hasVehicle && !state.locked) {
            FixoraTextLink(
                text = stringResource(R.string.vehicle_delete),
                onClick = { if (!state.isBusy) confirmDelete = true },
                color = ErrorRed,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.vehicle_delete_title)) },
            text = { Text(stringResource(R.string.vehicle_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.vehicle_delete_confirm), color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.date_picker_cancel)) }
            },
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun VehiclePreview() {
    FixoraTheme {
        VehicleContent(
            state = VehicleUiState(isLoaded = true, hasVehicle = true),
            onBack = {}, onChange = { _, _ -> }, onBlur = {}, onSave = {}, onDelete = {}, onRetryLoad = {},
        )
    }
}
