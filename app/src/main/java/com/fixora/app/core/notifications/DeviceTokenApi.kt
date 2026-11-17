package com.fixora.app.core.notifications

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** Tokens de FCM del usuario con sesión (API-16). Usa el cliente con token de acceso. */
interface DeviceTokenApi {
    /** Registra el token o lo reasigna a este usuario si ya estaba con otro. */
    @POST("me/devices")
    suspend fun register(@Body body: RegisterDeviceRequest)

    @POST("me/devices/deactivate")
    suspend fun deactivate(@Body body: DeviceTokenRequest)
}

@Serializable
data class RegisterDeviceRequest(val token: String, val platform: String)

@Serializable
data class DeviceTokenRequest(val token: String)
