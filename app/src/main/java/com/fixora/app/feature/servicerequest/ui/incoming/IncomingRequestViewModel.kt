package com.fixora.app.feature.servicerequest.ui.incoming

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
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

enum class IncomingOutcome { Accepted, Rejected }

data class IncomingRequestUiState(
    val isLoading: Boolean = true,
    val request: ServiceRequest? = null,
    val loadError: ApiError? = null,
    /** Distancia en línea recta desde donde está el técnico; null sin ubicación. */
    val distanceKm: Double? = null,
    /** Segundos para responder; null si la solicitud no vence (agendada) o no se sabe. */
    val secondsLeft: Long? = null,
    val isAccepting: Boolean = false,
    val isRejecting: Boolean = false,
    val actionError: ApiError? = null,
    val outcome: IncomingOutcome? = null,
) {
    /** Ya no se puede responder: la aceptó otro, el cliente la canceló o venció. */
    val isClosed: Boolean
        get() = request != null && (request.status != RequestStatus.Pending || secondsLeft == 0L)

    /**
     * Suena desde que se abre (aunque la solicitud aún no cargue o falle la red) hasta que se
     * responde o ya no se puede responder.
     */
    val isRinging: Boolean get() = !isClosed && outcome == null

    val isBusy: Boolean get() = isAccepting || isRejecting
}

/**
 * Pantalla de alarma de solicitud entrante (ANDROID-13, Figma «Fixora Técnico - Alerta de
 * Emergencia»): cuenta regresiva, distancia y aceptar o rechazar.
 */
@HiltViewModel
class IncomingRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: IncomingRequestRepository,
    private val location: LocationSource,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    /** Reloj de la cuenta regresiva; las pruebas lo cambian. */
    internal var now: () -> Instant = Instant::now

    private val _uiState = MutableStateFlow(IncomingRequestUiState())
    val uiState: StateFlow<IncomingRequestUiState> = _uiState.asStateFlow()

    private var ticker: Job? = null

    init {
        load()
    }

    fun retry() = load()

    fun accept() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isAccepting = true, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.accept(requestId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isAccepting = false, outcome = IncomingOutcome.Accepted) }
                is ApiResult.Failure -> onActionFailed(result.error) { it.copy(isAccepting = false) }
            }
        }
    }

    fun reject(reason: String) {
        if (_uiState.value.isBusy || reason.isBlank()) return
        _uiState.update { it.copy(isRejecting = true, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.reject(requestId, reason)) {
                is ApiResult.Success -> _uiState.update { it.copy(isRejecting = false, outcome = IncomingOutcome.Rejected) }
                is ApiResult.Failure -> onActionFailed(result.error) { it.copy(isRejecting = false) }
            }
        }
    }

    /** Si ya no se podía responder (409), se recarga para mostrar en qué quedó. */
    private fun onActionFailed(error: ApiError, reset: (IncomingRequestUiState) -> IncomingRequestUiState) {
        _uiState.update { reset(it).copy(actionError = error) }
        if (error is ApiError.Http && error.status == 409) load(showLoading = false)
    }

    private fun load(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(isLoading = true, loadError = null) }
            when (val result = repository.request(requestId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, request = result.data, secondsLeft = secondsLeft(result.data)) }
                    startCountdown()
                    measureDistance(result.data)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    private fun secondsLeft(request: ServiceRequest): Long? {
        if (request.scheduledAt != null) return null
        val expires = request.expiresAt ?: return null
        return Duration.between(now(), expires).seconds.coerceAtLeast(0)
    }

    private fun startCountdown() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val left = _uiState.value.secondsLeft ?: return@launch
                if (left <= 0) return@launch
                delay(1_000)
                val request = _uiState.value.request ?: return@launch
                _uiState.update { it.copy(secondsLeft = secondsLeft(request)) }
            }
        }
    }

    private suspend fun measureDistance(request: ServiceRequest) {
        val target = request.position ?: return
        if (!location.hasPermission()) return
        val here = location.current() ?: return
        _uiState.update { it.copy(distanceKm = MapRules.distanceKm(here, target)) }
    }

    companion object {
        /** La API da 5 minutos para responder; el aro de la cuenta regresiva parte de aquí. */
        const val RESPONSE_WINDOW_SECONDS = 5 * 60L

        /** Velocidad media en ciudad para estimar el traslado, igual que el respaldo de la API. */
        private const val CITY_SPEED_KMH = 25.0

        /** Minutos aproximados de traslado; al menos 1. */
        fun etaMinutes(distanceKm: Double): Int = ((distanceKm / CITY_SPEED_KMH) * 60).toInt().coerceAtLeast(1)
    }
}
