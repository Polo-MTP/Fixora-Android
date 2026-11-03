package com.fixora.app.feature.auth.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.session.Session
import com.fixora.app.feature.auth.data.AuthRepository
import com.fixora.app.feature.auth.data.NewClient
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
import java.time.LocalDate
import javax.inject.Inject

/** Segundos que la API pide esperar entre un envío del código y el siguiente. */
const val RESEND_SECONDS = 60

/**
 * Pasos del registro del cliente (módulos 1 y 2 en Plane). La API pide los datos personales para
 * crear la cuenta, así que el código se verifica al final. [Done] es la confirmación: no cuenta
 * en el indicador de pasos.
 */
enum class RegisterStep(val number: Int) {
    Account(1),
    Personal(2),
    Verify(3),
    Done(0),
    ;

    companion object {
        val total = entries.count { it != Done }
    }
}

data class RegisterUiState(
    val step: RegisterStep = RegisterStep.Account,
    // Paso 1 · cuenta (users)
    val email: FormField = FormField(),
    val password: FormField = FormField(),
    val confirmPassword: FormField = FormField(),
    // Paso 2 · datos personales (personal_data)
    val name: FormField = FormField(),
    val lastNameP: FormField = FormField(),
    val lastNameM: FormField = FormField(),
    val birthDate: LocalDate? = null,
    val phone: FormField = FormField(),
    // Paso 3 · verificación del correo
    val code: String = "",
    /** Cambia cada vez que se envía un código, para reiniciar la cuenta regresiva. */
    val codeRound: Int = 0,
    /** Segundos de espera para pedir otro código (0 si se puede pedir ya). */
    val resendSeconds: Int = RESEND_SECONDS,
    val codeResent: Boolean = false,
    /** Correo con el que ya se creó la cuenta en la API, para no crearla dos veces. */
    val registeredEmail: String? = null,
    val submittedSteps: Set<RegisterStep> = emptySet(),
    val isLoading: Boolean = false,
    val error: ApiError? = null,
    val today: LocalDate = LocalDate.now(),
) {
    private fun show(field: FormField, step: RegisterStep) = field.touched || step in submittedSteps

    val emailError get() = AuthValidator.email(email.value).visibleIf(show(email, RegisterStep.Account))
    val passwordRules get() = AuthValidator.passwordRules(password.value)
    val passwordError get() = AuthValidator.newPassword(password.value).visibleIf(show(password, RegisterStep.Account))
    val confirmPasswordError get() =
        AuthValidator.confirmPassword(password.value, confirmPassword.value).visibleIf(show(confirmPassword, RegisterStep.Account))

    val nameError get() = AuthValidator.required(name.value).visibleIf(show(name, RegisterStep.Personal))
    val lastNamePError get() = AuthValidator.required(lastNameP.value).visibleIf(show(lastNameP, RegisterStep.Personal))
    val birthDateError get() =
        AuthValidator.birthDate(birthDate, today).visibleIf(birthDate != null || RegisterStep.Personal in submittedSteps)
    val phoneError get() = AuthValidator.phone(phone.value).visibleIf(show(phone, RegisterStep.Personal))

    val isCodeComplete get() = AuthValidator.isCodeComplete(code)

    val isAccountValid get() = AuthValidator.email(email.value) == null &&
        AuthValidator.newPassword(password.value) == null &&
        AuthValidator.confirmPassword(password.value, confirmPassword.value) == null

    val isPersonalValid get() = AuthValidator.required(name.value) == null &&
        AuthValidator.required(lastNameP.value) == null &&
        AuthValidator.birthDate(birthDate, today) == null &&
        AuthValidator.phone(phone.value) == null

    val normalizedEmail get() = email.value.trim().lowercase()
}

/** Lo que pueden hacer los pasos del registro. Vacío por defecto para las vistas previas. */
interface RegisterActions {
    fun onEmailChange(value: String) {}
    fun onEmailBlur() {}
    fun onPasswordChange(value: String) {}
    fun onPasswordBlur() {}
    fun onConfirmPasswordChange(value: String) {}
    fun onConfirmPasswordBlur() {}
    fun continueFromAccount() {}

    fun onNameChange(value: String) {}
    fun onNameBlur() {}
    fun onLastNamePChange(value: String) {}
    fun onLastNamePBlur() {}
    fun onLastNameMChange(value: String) {}
    fun onBirthDateSelected(date: LocalDate) {}
    fun onPhoneChange(value: String) {}
    fun onPhoneBlur() {}
    fun submitPersonalData() {}

