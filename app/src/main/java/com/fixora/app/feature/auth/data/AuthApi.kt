package com.fixora.app.feature.auth.data

import com.fixora.app.core.session.TokensDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("auth/verify-email")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest): MessageResponse

    @POST("auth/resend-verification")
    suspend fun resendVerification(@Body body: EmailRequest): MessageResponse
}

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val user: UserDto, val tokens: TokensDto)

@Serializable
data class UserDto(val id: String, val email: String, val role: String, val status: String)

/** Cuenta (users) y datos personales (personal_data) del cliente nuevo. */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    @SerialName("lastname_p") val lastNameP: String,
    @SerialName("lastname_m") val lastNameM: String?,
    /** Formato yyyy-MM-dd. */
    @SerialName("birth_date") val birthDate: String,
    val phone: String,
)

@Serializable
data class RegisterResponse(val user: UserDto)

@Serializable
data class VerifyEmailRequest(val email: String, val code: String)

@Serializable
data class EmailRequest(val email: String)

@Serializable
data class MessageResponse(val message: String)
