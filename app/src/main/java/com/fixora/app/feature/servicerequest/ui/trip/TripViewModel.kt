package com.fixora.app.feature.servicerequest.ui.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.payment.data.PaymentRepository
import com.fixora.app.feature.payment.ui.AdjustmentDraft
import com.fixora.app.feature.servicerequest.data.TripRepository
import com.fixora.app.feature.servicerequest.data.TripRoute
import com.fixora.app.feature.servicerequest.data.TripStep
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import com.fixora.app.feature.tracking.service.TripTrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TripUiState(
    val isLoading: Boolean = true,
    val request: ServiceRequest? = null,
    val loadError: ApiError? = null,
    /** Dónde está el técnico: la que recibió la API en camino o, si no, la del celular. */
    val technician: Coordinates? = null,
    val route: TripRoute? = null,
    val isAdvancing: Boolean = false,
    val actionError: ApiError? = null,
    /** No se pudo leer la ubicación del celular para avanzar (GPS apagado). */
    val noPosition: Boolean = false,
    /** Hace falta el permiso de ubicación para salir o para compartirla en camino. */
    val needsLocation: Boolean = false,
    /** Ya se pidió el permiso y no se dio: solo queda activarlo en los ajustes. */
    val locationDenied: Boolean = false,
    /** Paso que espera el permiso para continuar. */
    val pendingStep: TripStep? = null,
    /** Hoja para proponer un ajuste de precio (ANDROID-25). */
    val showAdjustmentSheet: Boolean = false,
    /** Hoja para decir que no llegó el pago del cliente. */
    val showRejectPaymentSheet: Boolean = false,
    /** Se manda un ajuste o la respuesta a un pago. */
    val isSubmitting: Boolean = false,
    /** Error de la hoja abierta o de confirmar el pago. */
    val paymentError: ApiError? = null,
) {
    /** No se puede finalizar mientras el cliente no responda un ajuste (la API responde 409). */
    val waitsAdjustmentAnswer: Boolean
        get() = request?.status == RequestStatus.Arrived && request.pendingAdjustment != null

    /** Ya en el domicilio puede proponer un ajuste, uno a la vez. */
    val canProposeAdjustment: Boolean
        get() = request?.status == RequestStatus.Arrived && request.pendingAdjustment == null

    /** El siguiente paso según el estado; null cuando ya terminó o no le toca al técnico. */
    val nextStep: TripStep?
        get() = when (request?.status) {
            RequestStatus.Accepted, RequestStatus.Scheduled -> TripStep.Start
            RequestStatus.OnTheWay -> TripStep.Arrive
            RequestStatus.Arrived -> TripStep.Finish
            else -> null
        }
}

/**
 * Traslado del técnico al domicilio (ANDROID-20): mapa con la ruta y lo que falta para llegar, y
 * el botón que avanza la solicitud: Iniciar traslado, Llegué y Finalizar. Al salir empieza a
 * compartir su ubicación con el cliente (ANDROID-19) y al llegar deja de hacerlo.
 */
