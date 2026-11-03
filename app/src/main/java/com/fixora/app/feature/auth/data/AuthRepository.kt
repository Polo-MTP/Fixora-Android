package com.fixora.app.feature.auth.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.core.session.RefreshTokenRequest
import com.fixora.app.core.session.Roles
import com.fixora.app.core.session.Session
import com.fixora.app.core.session.SessionApi
import com.fixora.app.core.session.SessionStore
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Datos del formulario de registro, ya validados por la app. */
data class NewClient(
    val email: String,
    val password: String,
    val name: String,
    val lastNameP: String,
    val lastNameM: String,
    val birthDate: LocalDate,
    val phone: String,
)

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionApi: SessionApi,
    private val sessionStore: SessionStore,
    private val json: Json,
) {
    /**
     * Inicia sesión y, si la API acepta, guarda la sesión cifrada en el celular. Los roles que no
     * usan la app (administrador, soporte) reciben [WEB_ONLY_ROLE] y su sesión se revoca.
     */
    suspend fun login(email: String, password: String): ApiResult<Unit> {
        val result = signIn(email, password)
        if (result is ApiResult.Failure) return result
        saveSession((result as ApiResult.Success).data)
        return ApiResult.Success(Unit)
    }

    /**
     * Pide la sesión a la API sin guardarla todavía; el registro la guarda con [saveSession] cuando
     * el cliente sale de la pantalla de confirmación.
     */
    suspend fun signIn(email: String, password: String): ApiResult<Session> {
        val result = safeApiCall(json) { authApi.login(LoginRequest(normalize(email), password)) }
        if (result is ApiResult.Failure) return result

        val (user, tokens) = (result as ApiResult.Success).data
        if (Roles.homeArea(user.role) == null) {
            safeApiCall(json) { sessionApi.logout(RefreshTokenRequest(tokens.refreshToken)) }
            return ApiResult.Failure(ApiError.Http(status = 403, code = WEB_ONLY_ROLE, message = null))
        }
        return ApiResult.Success(
            Session(
                userId = user.id,
                email = user.email,
                role = user.role,
                status = user.status,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
            ),
        )
    }

    /** Al guardarse la sesión, FixoraNavHost lleva al inicio que le toca al rol. */
    suspend fun saveSession(session: Session) = sessionStore.save(session)

    /** Crea la cuenta del cliente; la API le manda un código de 6 dígitos a su correo. */
    suspend fun register(client: NewClient): ApiResult<Unit> = safeApiCall(json) {
        authApi.register(
            RegisterRequest(
                email = normalize(client.email),
                password = client.password,
                name = client.name.trim(),
                lastNameP = client.lastNameP.trim(),
                lastNameM = client.lastNameM.trim().ifEmpty { null },
                birthDate = client.birthDate.toString(),
                phone = client.phone,
            ),
        )
        Unit
    }

    suspend fun verifyEmail(email: String, code: String): ApiResult<Unit> = safeApiCall(json) {
        authApi.verifyEmail(VerifyEmailRequest(normalize(email), code))
        Unit
    }

    suspend fun resendVerificationCode(email: String): ApiResult<Unit> = safeApiCall(json) {
        authApi.resendVerification(EmailRequest(normalize(email)))
        Unit
    }

    private fun normalize(email: String) = email.trim().lowercase()

    companion object {
        /** Código propio de la app (no de la API) para las cuentas del panel web. */
        const val WEB_ONLY_ROLE = "WEB_ONLY_ROLE"

        // Códigos de error de la API que el registro maneja aparte.
        const val EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED"
        const val EMAIL_ALREADY_VERIFIED = "EMAIL_ALREADY_VERIFIED"
        const val EMAIL_SEND_FAILED = "EMAIL_SEND_FAILED"
    }
}