    fun onCodeChange(value: String) {}
    fun verifyCode() {}
    fun resendCode() {}
    fun changeEmail() {}
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel(), RegisterActions {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    /** Sesión obtenida al verificar; se guarda al salir de la confirmación. */
    private var pendingSession: Session? = null

    // Al corregir un campo, el error de la API anterior ya no aplica.
    private fun edit(change: (RegisterUiState) -> RegisterUiState) = _uiState.update { change(it).copy(error = null) }

    override fun onEmailChange(value: String) = edit { it.copy(email = it.email.onChange(value)) }
    override fun onEmailBlur() = _uiState.update { it.copy(email = it.email.onBlur()) }
    override fun onPasswordChange(value: String) = edit { it.copy(password = it.password.onChange(value)) }
    override fun onPasswordBlur() = _uiState.update { it.copy(password = it.password.onBlur()) }
    override fun onConfirmPasswordChange(value: String) = edit { it.copy(confirmPassword = it.confirmPassword.onChange(value)) }
    override fun onConfirmPasswordBlur() = _uiState.update { it.copy(confirmPassword = it.confirmPassword.onBlur()) }

    override fun onNameChange(value: String) = edit { it.copy(name = it.name.onChange(value)) }
    override fun onNameBlur() = _uiState.update { it.copy(name = it.name.onBlur()) }
    override fun onLastNamePChange(value: String) = edit { it.copy(lastNameP = it.lastNameP.onChange(value)) }
    override fun onLastNamePBlur() = _uiState.update { it.copy(lastNameP = it.lastNameP.onBlur()) }
    override fun onLastNameMChange(value: String) = edit { it.copy(lastNameM = it.lastNameM.onChange(value)) }
    override fun onBirthDateSelected(date: LocalDate) = edit { it.copy(birthDate = date) }
    override fun onPhoneChange(value: String) = edit {
        it.copy(phone = it.phone.onChange(value.filter { c -> c in '0'..'9' }.take(AuthValidator.PHONE_LENGTH)))
    }
    override fun onPhoneBlur() = _uiState.update { it.copy(phone = it.phone.onBlur()) }

    override fun onCodeChange(value: String) = edit { it.copy(code = value) }

    /** Paso 1: si la cuenta es válida, pasa a los datos personales. */
    override fun continueFromAccount() {
        val state = _uiState.updateAndGet { it.copy(submittedSteps = it.submittedSteps + RegisterStep.Account) }
        if (state.isAccountValid) goTo(RegisterStep.Personal)
    }

    /**
     * Paso 2: crea la cuenta en la API y pasa a verificar el correo. Si la cuenta ya se creó con
     * este correo (el cliente regresó desde el paso 3), solo avanza.
     */
    override fun submitPersonalData() {
        val state = _uiState.updateAndGet { it.copy(submittedSteps = it.submittedSteps + RegisterStep.Personal) }
        if (!state.isPersonalValid || state.isLoading) return
        if (state.registeredEmail == state.normalizedEmail) return goTo(RegisterStep.Verify)

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val client = NewClient(
                email = state.email.value,
                password = state.password.value,
                name = state.name.value,
                lastNameP = state.lastNameP.value,
                lastNameM = state.lastNameM.value,
                birthDate = checkNotNull(state.birthDate),
                phone = state.phone.value,
            )
            val result = authRepository.register(client)
            val error = (result as? ApiResult.Failure)?.error
            _uiState.update {
                when {
                    error == null -> it.copy(
                        isLoading = false,
                        registeredEmail = state.normalizedEmail,
                        step = RegisterStep.Verify,
                        code = "",
                        codeRound = it.codeRound + 1,
                        resendSeconds = RESEND_SECONDS,
                        codeResent = false,
                    )
                    // El correo ya tiene cuenta: se corrige en el paso 1.
                    error.hasCode(AuthRepository.EMAIL_ALREADY_REGISTERED) ->
                        it.copy(isLoading = false, step = RegisterStep.Account, error = error)
                    // La cuenta sí se creó; falta el código, que se puede pedir de inmediato.
                    error.hasCode(AuthRepository.EMAIL_SEND_FAILED) -> it.copy(
                        isLoading = false,
                        registeredEmail = state.normalizedEmail,
                        step = RegisterStep.Verify,
                        code = "",
                        codeRound = it.codeRound + 1,
                        resendSeconds = 0,
                        codeResent = false,
                        error = error,
                    )
                    else -> it.copy(isLoading = false, error = error)
                }
            }
        }
    }

    /** Paso 3: verifica el código y, si es correcto, inicia la sesión del cliente. */
    override fun verifyCode() {
        val state = _uiState.value
        if (!state.isCodeComplete || state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.verifyEmail(state.email.value, state.code)
            val error = (result as? ApiResult.Failure)?.error
            if (error != null && !error.hasCode(AuthRepository.EMAIL_ALREADY_VERIFIED)) {
                _uiState.update { it.copy(isLoading = false, error = error) }
                return@launch
            }
            // Si no se pudiera iniciar sesión aquí, la confirmación lleva al login.
            pendingSession = (authRepository.signIn(state.email.value, state.password.value) as? ApiResult.Success)?.data
            _uiState.update { it.copy(isLoading = false, step = RegisterStep.Done) }
        }
    }

    override fun resendCode() {
        val state = _uiState.value
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null, codeResent = false) }
        viewModelScope.launch {
            val result = authRepository.resendVerificationCode(state.email.value)
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

    /** «¿Correo equivocado?»: regresa al paso 1; con otro correo se crea una cuenta nueva. */
    override fun changeEmail() = goTo(RegisterStep.Account)

    /**
     * Sale de la confirmación guardando la sesión (FixoraNavHost lleva al inicio). Devuelve false
     * si no hay sesión y el cliente debe iniciar sesión a mano.
     */
    fun finish(): Boolean {
        val session = pendingSession ?: return false
        viewModelScope.launch { authRepository.saveSession(session) }
        return true
    }

    /** Regresa un paso. Devuelve false si ya estaba en el primero (hay que salir del registro). */
    fun back(): Boolean {
        val step = _uiState.value.step
        if (step == RegisterStep.Account || step == RegisterStep.Done) return false
        goTo(RegisterStep.entries[step.ordinal - 1])
        return true
    }

    private fun goTo(step: RegisterStep) = _uiState.update { it.copy(step = step, error = null) }

    private fun ApiError.hasCode(code: String) = this is ApiError.Http && this.code == code
}