@HiltViewModel
class TripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TripRepository,
    private val location: LocationSource,
    private val trip: TripTrackingController,
    private val payments: PaymentRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    private var follower: Job? = null
    private var watcher: Job? = null

    /** Llave del ajuste que se está proponiendo: la misma en cada reintento para no duplicarlo. */
    private var adjustmentKey: String? = null

    init {
        load(showLoading = true)
    }

    fun retry() = load(showLoading = true)

    /** Toca el botón principal. Sin permiso de ubicación primero lo pide y luego continúa. */
    fun advance() {
        val state = _uiState.value
        val step = state.nextStep ?: return
        if (state.isAdvancing) return
        if (step == TripStep.Finish && state.waitsAdjustmentAnswer) return
        if (!location.hasPermission()) {
            _uiState.update { it.copy(needsLocation = true, pendingStep = step) }
            return
        }
        _uiState.update { it.copy(isAdvancing = true, actionError = null, noPosition = false, pendingStep = null) }
        viewModelScope.launch {
            val here = location.current()
            if (here == null) {
                _uiState.update { it.copy(isAdvancing = false, noPosition = true) }
                return@launch
            }
            when (val result = repository.advance(requestId, step, here)) {
                is ApiResult.Success -> {
                    // Llegó: el cliente ya no necesita verlo avanzar.
                    if (step != TripStep.Start) trip.stop()
                    loadNow(showLoading = false)
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(actionError = result.error) }
                    // 409: alguien más la movió (el cliente canceló); se muestra cómo quedó.
                    loadNow(showLoading = false)
                }
            }
            _uiState.update { it.copy(isAdvancing = false) }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            _uiState.update { it.copy(locationDenied = true) }
            return
        }
        _uiState.update { it.copy(needsLocation = false, locationDenied = false) }
        val pending = _uiState.value.pendingStep
        if (pending != null) advance() else shareTripLocation()
    }

    /** Al volver a la pantalla (por ejemplo desde los ajustes) se revisa otra vez el permiso. */
    fun onResume() {
        if (!_uiState.value.needsLocation || !location.hasPermission()) return
        onLocationPermissionResult(true)
    }

    private fun load(showLoading: Boolean) {
        viewModelScope.launch { loadNow(showLoading) }
    }

    private suspend fun loadNow(showLoading: Boolean) {
        if (showLoading) _uiState.update { it.copy(isLoading = true, loadError = null) }
        when (val result = repository.request(requestId)) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(isLoading = false, loadError = null, request = result.data) }
                shareTripLocation()
                if (result.data.status == RequestStatus.OnTheWay) follow() else stopFollowing()
                if (needsWatching(result.data)) watch() else stopWatching()
                if (_uiState.value.technician == null && location.hasPermission()) {
                    location.current()?.let { here -> _uiState.update { it.copy(technician = it.technician ?: here) } }
                }
            }
            is ApiResult.Failure -> if (showLoading || _uiState.value.request == null) {
                _uiState.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    /** En camino: el cliente debe verlo avanzar. Si Android detuvo el envío, abrirla lo reanuda. */
    private fun shareTripLocation() {
        if (_uiState.value.request?.status != RequestStatus.OnTheWay) return
        if (location.hasPermission()) {
            trip.start(requestId)
            _uiState.update { if (it.pendingStep == null) it.copy(needsLocation = false) else it }
        } else {
            _uiState.update { it.copy(needsLocation = true) }
        }
    }

    /** Mientras va en camino trae la ruta y su posición cada [FOLLOW_INTERVAL_MS]. */
    private fun follow() {
        if (follower?.isActive == true) return
        follower = viewModelScope.launch {
            while (isActive) {
                val result = repository.tracking(requestId)
                if (result is ApiResult.Success) {
                    _uiState.update {
                        it.copy(
                            technician = result.data.technician ?: it.technician,
                            route = result.data.route ?: it.route,
                        )
                    }
                }
                delay(FOLLOW_INTERVAL_MS)
            }
        }
    }

    fun openAdjustment() {
        if (!_uiState.value.canProposeAdjustment) return
        if (adjustmentKey == null) adjustmentKey = UUID.randomUUID().toString()
        _uiState.update { it.copy(showAdjustmentSheet = true, paymentError = null) }
    }

    fun dismissAdjustment() = _uiState.update { it.copy(showAdjustmentSheet = false, paymentError = null) }

    /** Lo manda al cliente; queda pendiente hasta que lo apruebe o lo rechace. */
    fun proposeAdjustment(draft: AdjustmentDraft) {
        if (_uiState.value.isSubmitting) return
        val key = adjustmentKey ?: UUID.randomUUID().toString().also { adjustmentKey = it }
        _uiState.update { it.copy(isSubmitting = true, paymentError = null) }
        viewModelScope.launch {
            when (val result = payments.proposeAdjustment(requestId, draft.amountCents, draft.reason, draft.description, key)) {
                is ApiResult.Success -> {
                    adjustmentKey = null
                    _uiState.update { it.copy(isSubmitting = false, showAdjustmentSheet = false) }
                    loadNow(showLoading = false)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSubmitting = false, paymentError = result.error) }
            }
        }
    }

    /** Recibió el pago que registró el cliente. */
    fun confirmPayment() = respondPayment(received = true, reason = null)

    fun openRejectPayment() = _uiState.update { it.copy(showRejectPaymentSheet = true, paymentError = null) }

    fun dismissRejectPayment() = _uiState.update { it.copy(showRejectPaymentSheet = false, paymentError = null) }

    /** No le llegó: el cliente lo ve con el motivo y puede registrarlo otra vez. */
    fun rejectPayment(reason: String?) = respondPayment(received = false, reason = reason)

    private fun respondPayment(received: Boolean, reason: String?) {
        val state = _uiState.value
        val payment = state.request?.pendingPayment ?: return
        if (state.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, paymentError = null) }
        viewModelScope.launch {
            val result = payments.respondPayment(requestId, payment.id, received, reason)
            if (result is ApiResult.Failure) {
                _uiState.update { it.copy(isSubmitting = false, paymentError = result.error) }
            } else {
                _uiState.update { it.copy(isSubmitting = false, showRejectPaymentSheet = false) }
            }
            // 409: el pago ya se había respondido (por ejemplo, el dueño desde otro celular).
            loadNow(showLoading = false)
        }
    }

    /**
     * En el domicilio espera que el cliente responda los ajustes, y al terminar, que registre su
     * pago: mientras tanto se consulta la solicitud para ver la respuesta sin recargar.
     */
    private fun needsWatching(request: ServiceRequest): Boolean = when (request.status) {
        RequestStatus.Arrived -> true
        RequestStatus.Completed -> request.balanceCents > 0
        else -> false
    }

    private fun watch() {
        if (watcher?.isActive == true) return
        watcher = viewModelScope.launch {
            while (isActive) {
                delay(WATCH_INTERVAL_MS)
                loadNow(showLoading = false)
            }
        }
    }

    private fun stopWatching() {
        watcher?.cancel()
        watcher = null
    }

    private fun stopFollowing() {
        follower?.cancel()
        follower = null
        _uiState.update { it.copy(route = null) }
    }

    companion object {
        /** La API recalcula la ruta cada minuto o si se desvía; con 10 s el tiempo baja fluido. */
        const val FOLLOW_INTERVAL_MS = 10_000L

        /** Cada cuánto se revisa si el cliente ya respondió el ajuste o registró el pago. */
        const val WATCH_INTERVAL_MS = 5_000L
    }
}
