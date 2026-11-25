package com.fixora.app.feature.chat.data

import com.fixora.app.core.session.SessionTokens
import com.fixora.app.feature.chat.domain.ChatEvent
import com.fixora.app.feature.servicerequest.domain.parseApiInstant
import com.fixora.app.feature.tracking.data.TripSocket
import com.fixora.app.feature.tracking.data.TripSocketFactory
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Tiempo real del chat (ANDROID-23) por el WebSocket de la API: se autentica, entra a la sala
 * `chats:<id>` y entrega los mensajes nuevos y las marcas de leído.
 *
 * - Si la conexión se cae (sin señal, cambio de red, token vencido) se reconecta sola, esperando
 *   cada vez más, y avisa [ChatEvent.Connected] para que se pida lo que llegó mientras tanto.
 * - [beforeConnect] se llama antes de cada conexión; con false (sin red) espera y reintenta.
 */
class ChatRealtime(
    private val sockets: TripSocketFactory,
    private val tokens: SessionTokens,
    private val json: Json,
) {

    fun events(requestId: String, beforeConnect: suspend () -> Boolean): Flow<ChatEvent> = channelFlow {
        var backoff = MIN_BACKOFF_MS
        while (true) {
            if (beforeConnect()) {
                val token = tokens.current()?.accessToken ?: return@channelFlow
                val socket = sockets.open()
                try {
                    if (join(socket, token, requestId)) {
                        backoff = MIN_BACKOFF_MS
                        send(ChatEvent.Connected)
                        listen(socket)
                        send(ChatEvent.Disconnected)
                    }
                } finally {
                    socket.close()
                }
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** Se autentica y entra a la sala del chat; false si algo falla en el camino. */
    private suspend fun join(socket: TripSocket, token: String, requestId: String): Boolean {
        if (!socket.send(message("authenticate") { put("token", token) })) return false
        if (awaitType(socket, "authenticated") == null) return false
        if (!socket.send(message("subscribe_chat") { put("requestId", requestId) })) return false
        return awaitType(socket, "subscribed") != null
    }

    /** Espera la respuesta [type]; un error o que se cierre la conexión es null. */
    private suspend fun awaitType(socket: TripSocket, type: String): JsonObject? = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) {
        for (text in socket.incoming) {
            val obj = parse(text) ?: continue
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                type -> return@withTimeoutOrNull obj
                "error" -> return@withTimeoutOrNull null
            }
        }
        null
    }

    private suspend fun ProducerScope<ChatEvent>.listen(socket: TripSocket) {
        for (text in socket.incoming) {
            val obj = parse(text) ?: continue
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "event") continue
            val data = obj["data"] as? JsonObject ?: continue
            val event = when (obj["event"]?.jsonPrimitive?.contentOrNull) {
                "chat_message" -> runCatching { json.decodeFromJsonElement<ChatMessageDto>(data).toDomain() }
                    .getOrNull()?.let { ChatEvent.Message(it) }
                "chat_messages_read" -> ChatEvent.Read(
                    messageIds = runCatching { data["messageIds"]!!.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrDefault(emptyList()),
                    readAt = parseApiInstant(data["readAt"]?.jsonPrimitive?.contentOrNull),
                )
                else -> null
            }
            event?.let { send(it) }
        }
    }

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun message(type: String, fill: JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put("type", type)
            fill()
        }.toString()

    companion object {
        const val MIN_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
    }
}
