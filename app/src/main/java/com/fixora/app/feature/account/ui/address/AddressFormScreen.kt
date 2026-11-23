package com.fixora.app.feature.account.ui.address

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.auth.ui.components.message

/** Agregar o editar una dirección (ANDROID-5). Al guardar o eliminar regresa a la lista. */
@Composable
fun AddressFormScreen(
    onBack: () -> Unit,
    viewModel: AddressFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.isDone) { if (state.isDone) onBack() }

    AddressFormContent(
        state = state,
        onBack = onBack,
        onChange = viewModel::onChange,
        onBlur = viewModel::onBlur,
        onMainChange = viewModel::onMainChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@Composable
private fun AddressFormContent(
    state: AddressFormUiState,
    onBack: () -> Unit,
    onChange: (AddressField, String) -> Unit,
    onBlur: (AddressField) -> Unit,
    onMainChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun Field(
        field: AddressField,
        label: Int,
        placeholder: Int,
        modifier: Modifier = Modifier,
        keyboardType: KeyboardType = KeyboardType.Text,
    ) {
        AuthTextField(
            value = state[field].value,
            onValueChange = { onChange(field, it) },
            onFocusLost = { onBlur(field) },
            label = stringResource(label),
            placeholder = stringResource(placeholder),
            keyboardType = keyboardType,
            error = state.errorOf(field).message(),
            modifier = modifier,
        )
    }

    AccountScaffold(
        title = stringResource(if (state.isEditing) R.string.address_form_edit_title else R.string.address_form_new_title),
        onBack = onBack,
        bottomBar = {
            FixoraPrimaryButton(
                text = stringResource(R.string.account_save),
                onClick = onSave,
                enabled = state.isLoaded && !state.isDeleting,
                loading = state.isSaving,
            )
        },
    ) {
        Field(AddressField.Alias, R.string.address_alias, R.string.address_alias_placeholder)
        AliasSuggestions(onPick = { onChange(AddressField.Alias, it) })
        Field(AddressField.Street, R.string.address_street, R.string.address_street_placeholder)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(AddressField.NumExt, R.string.address_num_ext, R.string.address_num_ext_placeholder, Modifier.weight(1f))
            Field(AddressField.NumInt, R.string.address_num_int, R.string.address_num_int_placeholder, Modifier.weight(1f))
        }
        Field(AddressField.Colony, R.string.address_colony, R.string.address_colony_placeholder)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(
                AddressField.PostalCode,
                R.string.address_postal_code,
                R.string.address_postal_code_placeholder,
                Modifier.weight(1f),
                KeyboardType.Number,
            )
            Field(AddressField.Municipality, R.string.address_municipality, R.string.address_municipality_placeholder, Modifier.weight(1.4f))
        }
        Field(AddressField.State, R.string.address_state, R.string.address_state_placeholder)
        Field(AddressField.Reference, R.string.address_reference, R.string.address_reference_placeholder)
        MainAddressSwitch(checked = state.isMain, enabled = state.canChangeMain, onChange = onMainChange)
        state.error?.let { FormErrorText(it.message()) }
        if (state.isEditing) {
            FixoraTextLink(
                text = stringResource(R.string.address_delete),
                onClick = { if (!state.isBusy) confirmDelete = true },
                color = ErrorRed,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.address_delete_title)) },
            text = { Text(stringResource(R.string.address_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.address_delete_confirm), color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.date_picker_cancel)) }
            },
        )
    }
}

/** Atajos para los alias más comunes; también se puede escribir otro. */
@Composable
private fun AliasSuggestions(onPick: (String) -> Unit) {
    val suggestions = listOf(stringResource(R.string.address_alias_home), stringResource(R.string.address_alias_work))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { alias ->
            Text(
                text = alias,
                style = MaterialTheme.typography.labelLarge,
                color = Navy800,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(White)
                    .border(1.dp, Mist200, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onPick(alias) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun MainAddressSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FixoraCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.address_main_label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Navy900,
                )
                Text(
                    text = stringResource(if (enabled) R.string.address_main_hint else R.string.address_main_locked),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate500,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Orange500,
                    disabledCheckedTrackColor = Orange500.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun AddressFormPreview() {
    FixoraTheme {
        AddressFormContent(
            state = AddressFormUiState(isLoaded = true, isEditing = true, isMain = true, canChangeMain = false),
            onBack = {}, onChange = { _, _ -> }, onBlur = {}, onMainChange = {}, onSave = {}, onDelete = {},
        )
    }
}
