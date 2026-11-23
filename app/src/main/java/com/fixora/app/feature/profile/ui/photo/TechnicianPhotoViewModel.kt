package com.fixora.app.feature.profile.ui.photo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.profile.data.PhotoReader
import com.fixora.app.feature.profile.data.ProfileState
import com.fixora.app.feature.profile.data.TechnicianProfileRepository
import com.fixora.app.feature.profile.domain.TechnicianProfileRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TechnicianPhotoUiState(
    val isLoaded: Boolean = false,
    val loadError: ApiError? = null,
    val notTechnician: Boolean = false,
    /** Foto guardada en la API (enlace firmado). */
    val savedPhotoUrl: String? = null,
    /** Foto recién tomada o elegida, todavía sin guardar. */
    val pickedPhoto: Uri? = null,
    val bio: String = "",
    val isSaving: Boolean = false,
    val error: ApiError? = null,
    /** La foto elegida no se pudo leer (archivo dañado o que no es imagen). */
    val photoUnreadable: Boolean = false,
    val isSaved: Boolean = false,
) {
    val bioRemaining get() = TechnicianProfileRules.BIO_MAX_LENGTH - bio.length
}

/** Foto (cámara o galería) y biografía del técnico (ANDROID-7). */
@HiltViewModel
class TechnicianPhotoViewModel @Inject constructor(
    private val repository: TechnicianProfileRepository,
    private val photoReader: PhotoReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TechnicianPhotoUiState())
    val uiState: StateFlow<TechnicianPhotoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { state ->
                _uiState.update { ui ->
                    when (state) {
                        // Solo se llena una vez: después el formulario es del usuario.
                        is ProfileState.Loaded -> if (ui.isLoaded) {
                            ui
                        } else {
                            ui.copy(
                                isLoaded = true,
                                loadError = null,
                                savedPhotoUrl = state.profile.photoUrl,
                                bio = state.profile.bio,
                            )
                        }
                        is ProfileState.Failed -> ui.copy(loadError = state.error)
                        ProfileState.NotTechnician -> ui.copy(notTechnician = true)
                        ProfileState.Loading -> ui
                    }
                }
            }
        }
        retryLoad()
    }

    fun retryLoad() {
        viewModelScope.launch { repository.refresh() }
    }

    fun onPhotoPicked(uri: Uri?) {
        if (uri != null) _uiState.update { it.copy(pickedPhoto = uri, photoUnreadable = false, error = null) }
    }

    fun onBioChange(value: String) =
        _uiState.update { it.copy(bio = value.take(TechnicianProfileRules.BIO_MAX_LENGTH), error = null) }

    fun save() {
        val state = _uiState.value
        if (!state.isLoaded || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null, photoUnreadable = false) }
        viewModelScope.launch {
            val photo = state.pickedPhoto?.let { uri ->
                photoReader.readAsJpeg(uri) ?: run {
                    _uiState.update { it.copy(isSaving = false, photoUnreadable = true) }
                    return@launch
                }
            }
            when (val result = repository.updateProfile(state.bio, photo)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSaving = false, isSaved = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }
}
