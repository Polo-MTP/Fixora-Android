package com.fixora.app.feature.auth.ui.login

import androidx.lifecycle.ViewModel
import com.fixora.app.feature.auth.domain.AuthValidator
import com.fixora.app.feature.auth.ui.FormField
import com.fixora.app.feature.auth.ui.visibleIf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val email: FormField = FormField(),
    val password: FormField = FormField(),
    val submitted: Boolean = false,
) {
    val emailError get() = AuthValidator.email(email.value).visibleIf(email.touched || submitted)
    val passwordError get() = AuthValidator.required(password.value).visibleIf(password.touched || submitted)
    val isValid get() = AuthValidator.email(email.value) == null && AuthValidator.required(password.value) == null
}

class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = it.email.onChange(value)) }
    fun onEmailBlur() = _uiState.update { it.copy(email = it.email.onBlur()) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = it.password.onChange(value)) }
    fun onPasswordBlur() = _uiState.update { it.copy(password = it.password.onBlur()) }

    /** Muestra los errores pendientes y devuelve si el formulario se puede enviar. */
    fun submit(): Boolean {
        _uiState.update { it.copy(submitted = true) }
        return _uiState.value.isValid
    }
}
