package com.fixora.app.feature.chat.data

import com.fixora.app.core.network.DataResponse
import com.fixora.app.feature.chat.domain.ChatMessage
import com.fixora.app.feature.chat.domain.ChatPhoto
import com.fixora.app.feature.chat.domain.ChatReply
import com.fixora.app.feature.servicerequest.domain.parseApiInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Chat por solicitud (API-35). */
interface ChatApi {
    /** Sin cursores, los últimos; `before` para los más viejos y `after` para los que faltan. */
    @GET("service-requests/{id}/chat")
    suspend fun page(
        @Path("id") requestId: String,
        @Query("before") before: String? = null,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int? = null,
    ): DataResponse<ChatPageDto>

    /** Con el mismo client_uuid la API regresa el mensaje original en vez de duplicarlo. */
    @POST("service-requests/{id}/chat/messages")
    suspend fun send(@Path("id") requestId: String, @Body body: SendMessageBody): DataResponse<ChatMessageDto>

    @POST("service-requests/{id}/chat/read")
    suspend fun markRead(@Path("id") requestId: String, @Body body: MarkReadBody)

    @Multipart
    @POST("files/upload")
    suspend fun uploadPhoto(
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part,
    ): DataResponse<ChatUploadDto>
}

@Serializable
data class SendMessageBody(
    @SerialName("client_uuid") val clientUuid: String,
    val message: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("attachment_keys") val attachmentKeys: List<String>? = null,
)

@Serializable
data class MarkReadBody(@SerialName("up_to_message_id") val upToMessageId: String? = null)

@Serializable
data class ChatUploadDto(@SerialName("file_key") val fileKey: String)

@Serializable
data class ChatPageDto(
    val conversation: ConversationDto = ConversationDto(),
    val messages: List<ChatMessageDto> = emptyList(),
    val pagination: PaginationDto = PaginationDto(),
)

@Serializable
data class ConversationDto(
    @SerialName("can_send") val canSend: Boolean = false,
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
data class PaginationDto(
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_before") val nextBefore: String? = null,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("sender_name") val senderName: String? = null,
    val message: String? = null,
    @SerialName("reply_to") val replyTo: ReplyDto? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    @SerialName("client_uuid") val clientUuid: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain() = ChatMessage(
        id = id,
        senderId = senderId,
        senderName = senderName?.trim()?.takeIf { it.isNotEmpty() },
        text = message?.takeIf { it.isNotBlank() },
        replyTo = replyTo?.let { ChatReply(it.id, it.senderId, it.message?.takeIf(String::isNotBlank), it.hasAttachments) },
        // Sin enlace (el archivo ya no existe) no hay qué mostrar.
        photos = attachments.mapNotNull { a -> a.url?.takeIf { it.isNotBlank() }?.let { ChatPhoto(a.id, it) } },
        clientUuid = clientUuid,
        readAt = parseApiInstant(readAt),
        createdAt = parseApiInstant(createdAt),
    )
}

@Serializable
data class ReplyDto(
    val id: String,
    @SerialName("sender_id") val senderId: String = "",
    val message: String? = null,
    @SerialName("has_attachments") val hasAttachments: Boolean = false,
)

@Serializable
data class AttachmentDto(val id: String = "", val url: String? = null)
