package com.fixora.app.navigation

import com.fixora.app.core.notifications.PushMessage
import com.fixora.app.core.notifications.PushNotifier
import com.fixora.app.feature.chat.navigation.ChatRoute
import com.fixora.app.feature.servicerequest.navigation.IncomingRequestRoute
import com.fixora.app.feature.servicerequest.navigation.ServiceRequestRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationNavigationTest {

    @Test
    fun incomingRequestAlarmOpensTheAlarmScreen() {
        val message = PushMessage(type = "nueva_solicitud", entityType = "service_request", entityId = "15", isAlarm = true)

        assertEquals(IncomingRequestRoute("15"), notificationRoute(message))
    }

    @Test
    fun clientStatusChangesAlsoOpenTheRequest() {
        val message = PushMessage(type = "solicitud_aceptada", entityType = "service_request", entityId = "8")

        assertEquals(ServiceRequestRoute("8"), notificationRoute(message))
    }

    @Test
    fun aChatMessageOpensTheChat() {
        val message = PushMessage(type = "chat_message", entityType = "service_request", entityId = "15")

        assertEquals(ChatRoute("15"), notificationRoute(message))
    }

    @Test
    fun otherNotificationsStayOnHome() {
        assertNull(notificationRoute(PushMessage(type = "verificacion_proveedor", entityType = "supplier", entityId = "3")))
        assertNull(notificationRoute(PushMessage(type = "nueva_solicitud", entityType = "service_request", entityId = null)))
        assertNull(notificationRoute(PushMessage(type = "prueba")))
    }

    @Test
    fun sameRequestReplacesItsAlarm() {
        assertEquals(PushNotifier.alarmNotificationId("15"), PushNotifier.alarmNotificationId("15"))
        assertNotEquals(PushNotifier.alarmNotificationId("15"), PushNotifier.alarmNotificationId("16"))
        assertEquals(PushNotifier.alarmNotificationId(null), PushNotifier.alarmNotificationId("no-es-numero"))
    }

    @Test
    fun alarmDataIsReadLikeAnyNotification() {
        val message = PushMessage.fromData(
            mapOf(
                "type" to "nueva_solicitud",
                "entity_type" to "service_request",
                "entity_id" to "15",
                "is_alarm" to "true",
                "title" to "Nueva solicitud de servicio",
            ),
        )

        assertEquals(PushMessage("nueva_solicitud", "service_request", "15", isAlarm = true), message)
    }
}
