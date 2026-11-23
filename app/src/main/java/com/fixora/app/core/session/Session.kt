package com.fixora.app.core.session

import kotlinx.serialization.Serializable

/** Usuario con sesión iniciada y sus tokens, tal como los entrega la API al iniciar sesión. */
@Serializable
data class Session(
    val userId: String,
    val email: String,
    val role: String,
    val status: String,
    val accessToken: String,
    val refreshToken: String,
)

/** Lo que el cliente HTTP necesita para firmar peticiones y renovar los tokens. */
interface SessionTokens {
    suspend fun current(): Session?
    suspend fun updateTokens(accessToken: String, refreshToken: String)
    suspend fun clear()
}
