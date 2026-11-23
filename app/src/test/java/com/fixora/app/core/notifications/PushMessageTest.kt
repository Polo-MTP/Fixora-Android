package com.fixora.app.core.notifications

import com.fixora.app.navigation.notificationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushMessageTest {

    @Test
    fun readsTypeAndEntityFromTheApiData() {
        assertEquals(
            PushMessage("solicitud_aceptada", "service_request", "15"),
            PushMessage.fromData(mapOf("type" to "solicitud_aceptada", "entity_type" to "service_request", "entity_id" to "15")),
        )
    }

    @Test
    fun entityIsOptional() {
        assertEquals(PushMessage("reporte"), PushMessage.fromData(mapOf("type" to "reporte", "entity_id" to "")))
    }

    @Test
    fun withoutTypeItIsNotAFixoraNotification() {
        // Así llega una prueba enviada desde la consola de Firebase sin datos personalizados.
        assertNull(PushMessage.fromData(mapOf("google.message_id" to "0:123")))
        assertNull(PushMessage.fromData(mapOf("type" to " ")))
    }

    @Test
    fun onlyServiceRequestsLeaveHome() {
        // Las de una solicitud abren esa solicitud (ANDROID-13; ver NotificationNavigationTest).
        assertNull(notificationRoute(PushMessage("revision_documento_proveedor")))
    }
}
