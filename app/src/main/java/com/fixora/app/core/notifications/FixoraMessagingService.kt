package com.fixora.app.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recibe los mensajes de FCM. Con la app en segundo plano Android muestra solo las notificaciones
 * (con el canal y el ícono por defecto del AndroidManifest); con la app abierta llegan aquí y se
 * muestran con PushNotifier.
 */
@AndroidEntryPoint
class FixoraMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registrar: DeviceTokenRegistrar

    @Inject lateinit var notifier: PushNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { registrar.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Solicitud entrante (ANDROID-13): la API la manda solo con datos para que llegue aquí
        // aunque la app esté cerrada y se muestre como alarma a pantalla completa.
        if (message.data[PushNotifier.KEY_IS_ALARM] == "true") {
            val title = message.data["title"] ?: return
            notifier.showAlarm(title, message.data["body"].orEmpty(), message.data)
            return
        }
        // Los mensajes solo de datos (sin título) no muestran nada por ahora.
        val notification = message.notification
        val title = notification?.title ?: message.data["title"] ?: return
        val body = notification?.body ?: message.data["body"].orEmpty()
        notifier.show(title, body, message.data, notification?.channelId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
