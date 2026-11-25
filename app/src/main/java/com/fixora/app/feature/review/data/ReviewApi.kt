package com.fixora.app.feature.review.data

import com.fixora.app.core.network.DataResponse
import com.fixora.app.feature.servicerequest.data.UploadedEvidenceDto
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

/** Reseñas bidireccionales (API-39). */
interface ReviewApi {
    /** Las reseñas de la solicitud y si la cuenta todavía puede calificar. */
    @GET("service-requests/{id}/reviews")
    suspend fun requestReviews(@Path("id") requestId: String): DataResponse<RequestReviewsDto>

    /** Solo con la solicitud finalizada; con el mismo client_uuid la API regresa la original. */
    @POST("service-requests/{id}/reviews")
    suspend fun create(@Path("id") requestId: String, @Body body: CreateReviewBody): DataResponse<ReviewDto>

    /** Reseñas de clientes sobre la sucursal, para la ficha pública. */
    @GET("branches/{id}/reviews")
    suspend fun branchReviews(
        @Path("id") branchId: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): DataResponse<BranchReviewsDto>

    @Multipart
    @POST("files/upload")
    suspend fun uploadEvidence(
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part,
    ): DataResponse<UploadedEvidenceDto>
}

@Serializable
data class RequestReviewsDto(
    @SerialName("my_direction") val myDirection: String = "",
    @SerialName("can_review") val canReview: Boolean = false,
    val reviews: List<ReviewDto> = emptyList(),
)

@Serializable
data class ReviewDto(
    val id: String,
    val direction: String? = null,
    val rating: Int,
    val comment: String? = null,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val evidence: List<ReviewEvidenceDto> = emptyList(),
)

@Serializable
data class ReviewEvidenceDto(
    @SerialName("mime_type") val mimeType: String = "",
    val url: String? = null,
)

@Serializable
data class CreateReviewBody(
    val rating: Int,
    val comment: String? = null,
    @SerialName("attachment_keys") val attachmentKeys: List<String>? = null,
    @SerialName("client_uuid") val clientUuid: String,
)

@Serializable
data class BranchReviewsDto(
    val items: List<ReviewDto> = emptyList(),
    val pagination: PaginationDto = PaginationDto(),
)

@Serializable
data class PaginationDto(
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 0,
)
