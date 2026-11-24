package com.fixora.app.feature.account.ui.photo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.profile.data.PhotoReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lee la foto elegida como JPEG. Interfaz para probar el ViewModel sin Android. */
fun interface AccountPhotoReader {
    suspend fun readAsJpeg(uri: Uri): ByteArray?
}

data class AccountPhotoUiState(
    val isLoaded: Boolean = false,
    /** Foto guardada en la API (enlace firmado). */
    val savedPhotoUrl: String? = null,
    /** Foto recién tomada o elegida, todavía sin guardar. */
    val pickedPhoto: Uri? = null,
    /** Se pidió quitar la foto guardada; se quita al guardar. */
    val removeRequested: Boolean = false,
    /** Hay un servicio en curso: la foto no se puede cambiar (API-55). */
    val locked: Boolean = false,
    val isSaving: Boolean = false,
    val error: ApiError? = null,
    val photoUnreadable: Boolean = false,
    val isSaved: Boolean = false,
) {
    /** Lo que se ve en el círculo: la elegida, la guardada o nada si se va a quitar. */
    val shownPhoto: Any? get() = pickedPhoto ?: savedPhotoUrl.takeUnless { removeRequested }
    val canRemove get() = shownPhoto != null
}

/** Foto de perfil de la cuenta del cliente (cámara o galería). */
@HiltViewModel
class AccountPhotoViewModel @Inject constructor(
    private val repository: AccountRepository,
    private val photoReader: AccountPhotoReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountPhotoUiState())
    val uiState: StateFlow<AccountPhotoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.activeService.collect { locked -> _uiState.update { it.copy(locked = locked) } }
        }
        // Se vuelve a preguntar: el servicio pudo empezar o terminar desde la última consulta.
        viewModelScope.launch { repository.refreshPersonalData() }
        viewModelScope.launch {
            // Solo se lee una vez: después la foto en pantalla es la que elija el usuario.
            val personal = repository.personalData.first()
            _uiState.update { it.copy(isLoaded = true, savedPhotoUrl = personal.photoUrl) }
        }
    }

    fun onPhotoPicked(uri: Uri?) {
        if (uri != null) _uiState.update { it.copy(pickedPhoto = uri, removeRequested = false, photoUnreadable = false, error = null) }
    }

    fun remove() = _uiState.update { it.copy(pickedPhoto = null, removeRequested = true, photoUnreadable = false, error = null) }

    fun save() {
        val state = _uiState.value
        if (!state.isLoaded || state.isSaving || state.locked) return
        val picked = state.pickedPhoto
        if (picked == null && !state.removeRequested) {
            _uiState.update { it.copy(isSaved = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null, photoUnreadable = false) }
        viewModelScope.launch {
            val photo = picked?.let { uri ->
                photoReader.readAsJpeg(uri) ?: run {
                    _uiState.update { it.copy(isSaving = false, photoUnreadable = true) }
                    return@launch
                }
            }
            when (val result = repository.updatePhoto(photo)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSaving = false, isSaved = true) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }
}
