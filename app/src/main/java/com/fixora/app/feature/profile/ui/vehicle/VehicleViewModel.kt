package com.fixora.app.feature.profile.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.auth.domain.AuthValidator
import com.fixora.app.feature.auth.domain.FieldError
import com.fixora.app.feature.auth.ui.FormField
import com.fixora.app.feature.auth.ui.visibleIf
import com.fixora.app.feature.profile.data.ProfileState
import com.fixora.app.feature.profile.data.TechnicianProfileRepository
import com.fixora.app.feature.profile.domain.TechnicianProfileRules
import com.fixora.app.feature.profile.domain.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Campos del vehículo (tabla employee_vehicles). Todos son obligatorios. */
enum class VehicleField { Plate, Brand, Model, Color }

data class VehicleUiState(
    val isLoaded: Boolean = false,
    val loadError: ApiError? = null,
    val notTechnician: Boolean = false,
    /** Ya tiene un vehículo guardado: se puede quitar. */
    val hasVehicle: Boolean = false,
    /** Hay un servicio en curso: el cliente identifica al técnico por el vehículo (API-55). */
    val locked: Boolean = false,
    val fields: Map<VehicleField, FormField> = VehicleField.entries.associateWith { FormField() },
    val submitted: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: ApiError? = null,
    val isDone: Boolean = false,
) {
    operator fun get(field: VehicleField): FormField = fields.getValue(field)

    fun errorOf(field: VehicleField): FieldError? {
        val value = get(field).value
        val error = if (field == VehicleField.Plate) TechnicianProfileRules.plate(value) else AuthValidator.required(value)
        return error.visibleIf(get(field).touched || submitted)
    }

    val isValid get() = VehicleField.entries.all { copy(submitted = true).errorOf(it) == null }
    val isBusy get() = isSaving || isDeleting
}

/** Datos del vehículo con el que el técnico llega al servicio (ANDROID-7). */
@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: TechnicianProfileRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.activeService.collect { locked -> _uiState.update { it.copy(locked = locked) } }
        }
        viewModelScope.launch { accountRepository.refreshPersonalData() }
        viewModelScope.launch {
            repository.state.collect { state ->
                _uiState.update { ui ->
                    when (state) {
                        is ProfileState.Loaded -> if (ui.isLoaded) ui else ui.loadedWith(state.profile.vehicle)
                        is ProfileState.Failed -> ui.copy(loadError = state.error)
                        ProfileState.NotTechnician -> ui.copy(notTechnician = true)
                        ProfileState.Loading -> ui
                    }
                }
            }
        }
        retryLoad()
    }

    fun retryLoad() {
        viewModelScope.launch { repository.refresh() }
    }

    fun onChange(field: VehicleField, value: String) {
        if (_uiState.value.locked) return
        val cleaned = if (field == VehicleField.Plate) value.uppercase().take(TechnicianProfileRules.PLATE_MAX_LENGTH) else value
        _uiState.update { it.copy(fields = it.fields + (field to it[field].onChange(cleaned)), error = null) }
    }

    fun onBlur(field: VehicleField) = _uiState.update { it.copy(fields = it.fields + (field to it[field].onBlur())) }

    fun save() {
        val state = _uiState.updateAndGet { it.copy(submitted = true) }
        if (!state.isLoaded || !state.isValid || state.isBusy || state.locked) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        val vehicle = Vehicle(
            plate = state[VehicleField.Plate].value,
            brand = state[VehicleField.Brand].value,
            model = state[VehicleField.Model].value,
            color = state[VehicleField.Color].value,
        )
        viewModelScope.launch { finish(repository.saveVehicle(vehicle)) { copy(isSaving = false) } }
    }

    fun delete() {
        if (!_uiState.value.hasVehicle || _uiState.value.isBusy || _uiState.value.locked) return
        _uiState.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch { finish(repository.deleteVehicle()) { copy(isDeleting = false) } }
    }

    private fun finish(result: ApiResult<Unit>, idle: VehicleUiState.() -> VehicleUiState) {
        _uiState.update {
            when (result) {
                is ApiResult.Success -> it.idle().copy(isDone = true)
                is ApiResult.Failure -> it.idle().copy(error = result.error)
            }
        }
    }
}

private fun VehicleUiState.loadedWith(vehicle: Vehicle?) = copy(
    isLoaded = true,
    loadError = null,
    hasVehicle = vehicle != null,
    fields = mapOf(
        VehicleField.Plate to vehicle?.plate.orEmpty(),
        VehicleField.Brand to vehicle?.brand.orEmpty(),
        VehicleField.Model to vehicle?.model.orEmpty(),
        VehicleField.Color to vehicle?.color.orEmpty(),
    ).mapValues { FormField(it.value) },
)
