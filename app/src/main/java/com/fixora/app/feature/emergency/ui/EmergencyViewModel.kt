package com.fixora.app.feature.emergency.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.emergency.data.EmergencyRepository
import com.fixora.app.feature.map.data.MapRepository
import com.fixora.app.feature.map.domain.ServiceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class LocationStatus { Locating, Ready, NoPermission, Unavailable }

data class EmergencyUiState(
    val categories: List<ServiceCategory> = emptyList(),
    val categoriesError: ApiError? = null,
    val categoryId: String? = null,
    val location: LocationStatus = LocationStatus.Locating,
    val position: Coordinates? = null,
    val isSending: Boolean = false,
    val sendError: ApiError? = null,
    val createdRequestId: String? = null,
) {
    val category: ServiceCategory? get() = categories.firstOrNull { it.id == categoryId }
    val canSend: Boolean get() = category != null && position != null && !isSending
}

/**
 * Botón de emergencia (ANDROID-16, Figma «Fixora - Confirmar Emergencia»): sin formulario, solo el
 * servicio y la ubicación actual; la API la manda como urgente a los técnicos cercanos.
 */
@HiltViewModel
class EmergencyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val categories: MapRepository,
    private val location: LocationSource,
    private val repository: EmergencyRepository,
) : ViewModel() {

    /** Para que un reintento no cree otra emergencia (client_uuid); sobrevive si se recrea la pantalla. */
    private val clientUuid: String = savedStateHandle.get<String>(KEY_UUID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_UUID] = it }

    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    private val _requestPermission = Channel<Unit>(Channel.BUFFERED)

    /** La pantalla pide el permiso de ubicación cuando llega algo aquí. */
    val requestPermission: Flow<Unit> = _requestPermission.receiveAsFlow()

    init {
        loadCategories()
        locate()
    }

    fun onCategorySelected(id: String) = _uiState.update { it.copy(categoryId = id, sendError = null) }

    fun retryCategories() = loadCategories()

    /** Vuelve a buscar la ubicación; sin permiso, lo pide. */
    fun locate() {
        if (!location.hasPermission()) {
            _uiState.update { it.copy(location = LocationStatus.NoPermission) }
            _requestPermission.trySend(Unit)
            return
        }
        _uiState.update { it.copy(location = LocationStatus.Locating) }
        viewModelScope.launch {
            val here = location.current()
            _uiState.update {
                it.copy(position = here, location = if (here == null) LocationStatus.Unavailable else LocationStatus.Ready)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) locate() else _uiState.update { it.copy(location = LocationStatus.NoPermission) }
    }

    fun send() {
        val state = _uiState.value
        if (!state.canSend) return
        val categoryId = state.categoryId ?: return
        val position = state.position ?: return
        _uiState.update { it.copy(isSending = true, sendError = null) }
        viewModelScope.launch {
            when (val result = repository.send(clientUuid, categoryId, position)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSending = false, createdRequestId = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSending = false, sendError = result.error) }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(categoriesError = null) }
            when (val result = categories.categories()) {
                is ApiResult.Success -> _uiState.update { it.copy(categories = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(categoriesError = result.error) }
            }
        }
    }

    private companion object {
        const val KEY_UUID = "clientUuid"
    }
}
