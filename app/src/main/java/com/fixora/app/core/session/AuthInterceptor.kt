package com.fixora.app.core.session

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

internal const val AUTHORIZATION = "Authorization"

internal fun Request.withAccessToken(token: String): Request =
    newBuilder().header(AUTHORIZATION, "Bearer $token").build()

/** Agrega el token de acceso a cada petición si hay una sesión iniciada. */
class AuthInterceptor @Inject constructor(
    private val tokens: SessionTokens,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)

        // OkHttp llama a esto en su propio hilo, no en el principal: bloquear aquí es seguro.
        val session = runBlocking { tokens.current() }
        return chain.proceed(session?.let { request.withAccessToken(it.accessToken) } ?: request)
    }
}
