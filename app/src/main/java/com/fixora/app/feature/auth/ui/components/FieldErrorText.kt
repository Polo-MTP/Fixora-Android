package com.fixora.app.feature.auth.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fixora.app.R
import com.fixora.app.feature.auth.domain.FieldError

/** Texto que se muestra bajo el campo para cada error de validación. */
@Composable
fun FieldError?.message(): String? = when (this) {
    null -> null
    FieldError.Required -> stringResource(R.string.auth_error_required)
    FieldError.EmailEmpty -> stringResource(R.string.auth_error_email_empty)
    FieldError.EmailInvalid -> stringResource(R.string.auth_error_email_invalid)
    FieldError.PasswordWeak -> stringResource(R.string.auth_error_password_weak)
    FieldError.ConfirmEmpty -> stringResource(R.string.auth_error_confirm_empty)
    FieldError.PasswordMismatch -> stringResource(R.string.auth_error_password_mismatch)
    FieldError.PhoneInvalid -> stringResource(R.string.auth_error_phone)
    FieldError.BirthDateEmpty -> stringResource(R.string.auth_error_birth_date_empty)
    FieldError.Underage -> stringResource(R.string.auth_error_underage)
}
