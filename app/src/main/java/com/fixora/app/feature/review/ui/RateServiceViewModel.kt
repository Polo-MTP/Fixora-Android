package com.fixora.app.feature.review.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.review.data.ReviewRepository
import com.fixora.app.feature.review.domain.ReviewDirection
import com.fixora.app.feature.review.domain.ReviewRules
import com.fixora.app.feature.review.domain.ServiceReview
import com.fixora.app.feature.servicerequest.data.EvidenceProblem
import com.fixora.app.feature.servicerequest.data.EvidenceReadResult
import com.fixora.app.feature.servicerequest.data.EvidenceReader
import com.fixora.app.feature.servicerequest.data.ServiceRequestRepository
import com.fixora.app.feature.servicerequest.domain.PickedEvidence
import com.fixora.app.feature.servicerequest.domain.evidenceKindOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RateServiceUiState(
    val isLoading: Boolean = true,
    val loadError: ApiError? = null,
    /** Quién califica a quién; null mientras carga. */
    val direction: ReviewDirection? = null,
    /** A quién se califica: el técnico que atendió o el cliente. */
    val counterpartName: String = "",
    val counterpartPhotoUrl: String? = null,
    val canReview: Boolean = false,
    /** La que ya había escrito la cuenta: se muestra en vez del formulario. */
    val existing: ServiceReview? = null,
    val rating: Int = 0,
    val comment: String = "",
    val evidence: List<PickedEvidence> = emptyList(),
    val problem: EvidenceProblem? = null,
    val isReading: Boolean = false,
    val isSending: Boolean = false,
    val submitted: Boolean = false,
    val error: ApiError? = null,
    val isDone: Boolean = false,
) {
    val ratingMissing get() = submitted && rating == 0
    val canAddEvidence get() = evidence.size < ReviewRules.MAX_EVIDENCE && !isSending && !isReading

    /** Ni la escribió ni puede: la solicitud todavía no termina. */
    val notYet get() = !isLoading && loadError == null && !canReview && existing == null
}

/**
 * Calificar al terminar el servicio (ANDROID-26), en ambas apps: el cliente califica al técnico y
 * el técnico al cliente, con estrellas de 1 a 5, comentario y hasta 3 fotos o videos.
 */
@HiltViewModel
class RateServiceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reviews: ReviewRepository,
    private val requests: ServiceRequestRepository,
    private val evidenceReader: EvidenceReader,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    /** El mismo en cada reintento: la API regresa la reseña original en vez de duplicarla. */
    private val clientUuid = UUID.randomUUID().toString()

    private val evidenceFiles = mutableMapOf<Long, Pair<String, ByteArray>>()
    private var nextEvidenceId = 0L

    private val _uiState = MutableStateFlow(RateServiceUiState())
    val uiState: StateFlow<RateServiceUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val status = reviews.requestReviews(requestId)
            val request = requests.request(requestId)
            when {
                status is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, loadError = status.error) }
                request is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, loadError = request.error) }
                status is ApiResult.Success && request is ApiResult.Success -> {
                    val data = status.data
                    val rateTechnician = data.myDirection == ReviewDirection.ClientToProvider
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            direction = data.myDirection,
                            counterpartName = if (rateTechnician) request.data.technicianName.orEmpty() else request.data.clientName,
                            counterpartPhotoUrl = if (rateTechnician) request.data.technicianPhotoUrl else null,
                            canReview = data.canReview,
                            existing = data.mine,
                        )
                    }
                }
            }
        }
    }

    fun onRatingChange(rating: Int) {
        if (rating in 1..5) _uiState.update { it.copy(rating = rating, error = null) }
    }

    fun onCommentChange(comment: String) =
        _uiState.update { it.copy(comment = comment.take(ReviewRules.MAX_COMMENT_LENGTH), error = null) }

    /** Foto de la cámara o de la galería; null si el usuario no eligió nada. */
    fun onEvidencePicked(uri: Uri?) {
        if (uri != null) addEvidence { evidenceReader.read(uri, ReviewRules.EVIDENCE_MIME_TYPES) }
    }

    /** Lee la evidencia con [read] y la agrega. Separado de la Uri para poder probarlo. */
    fun addEvidence(read: suspend () -> EvidenceReadResult) {
        if (!_uiState.value.canAddEvidence) return
        _uiState.update { it.copy(isReading = true, problem = null) }
        viewModelScope.launch {
            when (val result = read()) {
                is EvidenceReadResult.Ready -> {
                    val id = nextEvidenceId++
                    evidenceFiles[id] = result.mimeType to result.bytes
                    val item = PickedEvidence(id, result.name, evidenceKindOf(result.mimeType))
                    _uiState.update { it.copy(isReading = false, evidence = it.evidence + item, error = null) }
                }
                is EvidenceReadResult.Rejected -> _uiState.update { it.copy(isReading = false, problem = result.problem) }
            }
        }
    }

    fun removeEvidence(id: Long) {
        if (_uiState.value.isSending) return
        evidenceFiles.remove(id)
        _uiState.update { state -> state.copy(evidence = state.evidence.filterNot { it.id == id }, problem = null) }
    }

    /** Sube lo que falte y guarda la reseña; lo ya subido conserva su llave para un reintento. */
    fun submit() {
        val state = _uiState.value
        if (state.isSending || state.isReading || !state.canReview) return
        _uiState.update { it.copy(submitted = true) }
        if (state.rating == 0) return
        _uiState.update { it.copy(isSending = true, error = null, problem = null) }
        viewModelScope.launch {
            for (item in state.evidence) {
                if (item.key != null) continue
                val (mimeType, bytes) = evidenceFiles[item.id] ?: continue
                when (val upload = reviews.uploadEvidence(mimeType, bytes)) {
                    is ApiResult.Success -> {
                        evidenceFiles.remove(item.id)
                        _uiState.update { current ->
                            current.copy(evidence = current.evidence.map { if (it.id == item.id) it.copy(key = upload.data) else it })
                        }
                    }
                    is ApiResult.Failure -> {
                        _uiState.update { it.copy(isSending = false, error = upload.error) }
                        return@launch
                    }
                }
            }
            val keys = _uiState.value.evidence.mapNotNull { it.key }
            val comment = state.comment.trim().takeIf { it.isNotEmpty() }
            when (val result = reviews.create(requestId, state.rating, comment, keys, clientUuid)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSending = false, isDone = true) }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(isSending = false, error = result.error) }
                    // 409: ya la había calificado (por ejemplo desde otro celular); se muestra la que quedó.
                    val error = result.error
                    if (error is ApiError.Http && error.status == 409) load()
                }
            }
        }
    }
}
