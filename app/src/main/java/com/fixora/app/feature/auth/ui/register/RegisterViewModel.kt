package com.fixora.app.feature.auth.ui.register

import androidx.lifecycle.ViewModel
import com.fixora.app.feature.auth.domain.AuthValidator
import com.fixora.app.feature.auth.ui.FormField
import com.fixora.app.feature.auth.ui.visibleIf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Pasos del registro del cliente (módulos 1 y 2 en Plane). */
enum class RegisterStep(val number: Int) {
    Account(1),
    Verify(2),
    Personal(3),
    ;

    companion object {
        val total = entries.size
    }
}

data class RegisterUiState(
    val step: RegisterStep = RegisterStep.Account,
    // Paso 1 · cuenta (users)
    val email: FormField = FormField(),
    val password: FormField = FormField(),
    val confirmPassword: FormField = FormField(),
    // Paso 2 · verificación del correo
    val code: String = "",
    // Paso 3 · datos personales (personal_data)
    val name: FormField = FormField(),
    val lastNameP: FormField = FormField(),
    val lastNameM: FormField = FormField(),
    val birthDate: LocalDate? = null,
    val phone: FormField = FormField(),
    val submittedSteps: Set<RegisterStep> = emptySet(),
    val today: LocalDate = LocalDate.now(),
) {
    private fun show(field: FormField, step: RegisterStep) = field.touched || step in submittedSteps

    val emailError get() = AuthValidator.email(email.value).visibleIf(show(email, RegisterStep.Account))
    val passwordRules get() = AuthValidator.passwordRules(password.value)
    val passwordError get() = AuthValidator.newPassword(password.value).visibleIf(show(password, RegisterStep.Account))
    val confirmPasswordError get() =
        AuthValidator.confirmPassword(password.value, confirmPassword.value).visibleIf(show(confirmPassword, RegisterStep.Account))

    val isCodeComplete get() = AuthValidator.isCodeComplete(code)

    val nameError get() = AuthValidator.required(name.value).visibleIf(show(name, RegisterStep.Personal))
    val lastNamePError get() = AuthValidator.required(lastNameP.value).visibleIf(show(lastNameP, RegisterStep.Personal))
    val birthDateError get() =
        AuthValidator.birthDate(birthDate, today).visibleIf(birthDate != null || RegisterStep.Personal in submittedSteps)
    val phoneError get() = AuthValidator.phone(phone.value).visibleIf(show(phone, RegisterStep.Personal))

    val isAccountValid get() = AuthValidator.email(email.value) == null &&
        AuthValidator.newPassword(password.value) == null &&
        AuthValidator.confirmPassword(password.value, confirmPassword.value) == null

    val isPersonalValid get() = AuthValidator.required(name.value) == null &&
        AuthValidator.required(lastNameP.value) == null &&
        AuthValidator.birthDate(birthDate, today) == null &&
        AuthValidator.phone(phone.value) == null
}

class RegisterViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = it.email.onChange(value)) }
    fun onEmailBlur() = _uiState.update { it.copy(email = it.email.onBlur()) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = it.password.onChange(value)) }
    fun onPasswordBlur() = _uiState.update { it.copy(password = it.password.onBlur()) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = it.confirmPassword.onChange(value)) }
    fun onConfirmPasswordBlur() = _uiState.update { it.copy(confirmPassword = it.confirmPassword.onBlur()) }

    fun onCodeChange(value: String) = _uiState.update { it.copy(code = value) }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = it.name.onChange(value)) }
    fun onNameBlur() = _uiState.update { it.copy(name = it.name.onBlur()) }
    fun onLastNamePChange(value: String) = _uiState.update { it.copy(lastNameP = it.lastNameP.onChange(value)) }
    fun onLastNamePBlur() = _uiState.update { it.copy(lastNameP = it.lastNameP.onBlur()) }
    fun onLastNameMChange(value: String) = _uiState.update { it.copy(lastNameM = it.lastNameM.onChange(value)) }
    fun onBirthDateSelected(date: LocalDate) = _uiState.update { it.copy(birthDate = date) }
    fun onPhoneChange(value: String) = _uiState.update {
        it.copy(phone = it.phone.onChange(value.filter { c -> c in '0'..'9' }.take(AuthValidator.PHONE_LENGTH)))
    }
    fun onPhoneBlur() = _uiState.update { it.copy(phone = it.phone.onBlur()) }

    /** Paso 1: si la cuenta es válida, pasa a verificar el correo. */
    fun continueFromAccount() {
        _uiState.update { it.copy(submittedSteps = it.submittedSteps + RegisterStep.Account) }
        if (_uiState.value.isAccountValid) goTo(RegisterStep.Verify)
    }

    /** Paso 2: con el código completo pasa a los datos personales. */
    fun verifyCode() {
        if (_uiState.value.isCodeComplete) goTo(RegisterStep.Personal)
    }

    /** Paso 3: muestra los errores pendientes y devuelve si se puede crear la cuenta. */
    fun submitPersonalData(): Boolean {
        _uiState.update { it.copy(submittedSteps = it.submittedSteps + RegisterStep.Personal) }
        return _uiState.value.isPersonalValid
    }

    fun changeEmail() = goTo(RegisterStep.Account)

    /** Regresa un paso. Devuelve false si ya estaba en el primero (hay que salir del registro). */
    fun back(): Boolean {
        val previous = RegisterStep.entries.getOrNull(_uiState.value.step.ordinal - 1) ?: return false
        goTo(previous)
        return true
    }

    private fun goTo(step: RegisterStep) = _uiState.update { it.copy(step = step) }
}
