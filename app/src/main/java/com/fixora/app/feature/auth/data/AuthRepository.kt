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
import javax.inject.Inject
import javax.inject.Singleton

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
        val result = safeApiCall(json) { authApi.login(LoginRequest(email.trim().lowercase(), password)) }
        if (result is ApiResult.Failure) return result

        val (user, tokens) = (result as ApiResult.Success).data
        if (Roles.homeArea(user.role) == null) {
            safeApiCall(json) { sessionApi.logout(RefreshTokenRequest(tokens.refreshToken)) }
            return ApiResult.Failure(ApiError.Http(status = 403, code = WEB_ONLY_ROLE, message = null))
        }
        sessionStore.save(
            Session(
                userId = user.id,
                email = user.email,
                role = user.role,
                status = user.status,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
            ),
        )
        return ApiResult.Success(Unit)
    }

    companion object {
        /** Código propio de la app (no de la API) para las cuentas del panel web. */
        const val WEB_ONLY_ROLE = "WEB_ONLY_ROLE"
    }
}
