package com.fixora.app.feature.servicerequest.data

import com.fixora.app.core.network.DataResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/** Solicitudes de servicio (API-26 y API-27). */
interface ServiceRequestApi {
    /** La ven el cliente que la creó y el proveedor o técnico al que le llegó. */
    @GET("service-requests/{id}")
    suspend fun getRequest(@Path("id") id: String): DataResponse<ServiceRequestDto>
}

@Serializable
data class ServiceRequestDto(
    val id: String,
    val status: String,
    @SerialName("is_emergency") val isEmergency: Boolean = false,
    val description: String = "",
    val category: NamedDto? = null,
    val branch: NamedDto? = null,
    val technician: TechnicianDto? = null,
    val client: ClientDto = ClientDto(),
    val address: AddressDto? = null,
    @SerialName("base_price") val basePrice: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
)

@Serializable
data class NamedDto(val id: String, val name: String = "")

@Serializable
data class TechnicianDto(val name: String = "")

@Serializable
data class ClientDto(
    val name: String = "",
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
)

@Serializable
data class AddressDto(
    val alias: String? = null,
    val colony: String = "",
    val municipality: String = "",
)

@Serializable
data class AttachmentDto(val url: String = "")
