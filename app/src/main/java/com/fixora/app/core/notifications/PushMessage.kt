package com.fixora.app.core.notifications

import android.os.Bundle

/**
 * Datos de una notificación push tal como los manda la API en `data` (centro de notificaciones,
 * API-25): `type` (por ejemplo solicitud_aceptada o revision_documento_proveedor) y la entidad a la
 * que se refiere. Con ellos se decide a qué pantalla llevar al tocarla.
 */
data class PushMessage(
    val type: String,
    val entityType: String? = null,
    val entityId: String? = null,
) {
    companion object {
        const val KEY_TYPE = "type"
        const val KEY_ENTITY_TYPE = "entity_type"
        const val KEY_ENTITY_ID = "entity_id"

        /** null si no trae tipo (por ejemplo, una prueba enviada desde la consola de Firebase). */
        fun fromData(data: Map<String, String?>): PushMessage? {
            val type = data[KEY_TYPE]?.takeIf { it.isNotBlank() } ?: return null
            return PushMessage(
                type = type,
                entityType = data[KEY_ENTITY_TYPE]?.takeIf { it.isNotBlank() },
                entityId = data[KEY_ENTITY_ID]?.takeIf { it.isNotBlank() },
            )
        }

        /**
         * Lee el Intent con el que se abrió la app al tocar la notificación: Android pone los datos
         * como extras (en segundo plano lo hace el sistema; en primer plano, PushNotifier).
         */
        fun fromExtras(extras: Bundle?): PushMessage? = extras?.let { bundle ->
            fromData(listOf(KEY_TYPE, KEY_ENTITY_TYPE, KEY_ENTITY_ID).associateWith { bundle.getString(it) })
        }
    }
}
