package com.fixora.app.feature.auth.ui.forgot

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.fixora.app.feature.auth.data.PasswordRecoveryRepository
import com.fixora.app.feature.auth.ui.FormField
import com.fixora.app.feature.auth.ui.components.AuthFooter
import com.fixora.app.feature.auth.ui.components.AuthScaffold
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.auth.ui.components.PasswordRulesView
import com.fixora.app.feature.auth.ui.components.ResendCountdown
import com.fixora.app.feature.auth.ui.components.VerificationCodeField
import com.fixora.app.feature.auth.ui.components.message
import com.fixora.app.feature.auth.ui.register.RegisterSuccessScreen

/**
 * Recuperar contraseña (ANDROID-4, con API-9): pide el correo, la API manda un código de 6 dígitos
 * y con él se pone la contraseña nueva. Al terminar regresa al login. Los pasos viven en una sola
 * pantalla; «atrás» regresa al paso anterior.
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val back = { if (!viewModel.back()) onBack() }
    BackHandler(enabled = state.step != ForgotPasswordStep.Email) {
        if (state.step == ForgotPasswordStep.Done) onLogin() else viewModel.back()
    }

    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            // Igual que el registro: avanzar desliza hacia la izquierda y regresar hacia la derecha.
            if (targetState.ordinal > initialState.ordinal) {
                (slideForwardEnter() togetherWith slideForwardExit()).apply { targetContentZIndex = 1f }
            } else {
                (slideBackwardEnter() togetherWith slideBackwardExit()).apply { targetContentZIndex = -1f }
            }
        },
        label = "forgotPasswordStep",
    ) { step ->
        when (step) {
            ForgotPasswordStep.Email -> EmailStep(state, viewModel, onBack = back, onLogin = onLogin)
            ForgotPasswordStep.Reset -> ResetStep(state, viewModel, onBack = back)
            ForgotPasswordStep.Done -> RegisterSuccessScreen(
                onContinue = onLogin,
                title = stringResource(R.string.forgot_success_title),
                subtitle = stringResource(R.string.forgot_success_subtitle),
                action = stringResource(R.string.forgot_success_continue),
            )
        }
    }
}

@Composable
private fun EmailStep(
    state: ForgotPasswordUiState,
    actions: ForgotPasswordActions,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    AuthScaffold(
        title = stringResource(R.string.forgot_title),
        subtitle = AnnotatedString(stringResource(R.string.forgot_subtitle)),
        onBack = onBack,
        footer = {
            AuthFooter(
                question = stringResource(R.string.forgot_remembered),
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
        ForgotError(state.error)
        FixoraPrimaryButton(
            text = stringResource(R.string.forgot_submit),
            onClick = actions::requestCode,
            loading = state.isLoading,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ResetStep(
    state: ForgotPasswordUiState,
    actions: ForgotPasswordActions,
    onBack: () -> Unit,
) {
    val email = state.email.value.trim()
    val subtitleText = stringResource(R.string.forgot_reset_subtitle, email)
    val subtitle = buildAnnotatedString {
        val start = subtitleText.indexOf(email)
        append(subtitleText)
        if (start >= 0) addStyle(SpanStyle(color = White, fontWeight = FontWeight.Bold), start, start + email.length)
    }
    val codeLabel = stringResource(R.string.verify_code_label)

    AuthScaffold(
        title = stringResource(R.string.forgot_reset_title),
        subtitle = subtitle,
        onBack = onBack,
        footer = {
            AuthFooter(
                question = stringResource(R.string.verify_wrong_email),
                action = stringResource(R.string.verify_change_email),
                onAction = actions::changeEmail,
            )
        },
    ) {
        Text(
            text = codeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = Navy900,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VerificationCodeField(code = state.code, onCodeChange = actions::onCodeChange, label = codeLabel)
        if (state.codeResent) {
            Text(
                text = stringResource(R.string.verify_code_resent),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ResendCountdown(
            round = state.codeRound,
            startSeconds = state.resendSeconds,
            onResend = actions::resendCode,
        )
        AuthTextField(
            value = state.password.value,
            onValueChange = actions::onPasswordChange,
            onFocusLost = actions::onPasswordBlur,
            label = stringResource(R.string.change_password_new),
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
        ForgotError(state.error)
        FixoraPrimaryButton(
            text = stringResource(R.string.forgot_reset_submit),
            onClick = actions::resetPassword,
            enabled = state.isCodeComplete,
            loading = state.isLoading,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Error de la API en el paso actual. */
@Composable
private fun ForgotError(error: ApiError?) {
    error ?: return
    Text(
        text = error.forgotMessage(),
        style = MaterialTheme.typography.bodySmall,
        color = ErrorRed,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** La API manda este error como 503, así que no trae un mensaje para el usuario. */
@Composable
private fun ApiError.forgotMessage(): String =
    if (this is ApiError.Http && code == PasswordRecoveryRepository.RECOVERY_EMAIL_FAILED) {
        stringResource(R.string.forgot_error_email_send)
    } else {
        message()
    }

private object PreviewActions : ForgotPasswordActions

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ForgotPasswordEmailPreview() {
    FixoraTheme { EmailStep(ForgotPasswordUiState(), PreviewActions, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ForgotPasswordResetPreview() {
    FixoraTheme {
        ResetStep(
            ForgotPasswordUiState(step = ForgotPasswordStep.Reset, email = FormField("ana@correo.com"), code = "123"),
            PreviewActions,
            {},
        )
    }
}
