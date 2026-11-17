package com.fixora.app.core.notifications

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

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

/** Espera una Task de Google Play Services; null si falla o se cancela. */
private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        continuation.resume(if (task.isSuccessful) task.result else null)
    }
}
