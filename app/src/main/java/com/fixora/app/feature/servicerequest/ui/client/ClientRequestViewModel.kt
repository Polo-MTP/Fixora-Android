package com.fixora.app.feature.servicerequest.ui.client

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.payment.data.PaymentRepository
import com.fixora.app.feature.payment.ui.PaymentDraft
import com.fixora.app.feature.servicerequest.data.ClientRequestRepository
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
import java.util.UUID
import javax.inject.Inject

/** En qué va la solicitud, como la ve el cliente. */
enum class ClientPhase { Waiting, Rejected, Expired, Canceled, Accepted, Completed }

data class ClientRequestUiState(
    val isLoading: Boolean = true,
    val request: ServiceRequest? = null,
    val loadError: ApiError? = null,
    val secondsLeft: Long? = null,
    val isCanceling: Boolean = false,
    val isResending: Boolean = false,
    val actionError: ApiError? = null,
    /** Solicitud nueva al reenviar; la pantalla la abre en lugar de esta. */
    val resentRequestId: String? = null,
    /** Ajuste que se está aprobando o rechazando (ANDROID-24). */
    val respondingAdjustmentId: String? = null,
    val showPaymentSheet: Boolean = false,
    val isRegisteringPayment: Boolean = false,
    /** Error de los ajustes o de la hoja de pago. */
    val paymentError: ApiError? = null,
) {
    /** Terminado y con saldo, sin otro pago esperando confirmación: le toca registrar su pago. */
    val canRegisterPayment: Boolean
        get() = request?.status == RequestStatus.Completed && request.balanceCents > 0 && request.pendingPayment == null

    val phase: ClientPhase?
        get() {
            val request = request ?: return null
            return when (request.status) {
                RequestStatus.Pending -> when {
                    request.wasRejected -> ClientPhase.Rejected
                    secondsLeft == 0L -> ClientPhase.Expired
                    else -> ClientPhase.Waiting
                }
                RequestStatus.Expired -> ClientPhase.Expired
                RequestStatus.Canceled -> ClientPhase.Canceled
                RequestStatus.Completed -> ClientPhase.Completed
                RequestStatus.Accepted, RequestStatus.Scheduled, RequestStatus.OnTheWay, RequestStatus.Arrived -> ClientPhase.Accepted
                RequestStatus.Unknown -> ClientPhase.Waiting
            }
        }

    /** Reenviar solo si la API tiene con qué (una normal necesita su dirección guardada). */
    val canResend: Boolean
        get() {
            val request = request ?: return false
            return request.categoryId != null &&
                if (request.isEmergency) request.position != null else request.branchId != null && request.addressId != null
        }
}

/**
 * Pantalla de espera del cliente (ANDROID-17): cuenta regresiva de 5 minutos mientras el proveedor
 * responde, cancelar, qué hacer si la rechazan o vence, y los datos del técnico si la aceptan.
 * Se actualiza sola consultando la solicitud cada pocos segundos.
 */
