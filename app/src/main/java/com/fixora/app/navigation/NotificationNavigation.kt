package com.fixora.app.navigation

import com.fixora.app.core.notifications.PushMessage

/**
 * Único punto donde una notificación tocada decide a qué pantalla llevar (ANDROID-6). Devuelve la
 * ruta a abrir encima del inicio, o null para quedarse en el inicio que ya muestra FixoraNavHost.
 *
 * Por ahora todas se quedan en el inicio. Los tipos que manda la API están en
 * PushMessage (por ejemplo solicitud_aceptada, con entity_type = service_request y su entity_id).
 */
internal fun notificationRoute(message: PushMessage): Any? = when (message.entityType) {
    // TODO(Sprint 4, «Alarma de solicitud entrante»): "service_request" -> ruta de la solicitud
    //  message.entityId, cuando exista esa pantalla.
    else -> null
}
