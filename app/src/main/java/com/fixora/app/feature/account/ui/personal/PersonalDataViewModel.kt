package com.fixora.app.feature.account.ui.personal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.session.SessionRepository
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.PersonalData
import com.fixora.app.feature.auth.domain.AuthValidator
import com.fixora.app.feature.auth.domain.FieldError
import com.fixora.app.feature.auth.ui.FormField
import com.fixora.app.feature.auth.ui.visibleIf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PersonalDataUiState(
    val isLoaded: Boolean = false,
    val email: String = "",
    val name: FormField = FormField(),
    val lastNameP: FormField = FormField(),
    val lastNameM: FormField = FormField(),
    val birthDate: LocalDate? = null,
    val phone: FormField = FormField(),
    val submitted: Boolean = false,
    val isSaving: Boolean = false,
    val error: ApiError? = null,
    val isSaved: Boolean = false,
    val today: LocalDate = LocalDate.now(),
) {
    private fun show(field: FormField) = field.touched || submitted

    val nameError: FieldError? get() = AuthValidator.required(name.value).visibleIf(show(name))
    val lastNamePError: FieldError? get() = AuthValidator.required(lastNameP.value).visibleIf(show(lastNameP))
    val birthDateError: FieldError? get() = AuthValidator.birthDate(birthDate, today).visibleIf(birthDate != null || submitted)
    val phoneError: FieldError? get() = AuthValidator.phone(phone.value).visibleIf(show(phone))

    val isValid
        get() = AuthValidator.required(name.value) == null &&
            AuthValidator.required(lastNameP.value) == null &&
            AuthValidator.birthDate(birthDate, today) == null &&
            AuthValidator.phone(phone.value) == null
}

/** Editar nombre, apellidos, fecha de nacimiento y teléfono (personal_data). El correo no se cambia aquí. */
@HiltViewModel
class PersonalDataViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalDataUiState())
    val uiState: StateFlow<PersonalDataUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val data = accountRepository.personalData.first()
            val email = sessionRepository.session.first()?.email.orEmpty()
            _uiState.update {
                it.copy(
                    isLoaded = true,
                    email = email,
                    name = FormField(data.name),
                    lastNameP = FormField(data.lastNameP),
                    lastNameM = FormField(data.lastNameM),
                    birthDate = data.birthDate,
                    phone = FormField(data.phone),
                )
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = it.name.onChange(value), error = null) }
    fun onNameBlur() = _uiState.update { it.copy(name = it.name.onBlur()) }
    fun onLastNamePChange(value: String) = _uiState.update { it.copy(lastNameP = it.lastNameP.onChange(value), error = null) }
    fun onLastNamePBlur() = _uiState.update { it.copy(lastNameP = it.lastNameP.onBlur()) }
    fun onLastNameMChange(value: String) = _uiState.update { it.copy(lastNameM = it.lastNameM.onChange(value), error = null) }
    fun onBirthDateSelected(date: LocalDate) = _uiState.update { it.copy(birthDate = date, error = null) }
    fun onPhoneChange(value: String) = _uiState.update {
        it.copy(phone = it.phone.onChange(value.filter { c -> c in '0'..'9' }.take(AuthValidator.PHONE_LENGTH)), error = null)
    }
    fun onPhoneBlur() = _uiState.update { it.copy(phone = it.phone.onBlur()) }

    fun save() {
        val state = _uiState.updateAndGet { it.copy(submitted = true) }
        if (!state.isValid || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val data = PersonalData(
                name = state.name.value.trim(),
                lastNameP = state.lastNameP.value.trim(),
                lastNameM = state.lastNameM.value.trim(),
                birthDate = state.birthDate,
                phone = state.phone.value,
            )
            when (val result = accountRepository.updatePersonalData(data)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSaving = false, isSaved = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }
}
