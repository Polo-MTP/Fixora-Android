package com.fixora.app.feature.providerprofile.data

import com.fixora.app.core.network.DataResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/** Ficha pública de la sucursal (API-21). No pide sesión, pero se manda igual. */
interface ProviderProfileApi {
    @GET("branches/{id}/public")
    suspend fun getPublicProfile(@Path("id") branchId: String): DataResponse<PublicProfileDto>
}

@Serializable
data class PublicProfileDto(
    val id: String,
    val name: String,
    @SerialName("business_name") val businessName: String = "",
    @SerialName("supplier_type") val supplierType: String = "",
    val description: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    val plates: List<String> = emptyList(),
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("reviews_count") val reviewsCount: Int = 0,
    @SerialName("total_jobs") val totalJobs: Int = 0,
    @SerialName("accepting_request") val acceptingRequest: Boolean = true,
    val location: PublicLocationDto = PublicLocationDto(),
    val schedules: List<PublicScheduleDto> = emptyList(),
    val categories: List<PublicCategoryDto> = emptyList(),
    val portfolio: List<PublicWorkDto> = emptyList(),
    val certificates: List<PublicCertificateDto> = emptyList(),
    @SerialName("latest_reviews") val latestReviews: List<PublicReviewDto> = emptyList(),
)

@Serializable
data class PublicLocationDto(
    val colony: String = "",
    val municipality: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class PublicScheduleDto(
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("open_time") val openTime: String,
    @SerialName("close_time") val closeTime: String,
    val active: Boolean = true,
)

@Serializable
data class PublicCategoryDto(
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_name") val categoryName: String,
    @SerialName("base_price") val basePrice: String,
    @SerialName("duration_minutes") val durationMinutes: Int = 60,
)

@Serializable
data class PublicWorkDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val media: List<PublicMediaDto> = emptyList(),
)

@Serializable
data class PublicMediaDto(val url: String = "")

@Serializable
data class PublicCertificateDto(
    val id: String,
    @SerialName("type_name") val typeName: String = "",
    @SerialName("file_key") val fileKey: String = "",
    val url: String = "",
)

/** Una de las últimas 5 reseñas publicadas; el nombre ya viene como «Roberto M.». */
@Serializable
data class PublicReviewDto(
    val rating: Int,
    val comment: String? = null,
    @SerialName("author_name") val authorName: String = "",
)
