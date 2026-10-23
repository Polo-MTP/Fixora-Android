package com.fixora.app.feature.auth.ui.register

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.slideBackwardEnter
import com.fixora.app.core.ui.slideBackwardExit
import com.fixora.app.core.ui.slideForwardEnter
import com.fixora.app.core.ui.slideForwardExit
import com.fixora.app.feature.auth.domain.PasswordRules
import com.fixora.app.feature.auth.ui.components.AuthFooter
import com.fixora.app.feature.auth.ui.components.AuthScaffold
import com.fixora.app.feature.auth.ui.components.AuthStep
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.auth.ui.components.VerificationCodeField
import com.fixora.app.feature.auth.ui.components.message
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val RESEND_SECONDS = 59
private val DateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Registro del cliente en tres pasos (módulos 1 y 2): cuenta, verificación del correo y datos
 * personales. Los pasos viven en una sola pantalla; «atrás» regresa al paso anterior.
 */
@Composable
fun RegisterScreen(
    onExit: () -> Unit,
    onLogin: () -> Unit,
    onRegistered: () -> Unit,
    viewModel: RegisterViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val back = { if (!viewModel.back()) onExit() }
    BackHandler(enabled = state.step != RegisterStep.Account) { viewModel.back() }

    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            // Igual que la navegación: avanzar desliza hacia la izquierda y regresar hacia la derecha.
            // El paso que sale queda encima al regresar, para que se vea cómo se retira.
            if (targetState.ordinal > initialState.ordinal) {
                (slideForwardEnter() togetherWith slideForwardExit()).apply { targetContentZIndex = 1f }
            } else {
                (slideBackwardEnter() togetherWith slideBackwardExit()).apply { targetContentZIndex = -1f }
            }
        },
        label = "registerStep",
    ) { step ->
        when (step) {
            RegisterStep.Account -> AccountStep(state, viewModel, onBack = back, onLogin = onLogin)
            RegisterStep.Verify -> VerifyStep(state, viewModel, onBack = back)
            RegisterStep.Personal -> PersonalStep(
                state = state,
                viewModel = viewModel,
                onBack = back,
                onSubmit = { if (viewModel.submitPersonalData()) onRegistered() },
            )
        }
    }
}

