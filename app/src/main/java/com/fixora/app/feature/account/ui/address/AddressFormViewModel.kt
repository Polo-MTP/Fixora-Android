package com.fixora.app.feature.account.ui.address

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.AccountValidator
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.auth.domain.AuthValidator
import com.fixora.app.feature.auth.domain.FieldError
import com.fixora.app.feature.auth.ui.FormField
import com.fixora.app.feature.auth.ui.visibleIf
import com.fixora.app.feature.availability.location.AddressGeocoder
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.GeocodedAddress
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.map.domain.MapRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    /** El punto marcado en el mapa: es a donde llega el técnico (ANDROID-41). */
    val location: Coordinates? = null,
    /** El mapa para marcar la ubicación, mientras está abierto. */
    val picker: LocationPicker? = null,
    /** Buscando la dirección escrita del punto que se acaba de confirmar. */
    val isLookingUp: Boolean = false,
    /**
     * Lo que el mapa escribió en cada campo. Si el usuario no lo cambió, al mover el punto se
     * vuelve a llenar; lo que corrigió a mano ya no se toca.
     */
    val autoFilled: Map<AddressField, String> = emptyMap(),
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

    /** Al intentar guardar sin haber marcado la ubicación. */
    val locationMissing get() = submitted && location == null

    /** Se llenaron campos con el mapa: hay que pedir que los revise, porque no siempre acierta. */
    val showReviewNotice get() = !isLookingUp && autoFilled.isNotEmpty()

    val isValid get() = location != null && AddressField.entries.all { field -> copy(submitted = true).errorOf(field) == null }
    val isBusy get() = isSaving || isDeleting
}

/** Agregar o editar una dirección; la ruta trae el id cuando se edita. */
@HiltViewModel
class AddressFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val locationSource: LocationSource,
    private val geocoder: AddressGeocoder,
) : ViewModel() {

    private var lookup: Job? = null

    // Argumento de AddressFormRoute; se lee directo para poder probarlo sin Android.
    private val addressId: String? = savedStateHandle["addressId"]

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
                        location = address.location,
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

    fun hasLocationPermission(): Boolean = locationSource.hasPermission()

    /**
     * Abre el mapa en el punto ya marcado. Si no hay, empieza en la ciudad y se mueve a donde está
     * el celular en cuanto se sepa (casi siempre se registra la dirección estando en ella).
     */
    fun openPicker() {
        val location = _uiState.value.location
        _uiState.update {
            it.copy(
                picker = LocationPicker(
                    start = location ?: MapRules.DEFAULT_CENTER,
                    zoom = if (location != null) LocationPicker.PRECISE_ZOOM else LocationPicker.CITY_ZOOM,
                ),
            )
        }
        if (location == null) onMyLocation()
    }

    /** Lleva el mapa a donde está el celular; sin permiso o con el GPS apagado no hace nada. */
    fun onMyLocation() {
        viewModelScope.launch {
            val here = locationSource.current() ?: return@launch
            _uiState.update { state ->
                val picker = state.picker ?: return@update state
                state.copy(picker = picker.copy(recenter = here, recenterId = picker.recenterId + 1))
            }
        }
    }

    /** Guarda el punto y llena con su dirección los campos que siguen vacíos (o que llenó el mapa antes). */
    fun onLocationConfirmed(location: Coordinates) {
        _uiState.update { it.copy(location = location, picker = null, error = null, isLookingUp = true) }
        lookup?.cancel()
        lookup = viewModelScope.launch {
            val found = geocoder.lookup(location)
            _uiState.update { state -> found?.let { state.fillWith(it) } ?: state.copy(isLookingUp = false) }
        }
    }

    private fun AddressFormUiState.fillWith(found: GeocodedAddress): AddressFormUiState {
        val suggested = mapOf(
            AddressField.Street to found.street,
            AddressField.NumExt to found.number,
            AddressField.Colony to found.colony,
            AddressField.PostalCode to found.postalCode.filter { it in '0'..'9' }.take(AccountValidator.POSTAL_CODE_LENGTH),
            AddressField.Municipality to found.municipality,
            AddressField.State to found.state,
        ).mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        val filled = suggested.filter { (field, value) ->
            val current = this[field].value.trim()
            (current.isEmpty() || current == autoFilled[field]) && current != value
        }
        // Lo que el mapa llenó antes y el usuario no tocó sigue siendo del mapa.
        val stillAuto = autoFilled.filter { (field, value) -> this[field].value.trim() == value }
        return copy(
            fields = fields + filled.mapValues { (field, value) -> this[field].onChange(value) },
            autoFilled = stillAuto + filled,
            isLookingUp = false,
        )
    }

    fun closePicker() = _uiState.update { it.copy(picker = null) }

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
            location = state.location,
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
