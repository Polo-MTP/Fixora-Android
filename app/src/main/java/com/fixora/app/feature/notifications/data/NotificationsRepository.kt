package com.fixora.app.feature.notifications.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.notifications.domain.AppNotification
import com.fixora.app.feature.notifications.domain.NotificationsPage
import com.fixora.app.feature.servicerequest.domain.parseApiInstant
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Inject
import javax.inject.Singleton

/** Centro de notificaciones y el número de no leídas para la campana de los inicios. */
interface NotificationsRepository {
    /** No leídas; lo usa la campana y se actualiza con cada consulta. */
    val unreadCount: StateFlow<Int>

    suspend fun page(page: Int): ApiResult<NotificationsPage>

    /** Solo vuelve a contar las no leídas (al llegar un push o volver al inicio). */
    suspend fun refreshUnread(): ApiResult<Unit>

    suspend fun markRead(id: String): ApiResult<Unit>

    suspend fun markAllRead(): ApiResult<Unit>
}

@Singleton
class RemoteNotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
    private val json: Json,
) : NotificationsRepository {

    private val _unreadCount = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    override suspend fun page(page: Int) = safeApiCall(json) {
        val response = api.list(page = page, limit = PAGE_SIZE)
        _unreadCount.value = response.unreadCount
        NotificationsPage(
            items = response.data.map { it.toDomain() },
            unreadCount = response.unreadCount,
            page = page,
            hasMore = page < response.pagination.totalPages,
        )
    }

    override suspend fun refreshUnread() = safeApiCall(json) {
        _unreadCount.value = api.list(page = 1, limit = 1, unread = true).unreadCount
    }

    override suspend fun markRead(id: String) = safeApiCall(json) {
        api.markRead(id)
        _unreadCount.update { (it - 1).coerceAtLeast(0) }
    }

    override suspend fun markAllRead() = safeApiCall(json) {
        api.markAllRead()
        _unreadCount.value = 0
    }

    private fun NotificationDto.toDomain() = AppNotification(
        id = id,
        type = type,
        title = title.trim(),
        message = message.trim(),
        entityType = entityType,
        entityId = entityId,
        isRead = readAt != null,
        createdAt = parseApiInstant(createdAt),
    )

    companion object {
        const val PAGE_SIZE = 20
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    abstract fun bindNotificationsRepository(impl: RemoteNotificationsRepository): NotificationsRepository

    companion object {
        @Provides
        @Singleton
        fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create()
    }
}
