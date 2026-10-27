package com.fixora.app.feature.auth.ui.forgot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.feature.auth.ui.components.AuthFooter
import com.fixora.app.feature.auth.ui.components.AuthScaffold
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.auth.ui.components.message

/** Recuperar contraseña: pide el correo para enviar un código (propuesta del módulo 1 y 2). */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onSubmit: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ForgotPasswordContent(
        state = state,
        onBack = onBack,
        onEmailChange = viewModel::onEmailChange,
        onEmailBlur = viewModel::onEmailBlur,
        onLogin = onLogin,
        onSubmit = { if (viewModel.submit()) onSubmit() },
    )
}

@Composable
private fun ForgotPasswordContent(
    state: ForgotPasswordUiState,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onEmailBlur: () -> Unit,
    onLogin: () -> Unit,
    onSubmit: () -> Unit,
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
            onValueChange = onEmailChange,
            onFocusLost = onEmailBlur,
            label = stringResource(R.string.auth_field_email),
            placeholder = stringResource(R.string.auth_field_email_placeholder),
            keyboardType = KeyboardType.Email,
            error = state.emailError.message(),
        )
        FixoraPrimaryButton(
            text = stringResource(R.string.forgot_submit),
            onClick = onSubmit,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ForgotPasswordPreview() {
    FixoraTheme {
        ForgotPasswordContent(ForgotPasswordUiState(), {}, {}, {}, {}, {})
    }
}
