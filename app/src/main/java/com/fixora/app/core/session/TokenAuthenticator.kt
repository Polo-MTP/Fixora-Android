package com.fixora.app.core.session

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renovación automática: cuando la API responde 401 a una petición con token, OkHttp llama a
 * [authenticate]. Aquí se piden tokens nuevos con el refresh token y se repite la petición, sin
 * que la pantalla se entere. Si la renovación es rechazada, la sesión se borra y la app regresa
 * a la landing.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokens: SessionTokens,
    private val sessionApi: SessionApi,
    private val json: Json,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val sentToken = response.request.header(AUTHORIZATION)?.removePrefix("Bearer ") ?: return null
        // Ya se renovó una vez para esta petición y aun así falló: no insistir.
        if (response.priorResponse != null) return null

        // Varias peticiones pueden recibir 401 al mismo tiempo; solo una renueva y las demás
        // esperan aquí y reusan los tokens nuevos. La API invalida el refresh token al usarlo.
        synchronized(lock) {
            val session = runBlocking { tokens.current() } ?: return null
            if (session.accessToken != sentToken) return response.request.withAccessToken(session.accessToken)

            return when (val result = runBlocking { safeApiCall(json) { sessionApi.refresh(RefreshTokenRequest(session.refreshToken)) } }) {
                is ApiResult.Success -> {
                    val new = result.data.tokens
                    runBlocking { tokens.updateTokens(new.accessToken, new.refreshToken) }
                    response.request.withAccessToken(new.accessToken)
                }
                is ApiResult.Failure -> {
                    // Sin internet o con la API caída (5xx) la sesión sigue siendo válida; solo se
                    // borra si la API rechaza el refresh token (4xx: vencido, revocado o ya usado).
                    if ((result.error as? ApiError.Http)?.status in 400..499) runBlocking { tokens.clear() }
                    null
                }
            }
        }
    }
}
