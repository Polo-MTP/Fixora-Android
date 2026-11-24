package com.fixora.app.feature.payment.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.payment.data.PaymentRepository
import com.fixora.app.feature.payment.domain.Debts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebtsUiState(
    val isLoading: Boolean = true,
    val debts: Debts? = null,
    val loadError: ApiError? = null,
    /** Servicio al que se le está mandando el recordatorio. */
    val remindingId: String? = null,
    /** Servicio al que se le acaba de mandar, para avisar que salió. */
    val remindedId: String? = null,
    val reminderError: Pair<String, ApiError>? = null,
)

/**
 * Adeudos de la cuenta (ANDROID-25): para el proveedor, lo que le deben y el botón para recordarle
 * al cliente (API-53); para el cliente, lo que debe. La API decide cuáles según quién pregunta.
 */
@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val repository: PaymentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebtsUiState())
    val uiState: StateFlow<DebtsUiState> = _uiState.asStateFlow()

    init {
        refresh(showLoading = true)
    }

    fun retry() = refresh(showLoading = true)

    /** Al volver a la pantalla: pudo confirmarse un pago mientras tanto. */
    fun refresh(showLoading: Boolean = false) {
        if (showLoading) _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.debts()) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, loadError = null, debts = result.data) }
                is ApiResult.Failure -> _uiState.update {
                    // Con datos ya en pantalla, un fallo al refrescar no los quita.
                    if (it.debts == null) it.copy(isLoading = false, loadError = result.error) else it.copy(isLoading = false)
                }
            }
        }
    }

    /** Uno cada 24 horas por servicio; la lista dice desde cuándo se puede otra vez. */
    fun remind(requestId: String) {
        val state = _uiState.value
        val debt = state.debts?.debts?.firstOrNull { it.requestId == requestId } ?: return
        if (state.remindingId != null || !debt.canRemind) return
        _uiState.update { it.copy(remindingId = requestId, remindedId = null, reminderError = null) }
        viewModelScope.launch {
            when (val result = repository.sendReminder(requestId)) {
                is ApiResult.Success -> _uiState.update { it.copy(remindingId = null, remindedId = requestId) }
                is ApiResult.Failure -> _uiState.update { it.copy(remindingId = null, reminderError = requestId to result.error) }
            }
            // En los dos casos se recarga: la API regresa cuándo se podrá mandar el siguiente.
            refresh()
        }
    }
}
