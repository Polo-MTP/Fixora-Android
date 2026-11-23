package com.fixora.app.navigation

import com.fixora.app.core.notifications.PushMessage
import com.fixora.app.feature.chat.navigation.ChatRoute
import com.fixora.app.feature.servicerequest.navigation.IncomingRequestRoute
import com.fixora.app.feature.servicerequest.navigation.ServiceRequestRoute

/**
 * Único punto donde una notificación tocada decide a qué pantalla llevar (ANDROID-6). Devuelve la
 * ruta a abrir encima del inicio, o null para quedarse en el inicio que ya muestra FixoraNavHost.
 *
 * Las de una solicitud de servicio (la alarma de solicitud entrante y los cambios de estado, con
 * entity_type = service_request) abren esa solicitud (ANDROID-13). Las demás se quedan en el inicio.
 */
internal fun notificationRoute(message: PushMessage): Any? = when (message.entityType) {
    SERVICE_REQUEST -> message.entityId?.let { id ->
        // La alarma de solicitud entrante abre la pantalla para responderla; un mensaje, el chat
        // (ANDROID-23); las demás, el detalle.
        when {
            message.isAlarm -> IncomingRequestRoute(id)
            message.type == CHAT_MESSAGE -> ChatRoute(id)
            else -> ServiceRequestRoute(id)
        }
    }
    else -> null
}

private const val SERVICE_REQUEST = "service_request"
private const val CHAT_MESSAGE = "chat_message"
