package com.fixora.app.feature.portfolio.ui.certificates

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.portfolio.data.DocumentProblem
import com.fixora.app.feature.portfolio.data.DocumentReadResult
import com.fixora.app.feature.portfolio.data.DocumentReader
import com.fixora.app.feature.portfolio.data.PickedDocument
import com.fixora.app.feature.portfolio.data.PortfolioRepository
import com.fixora.app.feature.portfolio.domain.Certificate
import com.fixora.app.feature.portfolio.domain.CertificateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lee el archivo elegido. Interfaz para probar el ViewModel sin Android. */
fun interface CertificateFileReader {
    suspend fun read(uri: Uri): DocumentReadResult
}

data class CertificatesUiState(
    val isLoading: Boolean = true,
    val certificates: List<Certificate> = emptyList(),
    val loadError: ApiError? = null,
    val isUploading: Boolean = false,
    /** El archivo elegido no sirve (tipo, tamaño o no se pudo leer). */
    val fileProblem: DocumentProblem? = null,
    val uploadError: ApiError? = null,
    /** Se acaba de subir uno: se avisa que queda pendiente de revisión. */
    val justUploaded: Boolean = false,
    /** Certificado cuya visibilidad se está cambiando. */
    val togglingId: String? = null,
    val toggleError: ApiError? = null,
)

/**
 * Certificados del proveedor (ANDROID-8): subir uno nuevo, ver si ya lo revisó el administrador y
 * elegir cuáles se muestran en la ficha. Solo se pueden mostrar los aprobados.
 */
@HiltViewModel
class CertificatesViewModel @Inject constructor(
    private val repository: PortfolioRepository,
    private val fileReader: CertificateFileReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CertificatesUiState())
    val uiState: StateFlow<CertificatesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** El usuario eligió un archivo en el selector del sistema (null si lo cerró sin elegir). */
    fun onFilePicked(uri: Uri?) {
        if (uri != null) onFileSelected { fileReader.read(uri) }
    }

    /** Lee el archivo con [read] y, si sirve, lo sube. Separado de la Uri para poder probarlo. */
    internal fun onFileSelected(read: suspend () -> DocumentReadResult) {
        if (_uiState.value.isUploading) return
        _uiState.update { it.copy(isUploading = true, fileProblem = null, uploadError = null, justUploaded = false) }
        viewModelScope.launch {
            when (val file = read()) {
                is DocumentReadResult.Rejected -> _uiState.update { it.copy(isUploading = false, fileProblem = file.problem) }
                is DocumentReadResult.Ready -> upload(file.document)
            }
        }
    }

    fun setVisibility(certificate: Certificate, isPublic: Boolean) {
        if (certificate.status != CertificateStatus.Approved || _uiState.value.togglingId != null) return
        _uiState.update { it.copy(togglingId = certificate.id, toggleError = null) }
        viewModelScope.launch {
            val result = repository.setVisibility(certificate.id, isPublic)
            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(
                        togglingId = null,
                        certificates = state.certificates.map { if (it.id == certificate.id) it.copy(isPublic = isPublic) else it },
                    )
                    is ApiResult.Failure -> state.copy(togglingId = null, toggleError = result.error)
                }
            }
        }
    }

    private suspend fun upload(document: PickedDocument) {
        when (val result = repository.uploadCertificate(document)) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(isUploading = false, justUploaded = true) }
                load()
            }
            is ApiResult.Failure -> _uiState.update { it.copy(isUploading = false, uploadError = result.error) }
        }
    }

    private suspend fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        _uiState.update {
            when (val result = repository.certificates()) {
                is ApiResult.Success -> it.copy(isLoading = false, certificates = result.data)
                is ApiResult.Failure -> it.copy(isLoading = false, loadError = result.error)
            }
        }
    }
}
