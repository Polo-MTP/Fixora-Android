package com.fixora.app.feature.auth.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.auth.data.AuthRepository
import com.fixora.app.feature.auth.domain.AuthValidator
import com.fixora.app.feature.auth.ui.FormField
import com.fixora.app.feature.auth.ui.visibleIf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: FormField = FormField(),
    val password: FormField = FormField(),
    val submitted: Boolean = false,
    val isLoading: Boolean = false,
    val error: ApiError? = null,
) {
    val emailError get() = AuthValidator.email(email.value).visibleIf(email.touched || submitted)
    val passwordError get() = AuthValidator.required(password.value).visibleIf(password.touched || submitted)
    val isValid get() = AuthValidator.email(email.value) == null && AuthValidator.required(password.value) == null
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Al corregir un campo, el error de la API anterior ya no aplica.
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = it.email.onChange(value), error = null) }
    fun onEmailBlur() = _uiState.update { it.copy(email = it.email.onBlur()) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = it.password.onChange(value), error = null) }
    fun onPasswordBlur() = _uiState.update { it.copy(password = it.password.onBlur()) }

    /**
     * Valida el formulario y, si está completo, inicia sesión con la API. Al guardarse la sesión,
     * FixoraNavHost lleva al inicio; mientras tanto el botón sigue cargando.
     */
    fun submit() {
        val state = _uiState.updateAndGet { it.copy(submitted = true) }
        if (!state.isValid || state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.login(state.email.value, state.password.value)
            if (result is ApiResult.Failure) _uiState.update { it.copy(isLoading = false, error = result.error) }
        }
    }
}
