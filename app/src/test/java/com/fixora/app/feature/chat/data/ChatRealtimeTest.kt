package com.fixora.app.feature.chat.data

import com.fixora.app.core.session.Session
import com.fixora.app.core.session.SessionTokens
import com.fixora.app.feature.chat.domain.ChatEvent
import com.fixora.app.feature.tracking.data.TripSocket
import com.fixora.app.feature.tracking.data.TripSocketFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRealtimeTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private class FakeSocket : TripSocket {
        private val channel = Channel<String>(Channel.UNLIMITED)
        override val incoming: ReceiveChannel<String> = channel
        val sent = mutableListOf<String>()
        var closed = false
        fun receive(text: String) = channel.trySend(text)
        fun drop() = channel.close()
        override fun send(text: String): Boolean {
            if (closed) return false
            sent += text
            return true
        }
        override fun close() {
            closed = true
            channel.close()
        }
    }

    private val sockets = mutableListOf<FakeSocket>()
    private val factory = TripSocketFactory { FakeSocket().also { sockets += it } }
    private val tokens = object : SessionTokens {
        override suspend fun current() = Session("29", "cliente@fixora.test", "cliente", "activo", "token-1", "refresh")
        override suspend fun updateTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun clear() = Unit
    }

    private fun FakeSocket.accept() {
        receive("""{"type":"authenticated","user":{"id":"29","role":"cliente"}}""")
        receive("""{"type":"subscribed","room":"chats:15"}""")
    }

    @Test
    fun joinsTheChatRoomAndDeliversMessagesAndReads() = runTest {
        var checks = 0
        val events = mutableListOf<ChatEvent>()
        val job = launch {
            ChatRealtime(factory, tokens, json).events("15", beforeConnect = { checks++; true }).collect { events += it }
        }
        runCurrent()
        val socket = sockets.single()
        socket.accept()
        runCurrent()

        assertEquals(1, checks)
        assertTrue(socket.sent[0].contains(""""type":"authenticate"""") && socket.sent[0].contains("token-1"))
        assertEquals("""{"type":"subscribe_chat","requestId":"15"}""", socket.sent[1])
        assertEquals(ChatEvent.Connected, events.single())

        socket.receive(
            """{"type":"event","event":"chat_message","channel":"chats:15","data":{"id":"40","sender_id":"9",
            "sender_name":"Juan","message":"Voy en camino","attachments":[],"client_uuid":"u-1","read_at":null,
            "created_at":"2026-11-23T18:00:00.000Z"}}""",
        )
        socket.receive("""{"type":"event","event":"chat_messages_read","data":{"messageIds":["38","39"],"readAt":"2026-11-23T18:01:00.000Z"}}""")
        socket.receive("""{"type":"event","event":"technician_location","data":{}}""")
        runCurrent()

        val message = (events[1] as ChatEvent.Message).message
        assertEquals("40", message.id)
        assertEquals("Voy en camino", message.text)
        assertEquals(ChatEvent.Read(listOf("38", "39"), Instant.parse("2026-11-23T18:01:00Z")), events[2])
        assertEquals(3, events.size)
        job.cancel()
    }

    @Test
    fun whenTheConnectionDropsItReconnectsAndSaysSo() = runTest {
        val events = mutableListOf<ChatEvent>()
        val job = launch {
            ChatRealtime(factory, tokens, json).events("15", beforeConnect = { true }).collect { events += it }
        }
        runCurrent()
        sockets[0].accept()
        runCurrent()
        sockets[0].drop()
        runCurrent()
        assertEquals(listOf(ChatEvent.Connected, ChatEvent.Disconnected), events)

        advanceTimeBy(ChatRealtime.MIN_BACKOFF_MS + 1)
        runCurrent()
        sockets[1].accept()
        runCurrent()
        assertEquals(ChatEvent.Connected, events.last())
        assertTrue(sockets[0].closed)
        job.cancel()
    }

    @Test
    fun withoutAccessToTheChatItRetriesLater() = runTest {
        val events = mutableListOf<ChatEvent>()
        val job = launch {
            ChatRealtime(factory, tokens, json).events("15", beforeConnect = { true }).collect { events += it }
        }
        runCurrent()
        sockets[0].receive("""{"type":"authenticated"}""")
        sockets[0].receive("""{"type":"error","code":"FORBIDDEN_ROOM"}""")
        runCurrent()

        assertTrue(events.isEmpty())
        assertTrue(sockets[0].closed)
        advanceTimeBy(ChatRealtime.MIN_BACKOFF_MS + 1)
        runCurrent()
        assertEquals(2, sockets.size)
        job.cancel()
    }
}
