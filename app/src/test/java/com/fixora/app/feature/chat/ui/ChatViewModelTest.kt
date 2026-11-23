package com.fixora.app.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.chat.data.ChatMe
import com.fixora.app.feature.chat.data.ChatRepository
import com.fixora.app.feature.chat.domain.ChatEvent
import com.fixora.app.feature.chat.domain.ChatMessage
import com.fixora.app.feature.chat.domain.ChatPage
import com.fixora.app.feature.servicerequest.data.ServiceRequestRepository
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val scheduler = TestCoroutineScheduler()

    private fun message(id: String, sender: String, text: String? = "Hola", uuid: String? = null, readAt: Instant? = null) =
        ChatMessage(id, sender, if (sender == ME) "Carla" else "Juan", text, null, emptyList(), uuid, readAt, Instant.parse("2026-11-23T18:00:00Z"))

    private inner class FakeChat : ChatRepository {
        var latest = listOf<ChatMessage>()
        val afterPages = mutableMapOf<String, List<ChatMessage>>()
        var canSend = true
        val sent = mutableListOf<Triple<String, String?, String?>>()
        val sentKeys = mutableListOf<List<String>>()
        var sendError: ApiError? = null
        val uploads = mutableListOf<ByteArray>()
        val reads = mutableListOf<String>()
        var events = Channel<ChatEvent>(Channel.UNLIMITED)
        var subscriptions = 0
        private var nextId = 100

        override suspend fun page(requestId: String, before: String?, after: String?): ApiResult<ChatPage> {
            val messages = if (after != null) afterPages[after].orEmpty() else latest
            return ApiResult.Success(ChatPage(canSend, 0, messages, hasMore = false, nextBefore = null))
        }

        override suspend fun send(requestId: String, clientUuid: String, text: String?, replyToId: String?, photoKeys: List<String>): ApiResult<ChatMessage> {
            sent += Triple(clientUuid, text, replyToId)
            sentKeys += photoKeys
            sendError?.let { return ApiResult.Failure(it) }
            return ApiResult.Success(message((nextId++).toString(), ME, text, clientUuid))
        }

        override suspend fun markRead(requestId: String, upToMessageId: String): ApiResult<Unit> {
            reads += upToMessageId
            return ApiResult.Success(Unit)
        }

        override suspend fun uploadPhoto(jpeg: ByteArray): ApiResult<String> {
            uploads += jpeg
            return ApiResult.Success("photos/29/${uploads.size}.jpg")
        }

        override fun events(requestId: String): Flow<ChatEvent> {
            subscriptions++
            return events.receiveAsFlow()
        }

        override suspend fun me() = ChatMe(ME, isClient = true)
    }

    private val chat = FakeChat()
    private val requests = ServiceRequestRepository { id ->
        ApiResult.Success(
            ServiceRequest(
                id = id, status = RequestStatus.Arrived, isEmergency = false, description = "", categoryName = "Plomería",
                clientName = "Carla", clientRating = 0.0, zone = "", basePrice = null, photoUrls = emptyList(),
                createdAt = null, expiresAt = null, scheduledAt = null, technicianName = "Juan Hernández",
            ),
        )
    }
    private var unreadablePhoto = false
    private val photos = ChatPhotoReader { uri -> if (unreadablePhoto) null else uri.toByteArray() }
    private lateinit var viewModel: ChatViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create() {
        viewModel = ChatViewModel(SavedStateHandle(mapOf("requestId" to "15")), chat, requests, photos)
        scheduler.runCurrent()
    }

    @Test
    fun showsTheConversationAndMarksAsReadOnlyWhileVisible() {
        chat.latest = listOf(message("40", ME, readAt = Instant.EPOCH), message("41", OTHER))
        create()

        assertEquals("Juan Hernández", state.otherName)
        assertEquals(listOf("40", "41"), state.messages.map { it.id })
        assertTrue(state.isMine(state.messages[0]))
        assertEquals("40", state.lastReadMineId)
        assertTrue(chat.reads.isEmpty())

        viewModel.onStart()
        scheduler.runCurrent()
        assertEquals(listOf("41"), chat.reads)
        assertEquals(1, chat.subscriptions)
    }

    @Test
    fun aSentMessageShowsAtOnceAndDoesNotRepeatWhenItAlsoArrivesByTheSocket() {
        create()
        viewModel.onStart()
        viewModel.onDraftChange("  Ya llegué  ")
        viewModel.send()
        assertEquals("Ya llegué", state.outgoing.single().text)
        assertEquals("", state.draft)

        scheduler.runCurrent()
        val confirmed = state.messages.single()
        assertTrue(state.outgoing.isEmpty())

        chat.events.trySend(ChatEvent.Message(confirmed))
        scheduler.runCurrent()
        assertEquals(1, state.messages.size)
    }

    @Test
    fun aFailedMessageRetriesWithTheSameKeyWithoutUploadingThePhotosAgain() {
        create()
        chat.sendError = ApiError.NoConnection
        viewModel.onPhotosPicked(listOf("content://foto/1", "content://foto/2"))
        viewModel.send()
        scheduler.runCurrent()
        assertTrue(state.outgoing.single().failed)
        assertEquals(2, chat.uploads.size)

        chat.sendError = null
        viewModel.retrySend(state.outgoing.single().clientUuid)
        scheduler.runCurrent()

        assertEquals(2, chat.uploads.size)
        assertEquals(chat.sent[0].first, chat.sent[1].first)
        assertEquals(listOf("photos/29/1.jpg", "photos/29/2.jpg"), chat.sentKeys[1])
        assertTrue(state.outgoing.isEmpty())
    }

    @Test
    fun anUnreadablePhotoIsNotSent() {
        create()
        unreadablePhoto = true
        viewModel.onPhotosPicked(listOf("content://foto/rota"))
        viewModel.send()
        scheduler.runCurrent()

        assertTrue(state.photoError)
        assertTrue(state.outgoing.single().failed)
        assertTrue(chat.sent.isEmpty())
    }

    @Test
    fun replyingSendsTheOriginalMessage() {
        chat.latest = listOf(message("41", OTHER, "¿Dónde está la llave?"))
        create()
        viewModel.onReply(state.messages.single())
        viewModel.onDraftChange("Debajo del lavabo")
        viewModel.send()
        scheduler.runCurrent()

        assertEquals("41", chat.sent.single().third)
        assertNull(state.replyTo)
    }

    @Test
    fun afterReconnectingItAsksOnlyForWhatItMissed() {
        chat.latest = listOf(message("40", OTHER))
        create()
        viewModel.onStart()
        scheduler.runCurrent()

        chat.afterPages["40"] = listOf(message("41", OTHER, "¿Ya vienes?"), message("42", OTHER, "Te espero"))
        chat.events.trySend(ChatEvent.Disconnected)
        scheduler.runCurrent()
        assertTrue(state.reconnecting)
        chat.events.trySend(ChatEvent.Connected)
        // El mismo mensaje también llega por el socket: no se repite.
        chat.events.trySend(ChatEvent.Message(message("42", OTHER, "Te espero")))
        scheduler.runCurrent()

        assertFalse(state.reconnecting)
        assertEquals(listOf("40", "41", "42"), state.messages.map { it.id })
        assertEquals("42", chat.reads.last())
    }

    @Test
    fun whenTheOtherSideReadsMyMessagesTheyShowAsRead() {
        chat.latest = listOf(message("40", ME), message("41", ME))
        create()
        viewModel.onStart()
        scheduler.runCurrent()
        assertNull(state.lastReadMineId)

        chat.events.trySend(ChatEvent.Read(listOf("40", "41"), Instant.parse("2026-11-23T18:05:00Z")))
        scheduler.runCurrent()
        assertEquals("41", state.lastReadMineId)
    }

    @Test
    fun inTheBackgroundItStopsListening() {
        create()
        viewModel.onStart()
        scheduler.runCurrent()
        viewModel.onStop()
        chat.events.trySend(ChatEvent.Message(message("50", OTHER)))
        scheduler.runCurrent()
        assertTrue(state.messages.isEmpty())

        // Al volver se suscribe otra vez.
        viewModel.onStart()
        scheduler.runCurrent()
        assertEquals(2, chat.subscriptions)
    }

    @Test
    fun aFinishedRequestIsReadOnly() {
        chat.canSend = false
        create()
        viewModel.onDraftChange("Hola")
        assertFalse(state.canSubmit)
        viewModel.send()
        assertTrue(state.outgoing.isEmpty())
    }

    private companion object {
        const val ME = "29"
        const val OTHER = "9"
    }
}
