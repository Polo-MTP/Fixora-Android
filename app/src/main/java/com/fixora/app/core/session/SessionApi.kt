package com.fixora.app.core.session

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** Endpoints de la sesión ya iniciada. El login vive en feature/auth (AuthApi). */
interface SessionApi {
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshTokenRequest): RefreshResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshTokenRequest)
}

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class TokensDto(val accessToken: String, val refreshToken: String)

@Serializable
data class RefreshResponse(val tokens: TokensDto)
