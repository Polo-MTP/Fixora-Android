package com.fixora.app.core.session

import com.fixora.app.core.network.safeApiCall
import com.fixora.app.core.notifications.DeviceTokenRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionStore: SessionStore,
    private val sessionApi: SessionApi,
    private val deviceTokenRegistrar: DeviceTokenRegistrar,
    private val json: Json,
) {
    // Vive lo mismo que la app: el aviso a la API sigue aunque la pantalla que cerró sesión ya
    // no exista.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val session: Flow<Session?> = sessionStore.session

    /**
     * Cierra la sesión en el celular, aunque no haya internet. Primero desactiva el token de
     * notificaciones (la API lo pide con sesión; espera como mucho unos segundos). A la API se le
     * avisa en segundo plano para que revoque el refresh token; si falla, ese token vence solo.
     */
    suspend fun logout() {
        val session = sessionStore.current() ?: return
        deviceTokenRegistrar.unregister()
        sessionStore.clear()
        scope.launch {
            safeApiCall(json) { sessionApi.logout(RefreshTokenRequest(session.refreshToken)) }
        }
    }
}
