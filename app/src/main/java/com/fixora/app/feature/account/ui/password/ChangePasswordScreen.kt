package com.fixora.app.feature.account.ui.password

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.account.ui.components.SampleDataNotice
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.auth.ui.components.PasswordRulesView
import com.fixora.app.feature.auth.ui.components.message

/** Cambiar contraseña (ANDROID-5). Al guardar regresa a Mi cuenta. */
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.change_password_saved)
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    ChangePasswordContent(
        state = state,
        onBack = onBack,
        onCurrentChange = viewModel::onCurrentChange,
        onCurrentBlur = viewModel::onCurrentBlur,
        onNewChange = viewModel::onNewChange,
        onNewBlur = viewModel::onNewBlur,
        onConfirmChange = viewModel::onConfirmChange,
        onConfirmBlur = viewModel::onConfirmBlur,
        onSave = viewModel::save,
    )
}

@Composable
private fun ChangePasswordContent(
    state: ChangePasswordUiState,
    onBack: () -> Unit,
    onCurrentChange: (String) -> Unit,
    onCurrentBlur: () -> Unit,
    onNewChange: (String) -> Unit,
    onNewBlur: () -> Unit,
    onConfirmChange: (String) -> Unit,
    onConfirmBlur: () -> Unit,
    onSave: () -> Unit,
) {
    AccountScaffold(
        title = stringResource(R.string.change_password_title),
        onBack = onBack,
        bottomBar = {
            FixoraPrimaryButton(
                text = stringResource(R.string.change_password_submit),
                onClick = onSave,
                loading = state.isSaving,
            )
        },
    ) {
        SampleDataNotice()
        AuthTextField(
            value = state.current.value,
            onValueChange = onCurrentChange,
            onFocusLost = onCurrentBlur,
            label = stringResource(R.string.change_password_current),
            placeholder = stringResource(R.string.change_password_current_placeholder),
            isPassword = true,
            error = state.currentError.message(),
        )
        AuthTextField(
            value = state.new.value,
            onValueChange = onNewChange,
            onFocusLost = onNewBlur,
            label = stringResource(R.string.change_password_new),
            placeholder = stringResource(R.string.register_password_placeholder),
            isPassword = true,
            error = state.newError.message(),
        )
        PasswordRulesView(state.newRules)
        AuthTextField(
            value = state.confirm.value,
            onValueChange = onConfirmChange,
            onFocusLost = onConfirmBlur,
            label = stringResource(R.string.register_confirm_password),
            placeholder = stringResource(R.string.register_confirm_placeholder),
            isPassword = true,
            error = state.confirmError.message(),
        )
        state.error?.let { FormErrorText(it.message()) }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChangePasswordPreview() {
    FixoraTheme {
        ChangePasswordContent(ChangePasswordUiState(), {}, {}, {}, {}, {}, {}, {}, {})
    }
}
