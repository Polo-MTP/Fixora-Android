package com.fixora.app.core.notifications

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Mantiene el token de FCM de este celular ligado a la sesión (ANDROID-6 con API-16): lo registra
 * al iniciar sesión, al abrir la app con sesión y cuando FCM lo renueva; al cerrar sesión lo
 * desactiva en la API y lo borra del celular, para que no le lleguen avisos de esa cuenta.
 *
 * @param userId usuario con sesión, o null sin sesión (viene de SessionStore).
 */
class DeviceTokenRegistrar(
    private val api: DeviceTokenApi,
    private val tokens: PushTokenProvider,
    private val userId: Flow<String?>,
    private val json: Json,
    private val scope: CoroutineScope,
) {
    // La primera vez que se genera el token llegan juntos el inicio de sesión y onNewToken: sin
    // esto se registraba dos veces al mismo tiempo y la API guardaba el token repetido.
    private val mutex = Mutex()

    /** Usuario y token que la API ya tiene registrados, para no mandarlos otra vez. */
    private var registered: Pair<String, String>? = null

    /** Empieza a escuchar la sesión. Se llama una vez, al arrancar la app. */
    fun start() {
        scope.launch {
            // Cada inicio de sesión (o cambio de cuenta) vuelve a registrar.
            userId.distinctUntilChanged().filterNotNull().collect { register() }
        }
    }

    /** FCM generó un token nuevo: solo se registra si hay sesión. */
    suspend fun onNewToken(token: String) {
        if (userId.first() != null) register(token)
    }

    /**
     * Se llama al cerrar sesión, ANTES de borrarla: la API pide el token de acceso para desactivar.
     * Espera a la API como mucho [DEACTIVATE_TIMEOUT_MS] para no frenar el cierre de sesión; si no
     * contesta (sin internet), borrar el token en el celular basta para que deje de recibir avisos.
     */
    suspend fun unregister() {
        mutex.withLock { registered = null }
        val token = tokens.currentToken() ?: return
        withTimeoutOrNull(DEACTIVATE_TIMEOUT_MS) {
            safeApiCall(json) { api.deactivate(DeviceTokenRequest(token)) }
        }
        scope.launch { tokens.deleteToken() }
    }

    /**
     * Registra el token del usuario con sesión, una sola vez por usuario y token. null si no hay
     * sesión o no hay token (Firebase sin configurar o sin conexión con FCM).
     */
    internal suspend fun register(token: String? = null): ApiResult<Unit>? = mutex.withLock {
        val user = userId.first() ?: return null
        val current = token ?: tokens.currentToken() ?: return null
        if (registered == user to current) return ApiResult.Success(Unit)
        val result = safeApiCall(json) { api.register(RegisterDeviceRequest(current, PLATFORM)) }
        if (result is ApiResult.Success) registered = user to current
        result
    }

    companion object {
        const val PLATFORM = "android"
        const val DEACTIVATE_TIMEOUT_MS = 3_000L
    }
}
