package com.fixora.app.feature.auth.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recuperar la contraseña sin sesión (API-9): la API manda un código de 6 dígitos al correo, que
 * vale 10 minutos y admite 5 intentos; con él se pone la contraseña nueva y se cierran las
 * sesiones abiertas de la cuenta.
 */
@Singleton
class PasswordRecoveryRepository @Inject constructor(
    private val authApi: AuthApi,
    private val json: Json,
) {
    /**
     * Pide el código. La API responde igual si el correo no está registrado o si se pidió otro hace
     * menos de un minuto (en ese caso no manda otro), así que el éxito no garantiza un correo nuevo.
     */
    suspend fun requestCode(email: String): ApiResult<Unit> = safeApiCall(json) {
        authApi.forgotPassword(EmailRequest(normalize(email)))
        Unit
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): ApiResult<Unit> = safeApiCall(json) {
        authApi.resetPassword(ResetPasswordRequest(normalize(email), code, newPassword))
        Unit
    }

    private fun normalize(email: String) = email.trim().lowercase()

    companion object {
        // Códigos de error de la API que la recuperación maneja aparte.
        /** 503 sin mensaje para el usuario: Resend no pudo enviar el correo. */
        const val RECOVERY_EMAIL_FAILED = "RECOVERY_EMAIL_FAILED"

        /** El código venció (10 min) o ya se usó: hay que pedir otro. */
        const val RECOVERY_CODE_EXPIRED = "RECOVERY_CODE_EXPIRED"

        /** Se equivocó 5 veces: el código se invalida y hay que pedir otro. */
        const val RECOVERY_ATTEMPTS_EXCEEDED = "RECOVERY_ATTEMPTS_EXCEEDED"
    }
}
