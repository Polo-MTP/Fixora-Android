package com.fixora.app.feature.account.ui.address

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.AccountValidator
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.navigation.AddressFormRoute
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
import javax.inject.Inject

/** Campos del formulario de dirección (tabla addresses, más el alias de address_user). */
enum class AddressField(val required: Boolean) {
    Alias(true),
    Street(true),
    NumExt(true),
    NumInt(false),
    Colony(true),
    PostalCode(true),
    Municipality(true),
    State(true),
    Reference(false),
}

data class AddressFormUiState(
    val isLoaded: Boolean = false,
    val isEditing: Boolean = false,
    val fields: Map<AddressField, FormField> = AddressField.entries.associateWith { FormField() },
    val isMain: Boolean = false,
    /** La principal no se puede desmarcar: para cambiarla se marca otra. */
    val canChangeMain: Boolean = true,
    val submitted: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: ApiError? = null,
    val isDone: Boolean = false,
) {
    operator fun get(field: AddressField): FormField = fields.getValue(field)

    fun errorOf(field: AddressField): FieldError? {
        val value = get(field).value
        val error = when {
            field == AddressField.PostalCode -> AccountValidator.postalCode(value)
            field.required -> AuthValidator.required(value)
            else -> null
        }
        return error.visibleIf(get(field).touched || submitted)
    }

    val isValid get() = AddressField.entries.all { field -> copy(submitted = true).errorOf(field) == null }
    val isBusy get() = isSaving || isDeleting
}

/** Agregar o editar una dirección; la ruta trae el id cuando se edita. */
@HiltViewModel
class AddressFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val addressId = savedStateHandle.toRoute<AddressFormRoute>().addressId

    private val _uiState = MutableStateFlow(AddressFormUiState(isEditing = addressId != null))
    val uiState: StateFlow<AddressFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val addresses = accountRepository.addresses.first()
            val address = addresses.firstOrNull { it.id == addressId }
            _uiState.update { state ->
                if (address == null) {
                    // La primera dirección que se agrega siempre es la principal.
                    state.copy(isLoaded = true, isMain = addresses.isEmpty(), canChangeMain = addresses.isNotEmpty())
                } else {
                    state.copy(
                        isLoaded = true,
                        fields = mapOf(
                            AddressField.Alias to address.alias,
                            AddressField.Street to address.street,
                            AddressField.NumExt to address.numExt,
                            AddressField.NumInt to address.numInt,
                            AddressField.Colony to address.colony,
                            AddressField.PostalCode to address.postalCode,
                            AddressField.Municipality to address.municipality,
                            AddressField.State to address.state,
                            AddressField.Reference to address.reference,
                        ).mapValues { FormField(it.value) },
                        isMain = address.isMain,
                        canChangeMain = !address.isMain,
                    )
                }
            }
        }
    }

    fun onChange(field: AddressField, value: String) {
        val cleaned = when (field) {
            AddressField.PostalCode -> value.filter { it in '0'..'9' }.take(AccountValidator.POSTAL_CODE_LENGTH)
            else -> value
        }
        _uiState.update { it.copy(fields = it.fields + (field to it[field].onChange(cleaned)), error = null) }
    }

    fun onBlur(field: AddressField) = _uiState.update { it.copy(fields = it.fields + (field to it[field].onBlur())) }

    fun onMainChange(isMain: Boolean) = _uiState.update { if (it.canChangeMain) it.copy(isMain = isMain) else it }

    fun save() {
        val state = _uiState.updateAndGet { it.copy(submitted = true) }
        if (!state.isValid || state.isBusy) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        val value = { field: AddressField -> state[field].value.trim() }
        val address = Address(
            id = addressId,
            alias = value(AddressField.Alias),
            street = value(AddressField.Street),
            numExt = value(AddressField.NumExt),
            numInt = value(AddressField.NumInt),
            colony = value(AddressField.Colony),
            postalCode = value(AddressField.PostalCode),
            municipality = value(AddressField.Municipality),
            state = value(AddressField.State),
            reference = value(AddressField.Reference),
            isMain = state.isMain,
        )
        viewModelScope.launch { finish(accountRepository.saveAddress(address)) { copy(isSaving = false) } }
    }

    fun delete() {
        val id = addressId ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch { finish(accountRepository.deleteAddress(id)) { copy(isDeleting = false) } }
    }

    private fun finish(result: ApiResult<Unit>, idle: AddressFormUiState.() -> AddressFormUiState) {
        _uiState.update {
            when (result) {
                is ApiResult.Success -> it.idle().copy(isDone = true)
                is ApiResult.Failure -> it.idle().copy(error = result.error)
            }
        }
    }
}
