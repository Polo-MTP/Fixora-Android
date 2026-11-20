package com.fixora.app.feature.notifications.domain

import com.fixora.app.core.notifications.PushMessage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Una notificación del centro de notificaciones (ANDROID-14). */
data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val entityType: String?,
    val entityId: String?,
    val isRead: Boolean,
    val createdAt: Instant?,
) {
    /** Se abre igual que si se tocara la notificación del celular. */
    fun asPushMessage() = PushMessage(type = type, entityType = entityType, entityId = entityId)

    val kind: NotificationKind
        get() = when {
            entityType == "service_request" -> NotificationKind.Service
            type.contains("proveedor") || type.contains("documento") -> NotificationKind.Account
            type.contains("reporte") -> NotificationKind.Report
            else -> NotificationKind.Other
        }
}

enum class NotificationKind { Service, Account, Report, Other }

data class NotificationsPage(
    val items: List<AppNotification>,
    val unreadCount: Int,
    val page: Int,
    val hasMore: Boolean,
)

/** Cuándo llegó, como se dice en México: «Ahora», «Hace 5 min», «Hace 3 h», «Ayer», «12 nov». */
sealed interface WhenLabel {
    data object Now : WhenLabel
    data class Minutes(val value: Long) : WhenLabel
    data class Hours(val value: Long) : WhenLabel
    data object Yesterday : WhenLabel
    data class Date(val date: LocalDate) : WhenLabel

    companion object {
        fun of(createdAt: Instant, now: Instant, zone: ZoneId): WhenLabel {
            val elapsed = Duration.between(createdAt, now).coerceAtLeast(Duration.ZERO)
            val today = now.atZone(zone).toLocalDate()
            val day = createdAt.atZone(zone).toLocalDate()
            return when {
                elapsed.toMinutes() < 1 -> Now
                elapsed.toMinutes() < 60 -> Minutes(elapsed.toMinutes())
                day == today -> Hours(elapsed.toHours())
                day == today.minusDays(1) -> Yesterday
                else -> Date(day)
            }
        }
    }
}
