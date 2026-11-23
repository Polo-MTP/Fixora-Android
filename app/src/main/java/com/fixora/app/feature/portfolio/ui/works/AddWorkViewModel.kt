package com.fixora.app.feature.portfolio.ui.works

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.portfolio.data.PortfolioRepository
import com.fixora.app.feature.portfolio.domain.PortfolioRules
import com.fixora.app.feature.profile.data.PhotoReader
import com.fixora.app.feature.profile.data.ProfileState
import com.fixora.app.feature.profile.data.TechnicianProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddWorkUiState(
    val title: String = "",
    val description: String = "",
    /** Fotos elegidas, todavía sin subir. Son opcionales. */
    val photos: List<Uri> = emptyList(),
    val submitted: Boolean = false,
    val isSaving: Boolean = false,
    val error: ApiError? = null,
    /** Alguna foto no se pudo leer (archivo dañado o que no es imagen). */
    val photoUnreadable: Boolean = false,
    val noBranch: Boolean = false,
    val isSaved: Boolean = false,
) {
    val titleMissing get() = submitted && title.isBlank()
    val canAddPhotos get() = photos.size < PortfolioRules.MAX_PHOTOS
    val photosLeft get() = PortfolioRules.MAX_PHOTOS - photos.size
}

/** Agregar un trabajo al portafolio (ANDROID-8): título, descripción y fotos opcionales. */
@HiltViewModel
class AddWorkViewModel @Inject constructor(
    private val repository: PortfolioRepository,
    private val profileRepository: TechnicianProfileRepository,
    private val photoReader: PhotoReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddWorkUiState())
    val uiState: StateFlow<AddWorkUiState> = _uiState.asStateFlow()

    fun onTitleChange(value: String) =
        _uiState.update { it.copy(title = value.take(PortfolioRules.TITLE_MAX_LENGTH), error = null) }

    fun onDescriptionChange(value: String) =
        _uiState.update { it.copy(description = value.take(PortfolioRules.DESCRIPTION_MAX_LENGTH), error = null) }

    /** Agrega las fotos elegidas sin pasar del máximo; las repetidas se ignoran. */
    fun onPhotosPicked(uris: List<Uri>) = _uiState.update { state ->
        val added = (state.photos + uris).distinct().take(PortfolioRules.MAX_PHOTOS)
        state.copy(photos = added, photoUnreadable = false, error = null)
    }

    fun removePhoto(uri: Uri) = _uiState.update { it.copy(photos = it.photos - uri, photoUnreadable = false) }

    fun save() {
        val state = _uiState.updateAndGet { it.copy(submitted = true) }
        if (state.title.isBlank() || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null, photoUnreadable = false) }
        viewModelScope.launch {
            val branchId = branchId() ?: run {
                _uiState.update { it.copy(isSaving = false, noBranch = true) }
                return@launch
            }
            // Se reducen y se les quitan los metadatos (ubicación incluida) antes de subirlas.
            val photos = state.photos.map { uri ->
                photoReader.readAsJpeg(uri)?.takeIf { it.size <= PortfolioRules.PHOTO_MAX_BYTES } ?: run {
                    _uiState.update { it.copy(isSaving = false, photoUnreadable = true) }
                    return@launch
                }
            }
            when (val result = repository.addWork(branchId, state.title, state.description, photos)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSaving = false, isSaved = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }

    private suspend fun branchId(): String? {
        (profileRepository.state.first() as? ProfileState.Loaded)?.profile?.branchId?.let { return it }
        profileRepository.refresh()
        return (profileRepository.state.first() as? ProfileState.Loaded)?.profile?.branchId
    }
}
