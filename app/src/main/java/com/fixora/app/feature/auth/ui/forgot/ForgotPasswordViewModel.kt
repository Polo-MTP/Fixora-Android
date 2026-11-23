package com.fixora.app.feature.auth.ui.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.auth.data.PasswordRecoveryRepository
import com.fixora.app.feature.auth.domain.AuthValidator
import com.fixora.app.feature.auth.ui.FormField
import com.fixora.app.feature.auth.ui.components.RESEND_SECONDS
import com.fixora.app.feature.auth.ui.visibleIf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Pasos de «Olvidé mi contraseña» (ANDROID-4): pedir el código, poner la nueva y confirmación. */
enum class ForgotPasswordStep { Email, Reset, Done }

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.Email,
    val email: FormField = FormField(),
    val code: String = "",
    val password: FormField = FormField(),
    val confirmPassword: FormField = FormField(),
    /** Cambia cada vez que se envía un código, para reiniciar la cuenta regresiva. */
    val codeRound: Int = 0,
    /** Segundos de espera para pedir otro código (0 si se puede pedir ya). */
    val resendSeconds: Int = RESEND_SECONDS,
    val codeResent: Boolean = false,
    val submittedSteps: Set<ForgotPasswordStep> = emptySet(),
    val isLoading: Boolean = false,
    val error: ApiError? = null,
) {
    private fun show(field: FormField, step: ForgotPasswordStep) = field.touched || step in submittedSteps

    val emailError get() = AuthValidator.email(email.value).visibleIf(show(email, ForgotPasswordStep.Email))
    val passwordRules get() = AuthValidator.passwordRules(password.value)
    val passwordError get() = AuthValidator.newPassword(password.value).visibleIf(show(password, ForgotPasswordStep.Reset))
    val confirmPasswordError get() =
        AuthValidator.confirmPassword(password.value, confirmPassword.value)
            .visibleIf(show(confirmPassword, ForgotPasswordStep.Reset))

    val isCodeComplete get() = AuthValidator.isCodeComplete(code)

    val isEmailValid get() = AuthValidator.email(email.value) == null

    /** La nueva contraseña cumple los mismos requisitos que el registro. */
    val isResetValid get() = isCodeComplete &&
        AuthValidator.newPassword(password.value) == null &&
        AuthValidator.confirmPassword(password.value, confirmPassword.value) == null
}

/** Lo que pueden hacer los pasos. Vacío por defecto para las vistas previas. */
interface ForgotPasswordActions {
    fun onEmailChange(value: String) {}
    fun onEmailBlur() {}
    fun requestCode() {}

    fun onCodeChange(value: String) {}
    fun onPasswordChange(value: String) {}
    fun onPasswordBlur() {}
    fun onConfirmPasswordChange(value: String) {}
    fun onConfirmPasswordBlur() {}
    fun resetPassword() {}
    fun resendCode() {}
    fun changeEmail() {}
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val recoveryRepository: PasswordRecoveryRepository,
) : ViewModel(), ForgotPasswordActions {
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    // Al corregir un campo, el error de la API anterior ya no aplica.
    private fun edit(change: (ForgotPasswordUiState) -> ForgotPasswordUiState) =
        _uiState.update { change(it).copy(error = null) }

    override fun onEmailChange(value: String) = edit { it.copy(email = it.email.onChange(value)) }
    override fun onEmailBlur() = _uiState.update { it.copy(email = it.email.onBlur()) }
    override fun onCodeChange(value: String) = edit { it.copy(code = value) }
    override fun onPasswordChange(value: String) = edit { it.copy(password = it.password.onChange(value)) }
    override fun onPasswordBlur() = _uiState.update { it.copy(password = it.password.onBlur()) }
    override fun onConfirmPasswordChange(value: String) = edit { it.copy(confirmPassword = it.confirmPassword.onChange(value)) }
    override fun onConfirmPasswordBlur() = _uiState.update { it.copy(confirmPassword = it.confirmPassword.onBlur()) }

    /** Paso 1: pide el código al correo y pasa a escribirlo junto con la contraseña nueva. */
    override fun requestCode() {
        val state = _uiState.updateAndGet { it.copy(submittedSteps = it.submittedSteps + ForgotPasswordStep.Email) }
        if (!state.isEmailValid || state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = recoveryRepository.requestCode(state.email.value)
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(
                        isLoading = false,
                        step = ForgotPasswordStep.Reset,
                        code = "",
                        codeRound = it.codeRound + 1,
                        resendSeconds = RESEND_SECONDS,
                        codeResent = false,
                    )
                    is ApiResult.Failure -> it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    /** Paso 2: cambia la contraseña con el código. Si el código ya no sirve, se borra para pedir otro. */
    override fun resetPassword() {
        val state = _uiState.updateAndGet { it.copy(submittedSteps = it.submittedSteps + ForgotPasswordStep.Reset) }
        if (!state.isResetValid || state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null, codeResent = false) }
        viewModelScope.launch {
            val result = recoveryRepository.resetPassword(state.email.value, state.code, state.password.value)
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(isLoading = false, step = ForgotPasswordStep.Done)
                    is ApiResult.Failure -> it.copy(
                        isLoading = false,
                        error = result.error,
                        code = if (result.error.isCodeGone()) "" else it.code,
                    )
                }
            }
        }
    }

    override fun resendCode() {
        val state = _uiState.value
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null, codeResent = false) }
        viewModelScope.launch {
            val result = recoveryRepository.requestCode(state.email.value)
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(
                        isLoading = false,
                        code = "",
                        codeRound = it.codeRound + 1,
                        resendSeconds = RESEND_SECONDS,
                        codeResent = true,
                    )
                    is ApiResult.Failure -> it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    /** «¿Correo equivocado?»: regresa al paso 1. */
    override fun changeEmail() = _uiState.update { it.copy(step = ForgotPasswordStep.Email, error = null) }

    /** Regresa un paso. Devuelve false si hay que salir de la pantalla. */
    fun back(): Boolean {
        if (_uiState.value.step != ForgotPasswordStep.Reset) return false
        changeEmail()
        return true
    }

    private fun ApiError.isCodeGone() = this is ApiError.Http && code in setOf(
        PasswordRecoveryRepository.RECOVERY_CODE_EXPIRED,
        PasswordRecoveryRepository.RECOVERY_ATTEMPTS_EXCEEDED,
    )
}
