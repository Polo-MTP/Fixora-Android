package com.fixora.app.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.map.data.MapRepository
import com.fixora.app.feature.map.domain.ServiceCategory
import com.fixora.app.feature.map.ui.MapLaunch
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nombre de pila del cliente para el saludo; interfaz para probar sin la cuenta. */
fun interface ClientFirstNameSource {
    fun firstName(): Flow<String?>
}

@Module
@InstallIn(SingletonComponent::class)
object ClientHomeModule {
    @Provides
    fun provideClientFirstNameSource(account: AccountRepository) = ClientFirstNameSource {
        account.personalData.map { data -> data.name.trim().substringBefore(' ').ifBlank { null } }
    }
}

data class ClientHomeUiState(
    /** null mientras no se sabe; el saludo queda solo «Hola». */
    val firstName: String? = null,
    val categories: List<ServiceCategory> = emptyList(),
    val isLoading: Boolean = true,
    val error: ApiError? = null,
    val query: String = "",
)

/** Inicio del cliente (ANDROID-12): buscador y accesos por servicio que abren el mapa filtrado. */
@HiltViewModel
class ClientHomeViewModel @Inject constructor(
    private val repository: MapRepository,
    private val nameSource: ClientFirstNameSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientHomeUiState())
    val uiState: StateFlow<ClientHomeUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        viewModelScope.launch {
            // Si no se pueden leer los datos personales, el saludo se queda sin nombre.
            nameSource.firstName()
                .catch { }
                .collect { name -> _uiState.update { it.copy(firstName = name) } }
        }
    }

    fun retry() = loadCategories()

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    /**
     * Lo que abre el mapa al buscar: si el texto es un servicio («plomeria», «electri») va ese
     * servicio; si no, el texto se usa para filtrar por nombre del negocio. Vacío no abre nada.
     */
    fun searchLaunch(): MapLaunch? {
        val state = _uiState.value
        val query = state.query.trim()
        if (query.isEmpty()) return null
        _uiState.update { it.copy(query = "") }
        val category = state.categories.firstOrNull { it.matches(query) }
        return if (category != null) MapLaunch(categoryId = category.id) else MapLaunch(query = query)
    }

    fun categoryLaunch(category: ServiceCategory) = MapLaunch(categoryId = category.id)

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.categories()) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, categories = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