@HiltViewModel
class ClientRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ClientRequestRepository,
    private val payments: PaymentRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    /** Reloj de la cuenta regresiva; las pruebas lo cambian. */
    internal var now: () -> Instant = Instant::now

    private val _uiState = MutableStateFlow(ClientRequestUiState())
    val uiState: StateFlow<ClientRequestUiState> = _uiState.asStateFlow()

    private var ticker: Job? = null

    /** Llave del pago que se registra: la misma en cada reintento para que no se cobre doble. */
    private var paymentKey: String? = null
    private var poller: Job? = null

    init {
        load(showLoading = true)
        startPolling()
    }

    fun retry() = load(showLoading = true)

    fun cancel() {
        val state = _uiState.value
        if (state.isCanceling || state.phase != ClientPhase.Waiting) return
        _uiState.update { it.copy(isCanceling = true, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.cancel(requestId)) {
                is ApiResult.Success -> load(showLoading = false)
                is ApiResult.Failure -> {
                    // 409: mientras tanto la aceptaron o venció; se muestra cómo quedó.
                    _uiState.update { it.copy(actionError = result.error) }
                    load(showLoading = false)
                }
            }
            _uiState.update { it.copy(isCanceling = false) }
        }
    }

    fun resend() {
        val state = _uiState.value
        val request = state.request ?: return
        if (state.isResending || !state.canResend) return
        _uiState.update { it.copy(isResending = true, actionError = null) }
        viewModelScope.launch {
            when (val result = repository.resend(request, UUID.randomUUID().toString())) {
                is ApiResult.Success -> _uiState.update { it.copy(isResending = false, resentRequestId = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(isResending = false, actionError = result.error) }
            }
        }
    }

    /** El cliente aprueba o rechaza lo que el técnico quiere cobrar de más. */
    fun respondAdjustment(approve: Boolean) {
        val state = _uiState.value
        val adjustment = state.request?.pendingAdjustment ?: return
        if (state.respondingAdjustmentId != null) return
        _uiState.update { it.copy(respondingAdjustmentId = adjustment.id, paymentError = null) }
        viewModelScope.launch {
            val result = payments.respondAdjustment(requestId, adjustment.id, approve)
            // 409: el ajuste ya estaba respondido o el servicio avanzó; se muestra cómo quedó.
            if (result is ApiResult.Failure) _uiState.update { it.copy(paymentError = result.error) }
            loadNow(showLoading = false)
            _uiState.update { it.copy(respondingAdjustmentId = null) }
        }
    }

    fun openPayment() {
        if (!_uiState.value.canRegisterPayment) return
        if (paymentKey == null) paymentKey = UUID.randomUUID().toString()
        _uiState.update { it.copy(showPaymentSheet = true, paymentError = null) }
    }

    fun dismissPayment() = _uiState.update { it.copy(showPaymentSheet = false, paymentError = null) }

    /** Queda pendiente hasta que el proveedor confirme que lo recibió. */
    fun registerPayment(draft: PaymentDraft) {
        if (_uiState.value.isRegisteringPayment) return
        val key = paymentKey ?: UUID.randomUUID().toString().also { paymentKey = it }
        _uiState.update { it.copy(isRegisteringPayment = true, paymentError = null) }
        viewModelScope.launch {
            when (val result = payments.registerPayment(requestId, draft.method, draft.amountCents, draft.reference, key)) {
                is ApiResult.Success -> {
                    paymentKey = null
                    _uiState.update { it.copy(isRegisteringPayment = false, showPaymentSheet = false) }
                    loadNow(showLoading = false)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isRegisteringPayment = false, paymentError = result.error) }
            }
        }
    }

    /**
     * Mientras la solicitud sigue viva se consulta; al terminar (rechazo, vencida...) se deja. Ya
     * terminada se sigue mientras falte pagar, para ver cuando el proveedor confirme el pago.
     */
    private fun startPolling() {
        poller = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val state = _uiState.value
                val phase = state.phase
                val owes = phase == ClientPhase.Completed && (state.request?.balanceCents ?: 0) > 0
                if (phase == ClientPhase.Waiting || phase == ClientPhase.Accepted || phase == null || owes) load(showLoading = false)
            }
        }
    }

    private suspend fun loadNow(showLoading: Boolean) {
        if (showLoading) _uiState.update { it.copy(isLoading = true, loadError = null) }
        when (val result = repository.request(requestId)) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(isLoading = false, loadError = null, request = result.data, secondsLeft = secondsLeft(result.data)) }
                startCountdown()
            }
            is ApiResult.Failure -> if (showLoading || _uiState.value.request == null) {
                _uiState.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    private fun load(showLoading: Boolean) {
        viewModelScope.launch { loadNow(showLoading) }
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

    companion object {
        /** Cada cuánto se pregunta a la API si ya respondieron. */
        const val POLL_INTERVAL_MS = 4_000L

        /** La API da 5 minutos para aceptar; el aro de la cuenta regresiva parte de aquí. */
        const val RESPONSE_WINDOW_SECONDS = 5 * 60L
    }
}
