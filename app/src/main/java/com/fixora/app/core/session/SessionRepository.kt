package com.fixora.app.core.session

import com.fixora.app.core.network.safeApiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _isLoggingOut = MutableStateFlow(false)

    /** Mientras se cierra la sesión: la app tapa la pantalla con «Cerrando sesión…» (ANDROID-44). */
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    /**
     * Cierra la sesión en el celular, aunque no haya internet. Primero corre los [LogoutListener]
     * (desactivar el token de notificaciones, ponerse fuera de línea), que la API pide con sesión;
     * van a la vez y cada uno espera como mucho unos segundos. A la API se le avisa en segundo plano
     * para que revoque el refresh token; si falla, ese token vence solo.
     */
    suspend fun logout() {
        val session = sessionStore.current() ?: return
        if (_isLoggingOut.value) return
        // En el scope de la app: la pantalla que pidió cerrar sesión desaparece a la mitad y su
        // viewModelScope se cancela, pero el aviso de «Cerrando sesión…» debe terminar con calma.
        scope.launch {
            _isLoggingOut.value = true
            try {
                val started = System.currentTimeMillis()
                coroutineScope { logoutListeners.map { async { it.beforeLogout() } }.awaitAll() }
                sessionStore.clear()
                // Que el aviso se alcance a ver y la landing termine de armarse debajo.
                delay((MIN_LOGOUT_SCREEN_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            } finally {
                _isLoggingOut.value = false
            }
            safeApiCall(json) { sessionApi.logout(RefreshTokenRequest(session.refreshToken)) }
        }.join()
    }

    private companion object {
        /** Lo mínimo que se muestra «Cerrando sesión…», aunque los avisos terminen antes. */
        const val MIN_LOGOUT_SCREEN_MS = 900L
    }
}
