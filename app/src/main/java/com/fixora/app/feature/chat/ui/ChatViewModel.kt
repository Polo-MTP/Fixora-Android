package com.fixora.app.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.chat.data.ChatMe
import com.fixora.app.feature.chat.data.ChatRepository
import com.fixora.app.feature.chat.domain.ChatEvent
import com.fixora.app.feature.chat.domain.ChatMessage
import com.fixora.app.feature.chat.domain.ChatReply
import com.fixora.app.feature.chat.domain.ChatRules
import com.fixora.app.feature.servicerequest.data.ServiceRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Convierte la foto elegida (su uri) en un JPEG listo para subir; null si no se pudo leer. */
fun interface ChatPhotoReader {
    suspend fun read(uri: String): ByteArray?
}

/** Mensaje propio que todavía no confirma la API: se ve enseguida y, si falla, se puede reintentar. */
data class OutgoingMessage(
    val clientUuid: String,
    val text: String?,
    val replyTo: ChatReply?,
    /** Uris locales de las fotos, para mostrarlas mientras se suben. */
    val photoUris: List<String>,
    val failed: Boolean = false,
)

data class ChatUiState(
    val isLoading: Boolean = true,
    val loadError: ApiError? = null,
    /** Con quién se habla: el técnico para el cliente, el cliente para el proveedor. */
    val otherName: String? = null,
    val categoryName: String? = null,
    val myUserId: String? = null,
    /** Confirmados por la API, en orden. */
    val messages: List<ChatMessage> = emptyList(),
    val outgoing: List<OutgoingMessage> = emptyList(),
    /** La solicitud terminó: el chat queda de solo lectura. */
    val canSend: Boolean = true,
    val hasMore: Boolean = false,
    val isLoadingOlder: Boolean = false,
    /** Se cayó el tiempo real; se reintenta solo. */
    val reconnecting: Boolean = false,
    val draft: String = "",
    val draftPhotos: List<String> = emptyList(),
    val replyTo: ChatMessage? = null,
    /** No se pudo leer alguna foto elegida. */
    val photoError: Boolean = false,
) {
    fun isMine(message: ChatMessage): Boolean = message.senderId == myUserId

    val canSubmit: Boolean get() = canSend && (draft.isNotBlank() || draftPhotos.isNotEmpty())

    /** Mi último mensaje que la otra parte ya leyó: ahí va la marca «Leído». */
    val lastReadMineId: String?
        get() = messages.lastOrNull { isMine(it) && it.readAt != null }?.id
}

