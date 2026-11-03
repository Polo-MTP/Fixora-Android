package com.fixora.app.feature.auth.ui.register

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.ui.message
import com.fixora.app.core.ui.slideBackwardEnter
import com.fixora.app.core.ui.slideBackwardExit
import com.fixora.app.core.ui.slideForwardEnter
import com.fixora.app.core.ui.slideForwardExit
import com.fixora.app.feature.auth.data.AuthRepository
import com.fixora.app.feature.auth.ui.components.AuthFooter
import com.fixora.app.feature.auth.ui.components.AuthScaffold
import com.fixora.app.feature.auth.ui.components.AuthStep
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.auth.ui.components.BirthDateFormat
import com.fixora.app.feature.auth.ui.components.BirthDatePickerDialog
import com.fixora.app.feature.auth.ui.components.PasswordRulesView
import com.fixora.app.feature.auth.ui.components.VerificationCodeField
import com.fixora.app.feature.auth.ui.components.message
import kotlinx.coroutines.delay

/**
 * Registro del cliente (módulos 1 y 2): cuenta, datos personales y verificación del correo. Al
 * enviar los datos personales se crea la cuenta en la API, que manda el código al correo; al
 * verificarlo se muestra la confirmación y se entra al inicio. Los pasos viven en una sola
 * pantalla; «atrás» regresa al paso anterior.
 */
@Composable
fun RegisterScreen(
    onExit: () -> Unit,
    onLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val back = { if (!viewModel.back()) onExit() }
    // Sin sesión (no se pudo iniciar al verificar) el cliente entra desde el login.
    val finish = { if (!viewModel.finish()) onLogin() }
    BackHandler(enabled = state.step != RegisterStep.Account) {
        if (state.step == RegisterStep.Done) finish() else viewModel.back()
    }

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
            RegisterStep.Personal -> PersonalStep(state, viewModel, onBack = back)
            RegisterStep.Verify -> VerifyStep(state, viewModel, onBack = back)
            RegisterStep.Done -> RegisterSuccessScreen(onContinue = finish)
        }
    }
}

@Composable
private fun AccountStep(
    state: RegisterUiState,
    actions: RegisterActions,
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
            onValueChange = actions::onEmailChange,
            onFocusLost = actions::onEmailBlur,
            label = stringResource(R.string.auth_field_email),
            placeholder = stringResource(R.string.auth_field_email_placeholder),
            keyboardType = KeyboardType.Email,
            error = state.emailError.message(),
        )
        AuthTextField(
            value = state.password.value,
            onValueChange = actions::onPasswordChange,
            onFocusLost = actions::onPasswordBlur,
            label = stringResource(R.string.auth_field_password),
            placeholder = stringResource(R.string.register_password_placeholder),
            isPassword = true,
            error = state.passwordError.message(),
        )
        PasswordRulesView(state.passwordRules)
        AuthTextField(
            value = state.confirmPassword.value,
            onValueChange = actions::onConfirmPasswordChange,
            onFocusLost = actions::onConfirmPasswordBlur,
            label = stringResource(R.string.register_confirm_password),
            placeholder = stringResource(R.string.register_confirm_placeholder),
            isPassword = true,
            error = state.confirmPasswordError.message(),
        )
        // Aquí llega el «ya existe una cuenta con este correo» del paso 2.
        RegisterError(state.error)
        FixoraPrimaryButton(
            text = stringResource(R.string.register_continue),
            onClick = actions::continueFromAccount,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PersonalStep(
    state: RegisterUiState,
    actions: RegisterActions,
    onBack: () -> Unit,
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
            onValueChange = actions::onNameChange,
            onFocusLost = actions::onNameBlur,
            label = stringResource(R.string.personal_name),
            placeholder = stringResource(R.string.personal_name_placeholder),
            error = state.nameError.message(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AuthTextField(
                value = state.lastNameP.value,
                onValueChange = actions::onLastNamePChange,
                onFocusLost = actions::onLastNamePBlur,
                label = stringResource(R.string.personal_lastname_p),
                placeholder = stringResource(R.string.personal_lastname_p_placeholder),
                error = state.lastNamePError.message(),
                modifier = Modifier.weight(1f),
            )
            AuthTextField(
                value = state.lastNameM.value,
                onValueChange = actions::onLastNameMChange,
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
            onValueChange = actions::onPhoneChange,
            onFocusLost = actions::onPhoneBlur,
            label = stringResource(R.string.personal_phone),
            placeholder = stringResource(R.string.personal_phone_placeholder),
            prefix = stringResource(R.string.personal_phone_prefix),
            keyboardType = KeyboardType.Phone,
            error = state.phoneError.message(),
        )
        RegisterError(state.error)
        FixoraPrimaryButton(
            text = stringResource(R.string.personal_submit),
            onClick = actions::submitPersonalData,
            loading = state.isLoading,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    if (showDatePicker) {
        BirthDatePickerDialog(
            initial = state.birthDate ?: state.today.minusYears(18),
            today = state.today,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                actions.onBirthDateSelected(it)
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun VerifyStep(
    state: RegisterUiState,
    actions: RegisterActions,
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
                onAction = actions::changeEmail,
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
        VerificationCodeField(code = state.code, onCodeChange = actions::onCodeChange, label = label)
        if (state.codeResent) {
            Text(
                text = stringResource(R.string.verify_code_resent),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RegisterError(state.error)
        ResendCountdown(
            round = state.codeRound,
            startSeconds = state.resendSeconds,
            onResend = actions::resendCode,
        )
        FixoraPrimaryButton(
            text = stringResource(R.string.verify_submit),
            onClick = actions::verifyCode,
            enabled = state.isCodeComplete,
            loading = state.isLoading,
        )
    }
}

/**
 * Cuenta regresiva para poder pedir otro código; al llegar a cero muestra «Reenviar código». Se
 * reinicia cada vez que cambia [round] (se envió un código nuevo).
 */
@Composable
private fun ResendCountdown(round: Int, startSeconds: Int, onResend: () -> Unit) {
    var secondsLeft by rememberSaveable(round) { mutableIntStateOf(startSeconds) }
    LaunchedEffect(round) {
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
                text = stringResource(R.string.verify_resend_in, "%d:%02d".format(secondsLeft / 60, secondsLeft % 60)),
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500,
            )
        } else {
            Text("${stringResource(R.string.verify_not_received)} ", style = MaterialTheme.typography.bodyMedium, color = Slate500)
            FixoraTextLink(stringResource(R.string.verify_resend), onClick = onResend)
        }
    }
}

/** Error de la API en el paso actual. */
@Composable
private fun RegisterError(error: ApiError?) {
    error ?: return
    Text(
        text = error.registerMessage(),
        style = MaterialTheme.typography.bodySmall,
        color = ErrorRed,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** La API manda este error como 503, así que no trae un mensaje para el usuario. */
@Composable
private fun ApiError.registerMessage(): String =
    if (this is ApiError.Http && code == AuthRepository.EMAIL_SEND_FAILED) {
        stringResource(R.string.register_error_email_send)
    } else {
        message()
    }

private object PreviewActions : RegisterActions

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RegisterAccountPreview() {
    FixoraTheme { AccountStep(RegisterUiState(), PreviewActions, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RegisterPersonalPreview() {
    FixoraTheme { PersonalStep(RegisterUiState(), PreviewActions, {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RegisterVerifyPreview() {
    FixoraTheme { VerifyStep(RegisterUiState(code = "123"), PreviewActions, {}) }
}
