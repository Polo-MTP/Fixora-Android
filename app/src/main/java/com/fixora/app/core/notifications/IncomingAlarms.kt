package com.fixora.app.core.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarmas de solicitud entrante que llegan con la app a la vista (ANDROID-13): en lugar de solo
 * una notificación arriba, la app abre la pantalla de alarma. FixoraNavHost las escucha.
 */
@Singleton
class IncomingAlarms @Inject constructor() {
    private val alarms = MutableSharedFlow<PushMessage>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val incoming: SharedFlow<PushMessage> = alarms.asSharedFlow()

    /** true si había alguien escuchando (la app a la vista); si no, hay que mostrar notificación. */
    fun offer(message: PushMessage): Boolean {
        if (!AppVisibility.isVisible || alarms.subscriptionCount.value == 0) return false
        return alarms.tryEmit(message)
    }
}

/** Si la app está a la vista; lo actualiza MainActivity en onStart y onStop. */
object AppVisibility {
    @Volatile
    var isVisible: Boolean = false
}
