package com.fixora.app.feature.availability.data

import com.fixora.app.core.network.DataResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * Interruptor En línea / Fuera de línea del técnico (API-22). La posición vive en Redis con un
 * vencimiento de 5 minutos: mientras está en línea hay que volver a mandarla antes de que venza.
 */
interface AvailabilityApi {
    @GET("technicians/me/availability")
    suspend fun get(): DataResponse<AvailabilityDto>

    /** Para ponerse en línea la API exige latitude y longitude. */
    @PUT("technicians/me/availability")
    suspend fun update(@Body body: AvailabilityRequest): DataResponse<AvailabilityDto>
}

@Serializable
data class AvailabilityRequest(
    val online: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class AvailabilityDto(
    val online: Boolean,
    val branch: BranchStatusDto? = null,
)

@Serializable
data class BranchStatusDto(
    @SerialName("is_open") val isOpen: Boolean = true,
    @SerialName("accepting_request") val acceptingRequest: Boolean = true,
)
