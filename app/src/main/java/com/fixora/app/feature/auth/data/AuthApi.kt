package com.fixora.app.feature.auth.data

import com.fixora.app.core.session.TokensDto
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse
}

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val user: UserDto, val tokens: TokensDto)

@Serializable
data class UserDto(val id: String, val email: String, val role: String, val status: String)
