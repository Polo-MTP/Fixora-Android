package com.fixora.app.feature.servicerequest.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.map.domain.MapRules
import com.fixora.app.feature.servicerequest.data.IncomingRequestRepository
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class ServiceRequestUiState(
    val isLoading: Boolean = true,
    val request: ServiceRequest? = null,
    val error: ApiError? = null,
    /** Distancia en línea recta desde el técnico; null sin ubicación. */
    val distanceKm: Double? = null,
    /** Segundos para responder; null si no vence (agendada) o ya no está pendiente. */
    val secondsLeft: Long? = null,
    val isAccepting: Boolean = false,
    val isRejecting: Boolean = false,
    val actionError: ApiError? = null,
    /** La rechazó desde aquí: la pantalla se cierra. */
    val rejected: Boolean = false,
) {
    /** Todavía se puede aceptar o rechazar. */
    val canRespond: Boolean
        get() = request?.status == RequestStatus.Pending && secondsLeft != 0L && !rejected

    val isBusy: Boolean get() = isAccepting || isRejecting
}

/**
 * Detalle de una solicitud para el técnico (ANDROID-13 y ANDROID-18): qué se pide, dónde, a qué
 * distancia y, mientras sigue pendiente, la cuenta regresiva con aceptar o rechazar.
 */
@HiltViewModel
class ServiceRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: IncomingRequestRepository,
    private val location: LocationSource,
) : ViewModel() {

    // El argumento de ServiceRequestRoute se guarda con el nombre de la propiedad.
    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    /** Reloj de la cuenta regresiva; las pruebas lo cambian. */
    internal var now: () -> Instant = Instant::now

    private val _uiState = MutableStateFlow(ServiceRequestUiState())
    val uiState: StateFlow<ServiceRequestUiState> = _uiState.asStateFlow()

    private var ticker: Job? = null

    init {
        load(showLoading = true)
    }

    fun retry() = load(showLoading = true)

    fun accept() {
        if (!_uiState.value.canRespond || _uiState.value.isBusy) return
        _uiState.update { it.copy(isAccepting = true, actionError = null) }
        viewModelScope.launch {
            val result = repository.accept(requestId)
            _uiState.update { it.copy(isAccepting = false, actionError = (result as? ApiResult.Failure)?.error) }
            // Aceptada (o ya tomada por otro): se recarga para mostrar cómo quedó.
            load(showLoading = false)
        }
    }

    fun reject(reason: String) {
        if (!_uiState.value.canRespond || _uiState.value.isBusy || reason.isBlank()) return
        _uiState.update { it.copy(isRejecting = true, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.reject(requestId, reason)) {
                is ApiResult.Success -> _uiState.update { it.copy(isRejecting = false, rejected = true) }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(isRejecting = false, actionError = result.error) }
                    load(showLoading = false)
                }
            }
        }
    }

    private fun load(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.request(requestId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, error = null, request = result.data, secondsLeft = secondsLeft(result.data)) }
                    startCountdown()
                    measureDistance(result.data)
                }
                is ApiResult.Failure -> if (showLoading || _uiState.value.request == null) {
                    _uiState.update { it.copy(isLoading = false, error = result.error) }
                }
            }
        }
    }

    private fun secondsLeft(request: ServiceRequest): Long? {
        if (request.status != RequestStatus.Pending || request.scheduledAt != null) return null
        val expires = request.expiresAt ?: return null
        return Duration.between(now(), expires).seconds.coerceAtLeast(0)
    }

    private fun startCountdown() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                val left = _uiState.value.secondsLeft ?: return@launch
                if (left <= 0) return@launch
                delay(1_000)
                val request = _uiState.value.request ?: return@launch
                _uiState.update { it.copy(secondsLeft = secondsLeft(request)) }
            }
        }
    }

    private suspend fun measureDistance(request: ServiceRequest) {
        if (_uiState.value.distanceKm != null) return
        val target = request.position ?: return
        if (!location.hasPermission()) return
        val here = location.current() ?: return
        _uiState.update { it.copy(distanceKm = MapRules.distanceKm(here, target)) }
    }
}
