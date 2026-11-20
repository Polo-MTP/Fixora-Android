package com.fixora.app.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.map.data.MapRepository
import com.fixora.app.feature.map.domain.MapFilters
import com.fixora.app.feature.map.domain.MapProvider
import com.fixora.app.feature.map.domain.MapRules
import com.fixora.app.feature.map.domain.ServiceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Abrir el mapa ya filtrado desde el Inicio (ANDROID-12): por un servicio o por lo que se escribió
 * en el buscador. [id] cambia en cada pedido para repetirlo aunque sea igual al anterior.
 */
data class MapLaunch(val categoryId: String? = null, val query: String = "", val id: Long = System.nanoTime())

/** Pedido de mover la cámara; [id] cambia en cada pedido para repetirlo aunque el punto sea el mismo. */
data class CameraRequest(val center: Coordinates, val zoom: Double, val id: Int)

data class MapUiState(
    /** Punto alrededor del cual se buscó; null mientras se obtiene la ubicación. */
    val origin: Coordinates? = null,
    val userLocation: Coordinates? = null,
    /** No hay permiso o no se pudo obtener la ubicación: se busca en el centro por defecto. */
    val usingDefaultCenter: Boolean = false,
    val categories: List<ServiceCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val query: String = "",
    val filters: MapFilters = MapFilters(),
    val providers: List<MapProvider> = emptyList(),
    /** Cuántos hay en el radio, aunque solo se muestren los 50 más cercanos. */
    val total: Int = 0,
    val isLoading: Boolean = false,
    val error: ApiError? = null,
    val selectedProviderId: String? = null,
    val camera: CameraRequest? = null,
    /** El mapa se movió lejos de donde se buscó. */
    val canSearchHere: Boolean = false,
) {
    val visibleProviders: List<MapProvider> get() = providers.filter { it.matches(query) }
    val selectedProvider: MapProvider? get() = visibleProviders.firstOrNull { it.id == selectedProviderId }
    val selectedCategory: ServiceCategory? get() = categories.firstOrNull { it.id == selectedCategoryId }
}

/** Mapa del cliente con los proveedores cercanos (ANDROID-10). */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: MapRepository,
    private val location: LocationSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _requestPermission = Channel<Unit>(Channel.BUFFERED)

    /** La pantalla lanza el diálogo de permisos de ubicación cuando llega algo aquí. */
    val requestPermission: Flow<Unit> = _requestPermission.receiveAsFlow()

    private var searchJob: Job? = null
    private var cameraIds = 0
    private var mapCenter: Coordinates? = null

    init {
        loadCategories()
        if (location.hasPermission()) locate() else _requestPermission.trySend(Unit)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) locate() else useCenter(null)
    }

    /** Vuelve a pedir la ubicación (botón «Mi ubicación»); sin permiso, vuelve a pedir el permiso. */
    fun onMyLocation() {
        if (location.hasPermission()) locate() else _requestPermission.trySend(Unit)
    }

    fun onCategorySelected(categoryId: String?) {
        if (categoryId == _uiState.value.selectedCategoryId) return
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        search()
    }

    fun onQueryChange(query: String) = _uiState.update { state ->
        val selected = state.selectedProviderId?.takeIf { id -> state.providers.any { it.id == id && it.matches(query) } }
        state.copy(query = query, selectedProviderId = selected)
    }

    /**
     * Al buscar con el teclado: si lo escrito es un servicio (por ejemplo «plomeria»), se elige ese
     * servicio; si no, se queda como filtro por nombre del negocio.
     */
    fun onQuerySubmit() {
        val state = _uiState.value
        val category = state.categories.firstOrNull { it.matches(state.query) } ?: return
        _uiState.update { it.copy(query = "", selectedCategoryId = category.id) }
        search()
    }

    /**
     * Llega desde el Inicio. Un texto que coincide con un servicio elige ese servicio; si no, se
     * queda como filtro por nombre del negocio sobre todos los servicios.
     */
    fun onLaunch(launch: MapLaunch) {
        val query = launch.query.trim()
        val state = _uiState.value
        val category = launch.categoryId ?: state.categories.firstOrNull { it.matches(query) }?.id
        _uiState.update {
            it.copy(
                selectedCategoryId = category,
                query = if (category != null && launch.categoryId == null) "" else query,
                selectedProviderId = null,
            )
        }
        search()
    }

    fun onFiltersApplied(filters: MapFilters) {
        val state = _uiState.value
        if (filters == state.filters) return
        val radiusChanged = filters.radiusKm != state.filters.radiusKm
        _uiState.update { it.copy(filters = filters) }
        if (radiusChanged) state.origin?.let { moveCamera(it, filters.radiusKm) }
        search()
    }

    fun onProviderSelected(id: String?) = _uiState.update { it.copy(selectedProviderId = id) }

    /** El mapa terminó de moverse; si quedó lejos de donde se buscó, se ofrece buscar ahí. */
    fun onCameraIdle(center: Coordinates) {
        mapCenter = center
        _uiState.update { state ->
            val origin = state.origin ?: return@update state
            state.copy(canSearchHere = MapRules.movedEnough(origin, center, state.filters.radiusKm))
        }
    }

    fun onSearchHere() {
        val center = mapCenter ?: return
        _uiState.update { it.copy(origin = center, canSearchHere = false) }
        search()
    }

    fun retry() {
        if (_uiState.value.categories.isEmpty()) loadCategories()
        if (_uiState.value.origin == null) onMyLocation() else search()
    }

    private fun locate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            useCenter(location.current())
        }
    }

    /** Busca alrededor de [position]; sin ubicación, alrededor del centro por defecto. */
    private fun useCenter(position: Coordinates?) {
        val origin = position ?: MapRules.DEFAULT_CENTER
        _uiState.update {
            it.copy(origin = origin, userLocation = position, usingDefaultCenter = position == null, canSearchHere = false)
        }
        moveCamera(origin, _uiState.value.filters.radiusKm)
        search()
    }

    private fun moveCamera(center: Coordinates, radiusKm: Int) {
        _uiState.update { it.copy(camera = CameraRequest(center, MapRules.zoomFor(radiusKm), ++cameraIds)) }
    }

    private fun search() {
        val state = _uiState.value
        val origin = state.origin ?: return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.search(origin, state.filters, state.selectedCategoryId)) {
                is ApiResult.Success -> _uiState.update { current ->
                    val providers = result.data.providers
                    current.copy(
                        isLoading = false,
                        providers = providers,
                        total = result.data.total,
                        selectedProviderId = current.selectedProviderId?.takeIf { id -> providers.any { it.id == id } },
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            // Sin servicios el mapa sigue sirviendo con «Todos»; no se muestra error por esto.
            (repository.categories() as? ApiResult.Success)?.let { result ->
                _uiState.update { it.copy(categories = result.data) }
            }
        }
    }
}
