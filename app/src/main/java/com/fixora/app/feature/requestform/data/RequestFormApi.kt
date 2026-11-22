package com.fixora.app.feature.requestform.data

import com.fixora.app.core.network.DataResponse
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
import retrofit2.http.Query

/** Formulario de solicitud y agendar cita (ANDROID-15, API-27). */
interface RequestFormApi {
    @POST("service-requests")
    suspend fun create(@Body body: CreateRequestBody): DataResponse<CreatedRequestDto>

    /** Horarios libres de un día (YYYY-MM-DD) para ese servicio, ya sin los ocupados. */
    @GET("branches/{id}/availability")
    suspend fun freeSlots(
        @Path("id") branchId: String,
        @Query("category_id") categoryId: String,
        @Query("date") date: String,
    ): DataResponse<List<SlotDto>>

    @Multipart
    @POST("files/upload")
    suspend fun uploadFile(
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part,
    ): DataResponse<UploadedFileDto>
}

/** Se manda address_id (una guardada) o address (la ubicación actual), nunca los dos. */
@Serializable
data class CreateRequestBody(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("branch_id") val branchId: String,
    @SerialName("category_id") val categoryId: String,
    val description: String,
    @SerialName("address_id") val addressId: String? = null,
    val address: NewAddressDto? = null,
    @SerialName("attachment_keys") val attachmentKeys: List<String>? = null,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
)

@Serializable
data class NewAddressDto(
    val street: String,
    @SerialName("num_ext") val numExt: String,
    val colony: String,
    val municipality: String,
    val state: String,
    @SerialName("postal_code") val postalCode: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class CreatedRequestDto(val id: String)

@Serializable
data class SlotDto(
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
)

@Serializable
data class UploadedFileDto(@SerialName("file_key") val fileKey: String)
