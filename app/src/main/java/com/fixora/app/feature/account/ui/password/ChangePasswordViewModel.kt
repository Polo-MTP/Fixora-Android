package com.fixora.app.feature.account.ui.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.AccountValidator
import com.fixora.app.feature.auth.domain.AuthValidator
import com.fixora.app.feature.auth.domain.FieldError
import com.fixora.app.feature.auth.domain.PasswordRules
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

data class ChangePasswordUiState(
    val current: FormField = FormField(),
    val new: FormField = FormField(),
    val confirm: FormField = FormField(),
    val submitted: Boolean = false,
    val isSaving: Boolean = false,
    val error: ApiError? = null,
    val isSaved: Boolean = false,
) {
    private fun show(field: FormField) = field.touched || submitted

    private val newPasswordError
        get() = AuthValidator.newPassword(new.value) ?: AccountValidator.passwordChanged(current.value, new.value)

    val currentError: FieldError? get() = AuthValidator.required(current.value).visibleIf(show(current))
    val newError: FieldError? get() = newPasswordError.visibleIf(show(new))
    val confirmError: FieldError? get() = AuthValidator.confirmPassword(new.value, confirm.value).visibleIf(show(confirm))
    val newRules: PasswordRules get() = AuthValidator.passwordRules(new.value)

    val isValid
        get() = AuthValidator.required(current.value) == null &&
            newPasswordError == null &&
            AuthValidator.confirmPassword(new.value, confirm.value) == null
}

/** Cambiar la contraseña pidiendo la actual, con los mismos requisitos que el registro. */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onCurrentChange(value: String) = _uiState.update { it.copy(current = it.current.onChange(value), error = null) }
    fun onCurrentBlur() = _uiState.update { it.copy(current = it.current.onBlur()) }
    fun onNewChange(value: String) = _uiState.update { it.copy(new = it.new.onChange(value), error = null) }
    fun onNewBlur() = _uiState.update { it.copy(new = it.new.onBlur()) }
    fun onConfirmChange(value: String) = _uiState.update { it.copy(confirm = it.confirm.onChange(value), error = null) }
    fun onConfirmBlur() = _uiState.update { it.copy(confirm = it.confirm.onBlur()) }

    fun save() {
        val state = _uiState.updateAndGet { it.copy(submitted = true) }
        if (!state.isValid || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = accountRepository.changePassword(state.current.value, state.new.value)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSaving = false, isSaved = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }
}
