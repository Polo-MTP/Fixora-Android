package com.fixora.app.feature.availability.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.data.AvailabilityRepository
import com.fixora.app.feature.availability.data.AvailabilityState
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.availability.service.OnlineServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Por qué no se pudo cambiar la disponibilidad. */
sealed interface AvailabilityProblem {
    /** La API lo rechazó (sucursal cerrada, sin internet...); trae su mensaje. */
    data class Api(val error: ApiError) : AvailabilityProblem

    /** No dio permiso de ubicación. */
    data object LocationDenied : AvailabilityProblem

    /** La ubicación está apagada o no se pudo obtener. */
    data object LocationUnavailable : AvailabilityProblem
}

data class AvailabilityUiState(
    val availability: AvailabilityState = AvailabilityState.Loading,
    val isChanging: Boolean = false,
    val problem: AvailabilityProblem? = null,
) {
    val isOnline get() = (availability as? AvailabilityState.Loaded)?.online == true
    val isVisible get() = availability != AvailabilityState.NotTechnician
}

/**
 * Interruptor En línea / Fuera de línea del técnico (ANDROID-9). Al ponerse en línea manda la
 * ubicación actual a la API e inicia OnlineService, que la sigue reportando; al salir lo detiene.
 */
@HiltViewModel
class AvailabilityViewModel @Inject constructor(
    private val repository: AvailabilityRepository,
    private val location: LocationSource,
    private val onlineService: OnlineServiceController,
) : ViewModel() {

    private data class Local(val isChanging: Boolean = false, val problem: AvailabilityProblem? = null)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<AvailabilityUiState> = combine(repository.state, local) { availability, local ->
        AvailabilityUiState(availability, local.isChanging, local.problem)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AvailabilityUiState())

    private val _requestPermission = Channel<Unit>(Channel.BUFFERED)

    /** La pantalla pide el permiso de ubicación y contesta con [onPermissionResult]. */
    val requestPermission: Flow<Unit> = _requestPermission.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refresh()
            // Si la app se cerró estando en línea, se vuelve a reportar la posición.
            if (repository.isOnline && location.hasPermission()) onlineService.start()
        }
    }

    fun onToggle(online: Boolean) {
        if (local.value.isChanging) return
        local.update { it.copy(problem = null) }
        when {
            !online -> goOffline()
            location.hasPermission() -> goOnline()
            else -> _requestPermission.trySend(Unit)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) goOnline() else local.update { it.copy(problem = AvailabilityProblem.LocationDenied) }
    }

    private fun goOnline() = change {
        val position = location.current() ?: return@change AvailabilityProblem.LocationUnavailable
        when (val result = repository.goOnline(position)) {
            is ApiResult.Success -> {
                onlineService.start()
                null
            }
            is ApiResult.Failure -> AvailabilityProblem.Api(result.error)
        }
    }

    private fun goOffline() = change {
        // Primero se deja de reportar: aunque la API no conteste, la posición vence sola en 5 min.
        onlineService.stop()
        (repository.goOffline() as? ApiResult.Failure)?.let { AvailabilityProblem.Api(it.error) }
    }

    private fun change(action: suspend () -> AvailabilityProblem?) {
        local.update { it.copy(isChanging = true) }
        viewModelScope.launch {
            val problem = action()
            local.update { it.copy(isChanging = false, problem = problem) }
        }
    }
}
