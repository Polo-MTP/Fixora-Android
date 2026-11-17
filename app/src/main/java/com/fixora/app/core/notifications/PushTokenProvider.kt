package com.fixora.app.core.notifications

import android.content.Context
import com.fixora.app.core.gms.awaitOrNull
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Token de FCM de este celular. Interfaz para poder probar DeviceTokenRegistrar sin Firebase. */
interface PushTokenProvider {
    /** null si Firebase no está configurado o no se pudo obtener (por ejemplo, sin internet). */
    suspend fun currentToken(): String?

    /** Invalida el token: FCM deja de entregarle mensajes y la próxima vez se genera otro. */
    suspend fun deleteToken()
}

@Singleton
class FirebasePushTokenProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PushTokenProvider {

    override suspend fun currentToken(): String? {
        if (!PushSetup.isEnabled(context)) return null
        return FirebaseMessaging.getInstance().token.awaitOrNull()
    }

    override suspend fun deleteToken() {
        if (!PushSetup.isEnabled(context)) return
        FirebaseMessaging.getInstance().deleteToken().awaitOrNull()
    }
}
