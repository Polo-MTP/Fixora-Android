package com.fixora.app.feature.auth.ui.login

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.feature.auth.ui.components.AuthFooter
import com.fixora.app.feature.auth.ui.components.AuthScaffold
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.auth.ui.components.message

/** Inicio de sesión de clientes y proveedores ya aprobados (módulos 1 y 2). */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onSubmit: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LoginContent(
        state = state,
        onBack = onBack,
        onEmailChange = viewModel::onEmailChange,
        onEmailBlur = viewModel::onEmailBlur,
        onPasswordChange = viewModel::onPasswordChange,
        onPasswordBlur = viewModel::onPasswordBlur,
        onForgotPassword = onForgotPassword,
        onRegister = onRegister,
        onSubmit = { if (viewModel.submit()) onSubmit() },
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onEmailBlur: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordBlur: () -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onSubmit: () -> Unit,
) {
    AuthScaffold(
        title = stringResource(R.string.login_title),
        subtitle = AnnotatedString(stringResource(R.string.login_subtitle)),
        onBack = onBack,
        footer = {
            AuthFooter(
                question = stringResource(R.string.login_no_account),
                action = stringResource(R.string.login_register),
                onAction = onRegister,
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
        AuthTextField(
            value = state.password.value,
            onValueChange = onPasswordChange,
            onFocusLost = onPasswordBlur,
            label = stringResource(R.string.auth_field_password),
            placeholder = stringResource(R.string.login_password_placeholder),
            isPassword = true,
            error = state.passwordError.message(),
        )
        FixoraTextLink(
            text = stringResource(R.string.login_forgot_password),
            onClick = onForgotPassword,
            modifier = Modifier.align(Alignment.End),
        )
        FixoraPrimaryButton(
            text = stringResource(R.string.login_submit),
            onClick = onSubmit,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoginPreview() {
    FixoraTheme {
        LoginContent(LoginUiState(), {}, {}, {}, {}, {}, {}, {}, {})
    }
}
