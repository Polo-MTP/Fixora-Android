package com.fixora.app.feature.availability.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.availability.location.Coordinates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Lo que la app sabe de la disponibilidad del técnico con sesión. */
sealed interface AvailabilityState {
    data object Loading : AvailabilityState

    /** La cuenta no atiende servicios (gerente sin perfil de técnico, o sin permiso para atender). */
    data object NotTechnician : AvailabilityState

    /** [branchOpen] es false si la sucursal está cerrada o no acepta solicitudes ahora. */
    data class Loaded(val online: Boolean, val branchOpen: Boolean) : AvailabilityState

    data class Failed(val error: ApiError) : AvailabilityState
}

/**
 * Disponibilidad del técnico (ANDROID-9). Es una sola instancia para la pantalla y para
 * OnlineService: si el servicio se detiene por un error, la pantalla lo ve en [state].
 */
class AvailabilityRepository(
    private val api: AvailabilityApi,
    private val json: Json,
) {
    private val _state = MutableStateFlow<AvailabilityState>(AvailabilityState.Loading)
    val state: StateFlow<AvailabilityState> = _state.asStateFlow()

    /** Consulta a la API si está en línea. */
    suspend fun refresh() {
        _state.value = when (val result = safeApiCall(json) { api.get().data }) {
            is ApiResult.Success -> result.data.toState()
            is ApiResult.Failure -> if (result.error.isNotTechnician()) AvailabilityState.NotTechnician
            else AvailabilityState.Failed(result.error)
        }
    }

    /** Se pone en línea en [position]. También sirve para volver a mandar la posición. */
    suspend fun goOnline(position: Coordinates): ApiResult<Unit> =
        update(AvailabilityRequest(online = true, latitude = position.latitude, longitude = position.longitude))

    suspend fun goOffline(): ApiResult<Unit> = update(AvailabilityRequest(online = false))

    /** true si la API lo tiene en línea según lo último que se supo. */
    val isOnline: Boolean get() = (_state.value as? AvailabilityState.Loaded)?.online == true

    /** Al cerrar sesión: la siguiente cuenta empieza sin estado. */
    fun clear() {
        _state.value = AvailabilityState.Loading
    }

    private suspend fun update(request: AvailabilityRequest): ApiResult<Unit> {
        val result = safeApiCall(json) { api.update(request).data }
        return when (result) {
            is ApiResult.Success -> {
                _state.value = result.data.toState()
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> {
                // Si la API rechazó ponerse en línea (sucursal cerrada, perfil inactivo), queda fuera.
                if (result.error.stopsOnline()) {
                    (_state.value as? AvailabilityState.Loaded)?.let { _state.value = it.copy(online = false) }
                }
                result
            }
        }
    }

    private fun AvailabilityDto.toState() = AvailabilityState.Loaded(
        online = online,
        branchOpen = branch?.let { it.isOpen && it.acceptingRequest } ?: true,
    )

    companion object {
        const val OUTSIDE_BRANCH_SCHEDULE = "OUTSIDE_BRANCH_SCHEDULE"
        const val BRANCH_NOT_ACCEPTING_REQUESTS = "BRANCH_NOT_ACCEPTING_REQUESTS"

        private fun ApiError.isNotTechnician() = this is ApiError.Http && status in setOf(403, 404)

        /** Errores con los que ya no tiene caso seguir reportando la posición. */
        internal fun ApiError.stopsOnline() = this is ApiError.Http && status in setOf(401, 403, 404, 409)
    }
}
