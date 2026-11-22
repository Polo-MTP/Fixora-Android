package com.fixora.app.feature.servicerequest.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.servicerequest.data.ServiceRequestRepository
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceRequestUiState(
    val isLoading: Boolean = true,
    val request: ServiceRequest? = null,
    val error: ApiError? = null,
)

/** Solicitud abierta desde un aviso (ANDROID-13). */
@HiltViewModel
class ServiceRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ServiceRequestRepository,
) : ViewModel() {

    // El argumento de ServiceRequestRoute se guarda con el nombre de la propiedad.
    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(ServiceRequestUiState())
    val uiState: StateFlow<ServiceRequestUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.request(requestId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, request = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
