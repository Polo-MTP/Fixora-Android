package com.fixora.app.navigation

import com.fixora.app.core.notifications.PushMessage
import com.fixora.app.feature.servicerequest.navigation.ServiceRequestRoute

/**
 * Único punto donde una notificación tocada decide a qué pantalla llevar (ANDROID-6). Devuelve la
 * ruta a abrir encima del inicio, o null para quedarse en el inicio que ya muestra FixoraNavHost.
 *
 * Las de una solicitud de servicio (la alarma de solicitud entrante y los cambios de estado, con
 * entity_type = service_request) abren esa solicitud (ANDROID-13). Las demás se quedan en el inicio.
 */
internal fun notificationRoute(message: PushMessage): Any? = when (message.entityType) {
    SERVICE_REQUEST -> message.entityId?.let(::ServiceRequestRoute)
    else -> null
}

private const val SERVICE_REQUEST = "service_request"
