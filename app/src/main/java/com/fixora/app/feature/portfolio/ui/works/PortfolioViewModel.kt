package com.fixora.app.feature.portfolio.ui.works

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.portfolio.data.PortfolioRepository
import com.fixora.app.feature.portfolio.domain.PortfolioRules
import com.fixora.app.feature.portfolio.domain.PortfolioWork
import com.fixora.app.feature.profile.data.PhotoReader
import com.fixora.app.feature.profile.data.ProfileState
import com.fixora.app.feature.profile.data.TechnicianProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PortfolioUiState(
    val isLoading: Boolean = true,
    val works: List<PortfolioWork> = emptyList(),
    val loadError: ApiError? = null,
    /** La cuenta no tiene sucursal de técnico, así que no tiene portafolio. */
    val noBranch: Boolean = false,
    /** Trabajo que se está borrando; mientras tanto su botón no responde. */
    val deletingId: String? = null,
    val deleteError: ApiError? = null,
    /** Trabajo al que se le están agregando fotos. */
    val addingPhotosId: String? = null,
    val addPhotosError: ApiError? = null,
    /** Alguna foto elegida no se pudo leer (archivo dañado o que no es imagen). */
    val photoUnreadable: Boolean = false,
)

/** Cuántas fotos más le caben a [work]. */
fun PortfolioWork.photosLeft() = PortfolioRules.MAX_PHOTOS - photoUrls.size

/** Trabajos realizados del proveedor (ANDROID-8): lista y borrar. */
@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: PortfolioRepository,
    private val profileRepository: TechnicianProfileRepository,
    private val photoReader: PhotoReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    /** Se llama al abrir la pantalla y al regresar de agregar un trabajo. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            val branchId = branchId() ?: run {
                _uiState.update { it.copy(isLoading = false, noBranch = true) }
                return@launch
            }
            _uiState.update {
                when (val result = repository.works(branchId)) {
                    is ApiResult.Success -> it.copy(isLoading = false, works = result.data, noBranch = false)
                    is ApiResult.Failure -> it.copy(isLoading = false, loadError = result.error)
                }
            }
        }
    }

    fun delete(workId: String) {
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(deletingId = workId, deleteError = null) }
        viewModelScope.launch {
            val result = repository.deleteWork(workId)
            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(deletingId = null, works = state.works.filterNot { it.id == workId })
                    is ApiResult.Failure -> state.copy(deletingId = null, deleteError = result.error)
                }
            }
        }
    }

    /** Sube las fotos elegidas y las agrega al trabajo; si son más de las que le caben, usa las primeras. */
    fun addPhotos(workId: String, uris: List<Uri>) {
        val work = _uiState.value.works.firstOrNull { it.id == workId } ?: return
        val picked = uris.distinct().take(work.photosLeft())
        if (picked.isEmpty() || _uiState.value.addingPhotosId != null) return
        _uiState.update { it.copy(addingPhotosId = workId, addPhotosError = null, photoUnreadable = false) }
        viewModelScope.launch {
            // Se reducen y se les quitan los metadatos (ubicación incluida) antes de subirlas.
            val photos = picked.map { uri ->
                photoReader.readAsJpeg(uri)?.takeIf { it.size <= PortfolioRules.PHOTO_MAX_BYTES } ?: run {
                    _uiState.update { it.copy(addingPhotosId = null, photoUnreadable = true) }
                    return@launch
                }
            }
            val result = repository.addPhotos(workId, photos)
            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(
                        addingPhotosId = null,
                        works = state.works.map { if (it.id == workId) result.data else it },
                    )
                    is ApiResult.Failure -> state.copy(addingPhotosId = null, addPhotosError = result.error)
                }
            }
        }
    }

    private suspend fun branchId(): String? {
        (profileRepository.state.first() as? ProfileState.Loaded)?.profile?.branchId?.let { return it }
        profileRepository.refresh()
        return (profileRepository.state.first() as? ProfileState.Loaded)?.profile?.branchId
    }
}
