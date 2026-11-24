package com.fixora.app.feature.servicerequest.data

import com.fixora.app.core.network.DataResponse
import com.fixora.app.feature.payment.data.PaymentDto
import com.fixora.app.feature.payment.data.PaymentSummaryDto
import com.fixora.app.feature.payment.data.PriceAdjustmentDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Solicitudes de servicio (API-26 y API-27). */
interface ServiceRequestApi {
    /** La ven el cliente que la creó y el proveedor o técnico al que le llegó. */
    @GET("service-requests/{id}")
    suspend fun getRequest(@Path("id") id: String): DataResponse<ServiceRequestDto>

    /** El primero que acepta se queda con ella; si ya no está pendiente responde 409. */
    @POST("service-requests/{id}/accept")
    suspend fun accept(@Path("id") id: String, @Body body: AcceptRequest)

    @POST("service-requests/{id}/reject")
    suspend fun reject(@Path("id") id: String, @Body body: RejectRequest)

    /** El técnico avanza el traslado: en_camino, llegado o completada, con su posición (ANDROID-20). */
    @POST("service-requests/{id}/status")
    suspend fun changeStatus(@Path("id") id: String, @Body body: StatusChangeRequest)

    /** Última posición del técnico y la ruta con su tiempo de llegada; solo mientras va en camino. */
    @GET("service-requests/{id}/tracking")
    suspend fun tracking(@Path("id") id: String): DataResponse<TrackingDto>

    /**
     * Cancela la solicitud. El cliente, mientras nadie la acepta, sin motivo; después de aceptar, el
     * técnico con motivo y, si tiene, las llaves de su evidencia (ANDROID-21).
     */
    @POST("service-requests/{id}/cancel")
    suspend fun cancel(@Path("id") id: String, @Body body: CancelRequest = CancelRequest())

    /** La evidencia de la cancelación se sube antes en la categoría «evidences». */
    @Multipart
    @POST("files/upload")
    suspend fun uploadEvidence(
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part,
    ): DataResponse<UploadedEvidenceDto>
}

@Serializable
data class UploadedEvidenceDto(@SerialName("file_key") val fileKey: String)

@Serializable
data class StatusChangeRequest(val status: String, val latitude: Double, val longitude: Double)

@Serializable
data class TrackingDto(
    val status: String = "",
    @SerialName("technician_location") val technicianLocation: PositionDto? = null,
    @SerialName("route_eta") val routeEta: RouteEtaDto? = null,
)

@Serializable
data class RouteEtaDto(
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    @SerialName("distance_meters") val distanceMeters: Double = 0.0,
    @SerialName("eta_timestamp") val etaTimestamp: String? = null,
    val geometry: LineStringDto? = null,
)

/** GeoJSON LineString: cada punto es [longitud, latitud]. */
@Serializable
data class LineStringDto(val coordinates: List<List<Double>> = emptyList())

@Serializable
data class CancelRequest(
    val reason: String? = null,
    @SerialName("evidence_keys") val evidenceKeys: List<String>? = null,
)

@Serializable
data class AcceptRequest(@SerialName("employee_id") val employeeId: String)

@Serializable
data class RejectRequest(val reason: String)

@Serializable
data class ServiceRequestDto(
    val id: String,
    val status: String,
    @SerialName("is_emergency") val isEmergency: Boolean = false,
    val description: String = "",
    val category: NamedDto? = null,
    val branch: BranchDto? = null,
    val technician: TechnicianDto? = null,
    val client: ClientDto = ClientDto(),
    val address: AddressDto? = null,
    val location: PositionDto? = null,
    @SerialName("base_price") val basePrice: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    @SerialName("agreed_price") val agreedPrice: String? = null,
    /** Precio acordado más los ajustes aprobados; la API lo fija al finalizar (API-36). */
    @SerialName("total_amount") val totalAmount: String? = null,
    @SerialName("price_adjustments") val priceAdjustments: List<PriceAdjustmentDto> = emptyList(),
    @SerialName("payment_summary") val paymentSummary: PaymentSummaryDto? = null,
    val payments: List<PaymentDto> = emptyList(),
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val dispatches: DispatchesDto = DispatchesDto(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
)

@Serializable
data class NamedDto(val id: String, val name: String = "")

@Serializable
data class BranchDto(val id: String, val name: String = "", val phone: String? = null)

@Serializable
data class TechnicianDto(
    val name: String = "",
    @SerialName("photo_url") val photoUrl: String? = null,
)

/** A cuántos técnicos se avisó y cuántos rechazaron (API-30). */
@Serializable
data class DispatchesDto(val sent: Int = 0, val rejected: Int = 0)

@Serializable
data class ClientDto(
    val name: String = "",
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
)

@Serializable
data class AddressDto(
    val id: String? = null,
    val alias: String? = null,
    val street: String? = null,
    @SerialName("num_ext") val numExt: String? = null,
    @SerialName("num_int") val numInt: String? = null,
    val reference: String? = null,
    val colony: String = "",
    val municipality: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class PositionDto(val latitude: Double? = null, val longitude: Double? = null)

@Serializable
data class AttachmentDto(
    @SerialName("file_key") val fileKey: String = "",
    val url: String = "",
)
