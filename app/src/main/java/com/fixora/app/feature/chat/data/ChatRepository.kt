package com.fixora.app.feature.chat.data

import androidx.core.net.toUri
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.core.session.HomeArea
import com.fixora.app.core.session.Roles
import com.fixora.app.core.session.SessionTokens
import com.fixora.app.feature.chat.domain.ChatEvent
import com.fixora.app.feature.chat.domain.ChatMessage
import com.fixora.app.feature.chat.domain.ChatPage
import com.fixora.app.feature.chat.ui.ChatPhotoReader
import com.fixora.app.feature.profile.data.PhotoReader
import com.fixora.app.feature.tracking.data.TripSocketFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/** El chat de una solicitud: mensajes por REST y lo nuevo en tiempo real. Interfaz para probar sin red. */
interface ChatRepository {
    /** Los últimos mensajes, o los anteriores a [before], o los posteriores a [after]. */
    suspend fun page(requestId: String, before: String? = null, after: String? = null): ApiResult<ChatPage>

    /** [clientUuid] es el mismo en cada reintento para que la API no lo duplique. */
    suspend fun send(
        requestId: String,
        clientUuid: String,
        text: String?,
        replyToId: String?,
        photoKeys: List<String>,
    ): ApiResult<ChatMessage>

    /** Marca como leídos los mensajes de la otra parte hasta [upToMessageId]. */
    suspend fun markRead(requestId: String, upToMessageId: String): ApiResult<Unit>

    /** Sube una foto ya convertida a JPEG y regresa su llave. */
    suspend fun uploadPhoto(jpeg: ByteArray): ApiResult<String>

    /** Eventos del chat mientras se recolecta; se reconecta solo. */
    fun events(requestId: String): Flow<ChatEvent>

    /** La cuenta con sesión, para saber qué mensajes son propios y con quién se habla. */
    suspend fun me(): ChatMe?
}

data class ChatMe(val userId: String, val isClient: Boolean)

class RemoteChatRepository(
    private val api: ChatApi,
    private val realtime: ChatRealtime,
    private val tokens: SessionTokens,
    private val json: Json,
) : ChatRepository {

    override suspend fun page(requestId: String, before: String?, after: String?) = safeApiCall(json) {
        val data = api.page(requestId, before = before, after = after, limit = PAGE_SIZE).data
        ChatPage(
            canSend = data.conversation.canSend,
            unreadCount = data.conversation.unreadCount,
            messages = data.messages.map { it.toDomain() },
            hasMore = data.pagination.hasMore,
            nextBefore = data.pagination.nextBefore,
        )
    }

    override suspend fun send(
        requestId: String,
        clientUuid: String,
        text: String?,
        replyToId: String?,
        photoKeys: List<String>,
    ) = safeApiCall(json) {
        api.send(
            requestId,
            SendMessageBody(
                clientUuid = clientUuid,
                message = text?.trim()?.takeIf { it.isNotEmpty() },
                replyToId = replyToId,
                attachmentKeys = photoKeys.takeIf { it.isNotEmpty() },
            ),
        ).data.toDomain()
    }

    override suspend fun markRead(requestId: String, upToMessageId: String) = safeApiCall(json) {
        api.markRead(requestId, MarkReadBody(upToMessageId))
    }

    override suspend fun uploadPhoto(jpeg: ByteArray) = safeApiCall(json) {
        val part = MultipartBody.Part.createFormData("file", "chat.jpg", jpeg.toRequestBody(JPEG))
        api.uploadPhoto(PHOTOS, part).data.fileKey
    }

    // Antes de cada conexión se consulta el chat por REST: si el token venció, esa llamada lo
    // renueva, y el socket se autentica con uno vigente.
    override fun events(requestId: String): Flow<ChatEvent> =
        realtime.events(requestId, beforeConnect = { page(requestId, after = null) is ApiResult.Success })

    override suspend fun me(): ChatMe? = tokens.current()?.let {
        ChatMe(it.userId, Roles.homeArea(it.role) == HomeArea.Client)
    }

    private companion object {
        const val PAGE_SIZE = 30
        val JPEG = "image/jpeg".toMediaType()
        val PHOTOS = "photos".toRequestBody("text/plain".toMediaType())
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ChatModule {
    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi = retrofit.create()

    @Provides
    fun provideChatRealtime(sockets: TripSocketFactory, tokens: SessionTokens, json: Json): ChatRealtime =
        ChatRealtime(sockets, tokens, json)

    @Provides
    fun provideChatPhotoReader(reader: PhotoReader): ChatPhotoReader = ChatPhotoReader { reader.readAsJpeg(it.toUri()) }

    @Provides
    fun provideChatRepository(api: ChatApi, realtime: ChatRealtime, tokens: SessionTokens, json: Json): ChatRepository =
        RemoteChatRepository(api, realtime, tokens, json)
}