/**
 * Chat de una solicitud (ANDROID-23): texto, fotos y responder a un mensaje, en tiempo real.
 *
 * - Cada mensaje lleva su propia llave: si se reintenta (o llega también por el socket) no se
 *   duplica.
 * - Al reconectar pide los mensajes posteriores al último que tiene.
 * - Solo escucha el tiempo real mientras la pantalla está a la vista ([onStart] / [onStop]); en
 *   segundo plano la API manda un push en su lugar.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val requests: ServiceRequestRepository,
    private val photoReader: ChatPhotoReader,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var me: ChatMe? = null
    private var nextBefore: String? = null
    private var listener: Job? = null
    private var visible = false
    private var markingRead = false

    /** Llaves de las fotos ya subidas de cada mensaje, para no subirlas otra vez al reintentar. */
    private val uploadedKeys = mutableMapOf<String, List<String>>()

    /** Bytes de las fotos de cada mensaje pendiente, leídos una sola vez. */
    private val photoBytes = mutableMapOf<String, List<ByteArray>>()

    init {
        viewModelScope.launch {
            me = repository.me()
            _uiState.update { it.copy(myUserId = me?.userId) }
            loadHeader()
        }
        loadLatest(showLoading = true)
    }

    fun retry() = loadLatest(showLoading = true)

    /** La pantalla está a la vista: escucha el tiempo real y marca como leído. */
    fun onStart() {
        visible = true
        if (listener?.isActive != true) {
            listener = viewModelScope.launch {
                repository.events(requestId).collect(::onEvent)
            }
        }
        markRead()
    }

    fun onStop() {
        visible = false
        listener?.cancel()
        listener = null
    }

    fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text.take(ChatRules.MAX_TEXT)) }

    fun onReply(message: ChatMessage) = _uiState.update { it.copy(replyTo = message) }

    fun cancelReply() = _uiState.update { it.copy(replyTo = null) }

    fun onPhotosPicked(uris: List<String>) = _uiState.update {
        it.copy(draftPhotos = (it.draftPhotos + uris).distinct().take(ChatRules.MAX_PHOTOS), photoError = false)
    }

    fun removePhoto(uri: String) = _uiState.update { it.copy(draftPhotos = it.draftPhotos - uri) }

    fun send() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val outgoing = OutgoingMessage(
            clientUuid = UUID.randomUUID().toString(),
            text = state.draft.trim().takeIf { it.isNotEmpty() },
            replyTo = state.replyTo?.let { ChatReply(it.id, it.senderId, it.text, it.photos.isNotEmpty()) },
            photoUris = state.draftPhotos,
        )
        _uiState.update { it.copy(draft = "", draftPhotos = emptyList(), replyTo = null, outgoing = it.outgoing + outgoing) }
        deliver(outgoing)
    }

    fun retrySend(clientUuid: String) {
        val outgoing = _uiState.value.outgoing.firstOrNull { it.clientUuid == clientUuid && it.failed } ?: return
        updateOutgoing(clientUuid) { it.copy(failed = false) }
        deliver(outgoing.copy(failed = false))
    }

    fun discard(clientUuid: String) {
        uploadedKeys.remove(clientUuid)
        photoBytes.remove(clientUuid)
        _uiState.update { state -> state.copy(outgoing = state.outgoing.filterNot { it.clientUuid == clientUuid }) }
    }

    /** Al llegar arriba de la lista se piden los mensajes anteriores. */
    fun loadOlder() {
        val state = _uiState.value
        val before = nextBefore ?: return
        if (!state.hasMore || state.isLoadingOlder) return
        _uiState.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            when (val result = repository.page(requestId, before = before)) {
                is ApiResult.Success -> {
                    nextBefore = result.data.nextBefore
                    _uiState.update { it.copy(isLoadingOlder = false, hasMore = result.data.hasMore) }
                    merge(result.data.messages)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoadingOlder = false) }
            }
        }
    }

    private suspend fun loadHeader() {
        val request = (requests.request(requestId) as? ApiResult.Success)?.data ?: return
        val other = if (me?.isClient == true) request.technicianName ?: request.branchName else request.clientName.takeIf { it.isNotBlank() }
        _uiState.update { it.copy(otherName = other, categoryName = request.categoryName.takeIf { name -> name.isNotBlank() }) }
    }

    private fun loadLatest(showLoading: Boolean) {
        if (showLoading) _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.page(requestId)) {
                is ApiResult.Success -> {
                    nextBefore = result.data.nextBefore
                    _uiState.update {
                        it.copy(isLoading = false, loadError = null, canSend = result.data.canSend, hasMore = result.data.hasMore)
                    }
                    merge(result.data.messages)
                    markRead()
                }
                is ApiResult.Failure -> _uiState.update {
                    if (it.messages.isEmpty()) it.copy(isLoading = false, loadError = result.error) else it.copy(isLoading = false)
                }
            }
        }
    }

    private suspend fun onEvent(event: ChatEvent) {
        when (event) {
            ChatEvent.Connected -> {
                _uiState.update { it.copy(reconnecting = false) }
                catchUp()
            }
            ChatEvent.Disconnected -> _uiState.update { it.copy(reconnecting = true) }
            is ChatEvent.Message -> {
                merge(listOf(event.message))
                markRead()
            }
            is ChatEvent.Read -> {
                val ids = event.messageIds.toSet()
                val at = event.readAt ?: Instant.now()
                _uiState.update { state ->
                    state.copy(messages = state.messages.map { if (it.id in ids && it.readAt == null) it.copy(readAt = at) else it })
                }
            }
        }
    }

    /** Lo que llegó mientras no hubo conexión: los posteriores al último mensaje que se tiene. */
    private suspend fun catchUp() {
        var after = _uiState.value.messages.lastOrNull()?.id
        if (after == null) {
            loadLatest(showLoading = false)
            return
        }
        repeat(MAX_CATCH_UP_PAGES) {
            val page = (repository.page(requestId, after = after) as? ApiResult.Success)?.data ?: return
            _uiState.update { it.copy(canSend = page.canSend) }
            merge(page.messages)
            if (!page.hasMore || page.messages.isEmpty()) {
                markRead()
                return
            }
            after = page.messages.last().id
        }
    }

    /** Junta mensajes sin repetir (por id) y quita los pendientes que ya confirmó la API. */
    private fun merge(incoming: List<ChatMessage>) {
        if (incoming.isEmpty()) return
        _uiState.update { state ->
            val byId = LinkedHashMap<String, ChatMessage>()
            state.messages.forEach { byId[it.id] = it }
            incoming.forEach { message ->
                // Uno ya leído no vuelve a «no leído» si llega una copia anterior.
                val known = byId[message.id]
                byId[message.id] = if (known?.readAt != null && message.readAt == null) message.copy(readAt = known.readAt) else message
            }
            val confirmed = incoming.mapNotNull { it.clientUuid }.toSet()
            confirmed.forEach {
                uploadedKeys.remove(it)
                photoBytes.remove(it)
            }
            state.copy(
                messages = byId.values.sortedBy { it.order },
                outgoing = state.outgoing.filterNot { it.clientUuid in confirmed },
            )
        }
    }

    private fun deliver(outgoing: OutgoingMessage) {
        viewModelScope.launch {
            val keys = uploadedKeys[outgoing.clientUuid] ?: run {
                val photos = photoBytes[outgoing.clientUuid] ?: outgoing.photoUris.map { uri ->
                    photoReader.read(uri) ?: run {
                        _uiState.update { it.copy(photoError = true) }
                        updateOutgoing(outgoing.clientUuid) { it.copy(failed = true) }
                        return@launch
                    }
                }.also { photoBytes[outgoing.clientUuid] = it }
                val uploaded = mutableListOf<String>()
                for (photo in photos) {
                    when (val upload = repository.uploadPhoto(photo)) {
                        is ApiResult.Success -> uploaded += upload.data
                        is ApiResult.Failure -> {
                            updateOutgoing(outgoing.clientUuid) { it.copy(failed = true) }
                            return@launch
                        }
                    }
                }
                uploaded.toList().also { uploadedKeys[outgoing.clientUuid] = it }
            }
            when (val result = repository.send(requestId, outgoing.clientUuid, outgoing.text, outgoing.replyTo?.id, keys)) {
                is ApiResult.Success -> merge(listOf(result.data))
                is ApiResult.Failure -> {
                    // 409: la solicitud terminó y el chat ya no acepta mensajes.
                    val closed = (result.error as? ApiError.Http)?.code == "CHAT_CLOSED"
                    if (closed) _uiState.update { it.copy(canSend = false) }
                    updateOutgoing(outgoing.clientUuid) { it.copy(failed = true) }
                }
            }
        }
    }

    private fun updateOutgoing(clientUuid: String, change: (OutgoingMessage) -> OutgoingMessage) = _uiState.update { state ->
        state.copy(outgoing = state.outgoing.map { if (it.clientUuid == clientUuid) change(it) else it })
    }

    /** Marca como leído lo último de la otra parte, solo con la pantalla a la vista. */
    private fun markRead() {
        if (!visible || markingRead) return
        val state = _uiState.value
        val myId = state.myUserId ?: return
        val last = state.messages.lastOrNull { it.senderId != myId && it.readAt == null } ?: return
        markingRead = true
        viewModelScope.launch {
            val result = repository.markRead(requestId, last.id)
            if (result is ApiResult.Success) {
                val now = Instant.now()
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map {
                            if (it.senderId != myId && it.readAt == null && it.order <= last.order) it.copy(readAt = now) else it
                        },
                    )
                }
            }
            markingRead = false
            // Pudo llegar otro mientras tanto.
            if (result is ApiResult.Success && _uiState.value.messages.any { it.senderId != myId && it.readAt == null }) markRead()
        }
    }

    private companion object {
        /** Tope de páginas al ponerse al día: evita un ciclo largo si algo no cuadra. */
        const val MAX_CATCH_UP_PAGES = 10
    }
}