@Composable
private fun AccountStep(
    state: RegisterUiState,
    viewModel: RegisterViewModel,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    AuthScaffold(
        title = stringResource(R.string.register_account_title),
        subtitle = AnnotatedString(stringResource(R.string.register_account_subtitle)),
        onBack = onBack,
        step = AuthStep(RegisterStep.Account.number, RegisterStep.total),
        footer = {
            AuthFooter(
                question = stringResource(R.string.register_has_account),
                action = stringResource(R.string.auth_login_action),
                onAction = onLogin,
            )
        },
    ) {
        AuthTextField(
            value = state.email.value,
            onValueChange = viewModel::onEmailChange,
            onFocusLost = viewModel::onEmailBlur,
            label = stringResource(R.string.auth_field_email),
            placeholder = stringResource(R.string.auth_field_email_placeholder),
            keyboardType = KeyboardType.Email,
            error = state.emailError.message(),
        )
        AuthTextField(
            value = state.password.value,
            onValueChange = viewModel::onPasswordChange,
            onFocusLost = viewModel::onPasswordBlur,
            label = stringResource(R.string.auth_field_password),
            placeholder = stringResource(R.string.register_password_placeholder),
            isPassword = true,
            error = state.passwordError.message(),
        )
        PasswordRulesView(state.passwordRules)
        AuthTextField(
            value = state.confirmPassword.value,
            onValueChange = viewModel::onConfirmPasswordChange,
            onFocusLost = viewModel::onConfirmPasswordBlur,
            label = stringResource(R.string.register_confirm_password),
            placeholder = stringResource(R.string.register_confirm_placeholder),
            isPassword = true,
            error = state.confirmPasswordError.message(),
        )
        FixoraPrimaryButton(
            text = stringResource(R.string.register_continue),
            onClick = viewModel::continueFromAccount,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Requisitos de la contraseña: se marcan en verde mientras se cumplen. */
@Composable
private fun PasswordRulesView(rules: PasswordRules) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Rule(stringResource(R.string.register_rule_length), rules.minLength, Modifier.weight(1f))
            Rule(stringResource(R.string.register_rule_uppercase), rules.uppercase, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Rule(stringResource(R.string.register_rule_digit), rules.digit, Modifier.weight(1f))
            Rule(stringResource(R.string.register_rule_lowercase), rules.lowercase, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Rule(text: String, met: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = (if (met) "● " else "○ ") + text,
        style = MaterialTheme.typography.labelSmall,
        color = if (met) SuccessGreen else Slate500,
        modifier = modifier,
    )
}

@Composable
private fun VerifyStep(
    state: RegisterUiState,
    viewModel: RegisterViewModel,
    onBack: () -> Unit,
) {
    val email = state.email.value.trim()
    val subtitleText = stringResource(R.string.verify_subtitle, email)
    val subtitle = buildAnnotatedString {
        val start = subtitleText.indexOf(email)
        append(subtitleText)
        if (start >= 0) addStyle(SpanStyle(color = White, fontWeight = FontWeight.Bold), start, start + email.length)
    }
    val label = stringResource(R.string.verify_code_label)

    AuthScaffold(
        title = stringResource(R.string.verify_title),
        subtitle = subtitle,
        onBack = onBack,
        step = AuthStep(RegisterStep.Verify.number, RegisterStep.total),
        footer = {
            AuthFooter(
                question = stringResource(R.string.verify_wrong_email),
                action = stringResource(R.string.verify_change_email),
                onAction = viewModel::changeEmail,
            )
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Navy900,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VerificationCodeField(code = state.code, onCodeChange = viewModel::onCodeChange, label = label)
        ResendCountdown()
        FixoraPrimaryButton(
            text = stringResource(R.string.verify_submit),
            onClick = viewModel::verifyCode,
            enabled = state.isCodeComplete,
        )
    }
}

/** Cuenta regresiva para poder pedir otro código; al llegar a cero muestra «Reenviar código». */
@Composable
private fun ResendCountdown() {
    var round by rememberSaveable { mutableIntStateOf(0) }
    var secondsLeft by rememberSaveable { mutableIntStateOf(RESEND_SECONDS) }
    LaunchedEffect(round) {
        secondsLeft = RESEND_SECONDS
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (secondsLeft > 0) {
            Text(
                text = stringResource(R.string.verify_resend_in, "0:%02d".format(secondsLeft)),
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500,
            )
        } else {
            Text("${stringResource(R.string.verify_not_received)} ", style = MaterialTheme.typography.bodyMedium, color = Slate500)
            // TODO(ANDROID-3): pedir a la API que reenvíe el código.
            FixoraTextLink(stringResource(R.string.verify_resend), onClick = { round++ })
        }
    }
}

@Composable
private fun PersonalStep(
    state: RegisterUiState,
    viewModel: RegisterViewModel,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    AuthScaffold(
        title = stringResource(R.string.personal_title),
        subtitle = AnnotatedString(stringResource(R.string.personal_subtitle)),
        onBack = onBack,
        step = AuthStep(RegisterStep.Personal.number, RegisterStep.total),
    ) {
        AuthTextField(
            value = state.name.value,
            onValueChange = viewModel::onNameChange,
            onFocusLost = viewModel::onNameBlur,
            label = stringResource(R.string.personal_name),
            placeholder = stringResource(R.string.personal_name_placeholder),
            error = state.nameError.message(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AuthTextField(
                value = state.lastNameP.value,
                onValueChange = viewModel::onLastNamePChange,
                onFocusLost = viewModel::onLastNamePBlur,
                label = stringResource(R.string.personal_lastname_p),
                placeholder = stringResource(R.string.personal_lastname_p_placeholder),
                error = state.lastNamePError.message(),
                modifier = Modifier.weight(1f),
            )
            AuthTextField(
                value = state.lastNameM.value,
                onValueChange = viewModel::onLastNameMChange,
                label = stringResource(R.string.personal_lastname_m),
                placeholder = stringResource(R.string.personal_lastname_m_placeholder),
                modifier = Modifier.weight(1f),
            )
        }
        AuthTextField(
            value = state.birthDate?.format(DateFormat).orEmpty(),
            onValueChange = {},
            label = stringResource(R.string.personal_birth_date),
            placeholder = stringResource(R.string.personal_birth_date_placeholder),
            error = state.birthDateError.message(),
            onClick = { showDatePicker = true },
        )
        AuthTextField(
            value = state.phone.value,
            onValueChange = viewModel::onPhoneChange,
            onFocusLost = viewModel::onPhoneBlur,
            label = stringResource(R.string.personal_phone),
            placeholder = stringResource(R.string.personal_phone_placeholder),
            prefix = stringResource(R.string.personal_phone_prefix),
            keyboardType = KeyboardType.Phone,
            error = state.phoneError.message(),
        )
        FixoraPrimaryButton(
            text = stringResource(R.string.personal_submit),
            // TODO(ANDROID-3): enviar el registro a la API antes de continuar.
            onClick = onSubmit,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
            initial = state.birthDate ?: state.today.minusYears(18),
            today = state.today,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.onBirthDateSelected(it)
                showDatePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initial: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // El selector de Material trabaja en milisegundos UTC.
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = (today.year - 100)..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text(stringResource(R.string.date_picker_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.date_picker_cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RegisterAccountPreview() {
    FixoraTheme { AccountStep(RegisterUiState(), RegisterViewModel(), {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RegisterVerifyPreview() {
    FixoraTheme { VerifyStep(RegisterUiState(code = "123"), RegisterViewModel(), {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RegisterPersonalPreview() {
    FixoraTheme { PersonalStep(RegisterUiState(), RegisterViewModel(), {}, {}) }
}
