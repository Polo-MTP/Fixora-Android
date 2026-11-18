package com.fixora.app.core.session

import com.fixora.app.core.network.safeApiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionStore: SessionStore,
    private val sessionApi: SessionApi,
    private val logoutListeners: Set<@JvmSuppressWildcards LogoutListener>,
    private val json: Json,
) {
    // Vive lo mismo que la app: el aviso a la API sigue aunque la pantalla que cerró sesión ya
    // no exista.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val session: Flow<Session?> = sessionStore.session

    /**
     * Cierra la sesión en el celular, aunque no haya internet. Primero corre los [LogoutListener]
     * (desactivar el token de notificaciones, ponerse fuera de línea), que la API pide con sesión;
     * van a la vez y cada uno espera como mucho unos segundos. A la API se le avisa en segundo plano
     * para que revoque el refresh token; si falla, ese token vence solo.
     */
    suspend fun logout() {
        val session = sessionStore.current() ?: return
        coroutineScope { logoutListeners.map { async { it.beforeLogout() } }.awaitAll() }
        sessionStore.clear()
        scope.launch {
            safeApiCall(json) { sessionApi.logout(RefreshTokenRequest(session.refreshToken)) }
        }
    }
}
