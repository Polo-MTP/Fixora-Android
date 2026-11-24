package com.fixora.app.feature.chat.domain

import java.time.Instant

/** Mensaje al que se responde, tal como la API lo resume. */
data class ChatReply(
    val id: String,
    val senderId: String,
    val text: String?,
    val hasPhotos: Boolean,
)

data class ChatPhoto(val id: String, val url: String)

/** Mensaje del chat de una solicitud (API-35). */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String?,
    val text: String?,
    val replyTo: ChatReply?,
    val photos: List<ChatPhoto>,
    /** La llave con la que la app lo mandó; sirve para reconocer el propio al llegar por el socket. */
    val clientUuid: String?,
    /** Cuándo lo leyó la otra parte; null si aún no. */
    val readAt: Instant?,
    val createdAt: Instant?,
) {
    /** Los ids son consecutivos en la API: ordenan los mensajes aunque lleguen desordenados. */
    val order: Long get() = id.toLongOrNull() ?: Long.MAX_VALUE
}

data class ChatPage(
    val canSend: Boolean,
    val unreadCount: Int,
    /** En orden cronológico. */
    val messages: List<ChatMessage>,
    val hasMore: Boolean,
    /** Para pedir los mensajes más viejos. */
    val nextBefore: String?,
)

/** Lo que llega por el WebSocket mientras el chat está abierto. */
sealed interface ChatEvent {
    /** Se conectó (o reconectó): hay que pedir lo que llegó mientras no hubo conexión. */
    data object Connected : ChatEvent

    /** Se cayó la conexión; se reintenta sola. */
    data object Disconnected : ChatEvent

    data class Message(val message: ChatMessage) : ChatEvent

    /** La otra parte leyó estos mensajes. */
    data class Read(val messageIds: List<String>, val readAt: Instant?) : ChatEvent
}

object ChatRules {
    /** Límites de la API: texto de hasta 2000 caracteres y hasta 5 fotos por mensaje. */
    const val MAX_TEXT = 2000
    const val MAX_PHOTOS = 5
}
