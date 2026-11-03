package com.fixora.app.feature.account.ui.personal

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.account.ui.components.SampleDataNotice
import com.fixora.app.feature.auth.ui.FormField
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.auth.ui.components.BirthDateFormat
import com.fixora.app.feature.auth.ui.components.BirthDatePickerDialog
import com.fixora.app.feature.auth.ui.components.message
import java.time.LocalDate

/** Datos personales del cliente (ANDROID-5). Al guardar regresa a Mi cuenta. */
@Composable
fun PersonalDataScreen(
    onBack: () -> Unit,
    viewModel: PersonalDataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.personal_data_saved)
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    PersonalDataContent(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onNameBlur = viewModel::onNameBlur,
        onLastNamePChange = viewModel::onLastNamePChange,
        onLastNamePBlur = viewModel::onLastNamePBlur,
        onLastNameMChange = viewModel::onLastNameMChange,
        onBirthDateSelected = viewModel::onBirthDateSelected,
        onPhoneChange = viewModel::onPhoneChange,
        onPhoneBlur = viewModel::onPhoneBlur,
        onSave = viewModel::save,
    )
}

@Composable
private fun PersonalDataContent(
    state: PersonalDataUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onNameBlur: () -> Unit,
    onLastNamePChange: (String) -> Unit,
    onLastNamePBlur: () -> Unit,
    onLastNameMChange: (String) -> Unit,
    onBirthDateSelected: (LocalDate) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPhoneBlur: () -> Unit,
    onSave: () -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    AccountScaffold(
        title = stringResource(R.string.personal_data_title),
        onBack = onBack,
        bottomBar = {
            FixoraPrimaryButton(
                text = stringResource(R.string.account_save),
                onClick = onSave,
                enabled = state.isLoaded,
                loading = state.isSaving,
            )
        },
    ) {
        SampleDataNotice()
        AuthTextField(
            value = state.name.value,
            onValueChange = onNameChange,
            onFocusLost = onNameBlur,
            label = stringResource(R.string.personal_name),
            placeholder = stringResource(R.string.personal_name_placeholder),
            error = state.nameError.message(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AuthTextField(
                value = state.lastNameP.value,
                onValueChange = onLastNamePChange,
                onFocusLost = onLastNamePBlur,
                label = stringResource(R.string.personal_lastname_p),
                placeholder = stringResource(R.string.personal_lastname_p_placeholder),
                error = state.lastNamePError.message(),
                modifier = Modifier.weight(1f),
            )
            AuthTextField(
                value = state.lastNameM.value,
                onValueChange = onLastNameMChange,
                label = stringResource(R.string.personal_lastname_m),
                placeholder = stringResource(R.string.personal_lastname_m_placeholder),
                modifier = Modifier.weight(1f),
            )
        }
        AuthTextField(
            value = state.birthDate?.format(BirthDateFormat).orEmpty(),
            onValueChange = {},
            label = stringResource(R.string.personal_birth_date),
            placeholder = stringResource(R.string.personal_birth_date_placeholder),
            error = state.birthDateError.message(),
            onClick = { showDatePicker = true },
        )
        AuthTextField(
            value = state.phone.value,
            onValueChange = onPhoneChange,
            onFocusLost = onPhoneBlur,
            label = stringResource(R.string.personal_phone),
            placeholder = stringResource(R.string.personal_phone_placeholder),
            prefix = stringResource(R.string.personal_phone_prefix),
            keyboardType = KeyboardType.Phone,
            error = state.phoneError.message(),
        )
        // El correo identifica la cuenta para iniciar sesión: se muestra, pero no se edita aquí.
        AuthTextField(
            value = state.email,
            onValueChange = {},
            label = stringResource(R.string.auth_field_email),
            placeholder = "",
            onClick = {},
        )
        Text(
            text = stringResource(R.string.personal_data_email_hint),
            style = MaterialTheme.typography.labelSmall,
            color = Slate500,
        )
        state.error?.let { FormErrorText(it.message()) }
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
            initial = state.birthDate ?: state.today.minusYears(18),
            today = state.today,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                onBirthDateSelected(it)
                showDatePicker = false
            },
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PersonalDataPreview() {
    FixoraTheme {
        PersonalDataContent(
            state = PersonalDataUiState(
                isLoaded = true,
                email = "ana@correo.com",
                name = FormField("Ana María"),
                lastNameP = FormField("Pérez"),
                lastNameM = FormField("López"),
                birthDate = LocalDate.of(1995, 4, 12),
                phone = FormField("5512345678"),
            ),
            onBack = {}, onNameChange = {}, onNameBlur = {}, onLastNamePChange = {}, onLastNamePBlur = {},
            onLastNameMChange = {}, onBirthDateSelected = {}, onPhoneChange = {}, onPhoneBlur = {}, onSave = {},
        )
    }
}
