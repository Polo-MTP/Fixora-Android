package com.fixora.app.feature.notifications.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Centro de notificaciones de la cuenta (API-25). */
interface NotificationsApi {
    /** Las más recientes primero; con unread=true solo las no leídas. */
    @GET("me/notifications")
    suspend fun list(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("unread") unread: Boolean? = null,
    ): NotificationsPageDto

    @POST("me/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String)

    @POST("me/notifications/read-all")
    suspend fun markAllRead()
}

@Serializable
data class NotificationsPageDto(
    val data: List<NotificationDto> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
    val pagination: PaginationDto = PaginationDto(),
)

@Serializable
data class NotificationDto(
    val id: String,
    val type: String = "",
    val title: String = "",
    val message: String = "",
    @SerialName("entity_type") val entityType: String? = null,
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class PaginationDto(
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 0,
)
