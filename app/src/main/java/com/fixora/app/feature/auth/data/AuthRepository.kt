package com.fixora.app.feature.auth.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.core.session.Session
import com.fixora.app.core.session.SessionStore
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val json: Json,
) {
    /** Inicia sesión y, si la API acepta, guarda la sesión cifrada en el celular. */
    suspend fun login(email: String, password: String): ApiResult<Unit> =
        when (val result = safeApiCall(json) { authApi.login(LoginRequest(email.trim().lowercase(), password)) }) {
            is ApiResult.Success -> {
                val (user, tokens) = result.data
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
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
        }
}
