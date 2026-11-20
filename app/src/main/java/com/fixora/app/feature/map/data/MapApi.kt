package com.fixora.app.feature.map.data

import com.fixora.app.core.network.DataResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Búsqueda de proveedores para el mapa del cliente (API-23) y catálogo de servicios. */
interface MapApi {
    /** Los parámetros null no se mandan; available_now solo se manda cuando es true. */
    @GET("search/branches")
    suspend fun searchBranches(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("radius_km") radiusKm: Int,
        @Query("category_id") categoryId: String?,
        @Query("available_now") availableNow: Boolean?,
        @Query("sort") sort: String,
        @Query("limit") limit: Int,
    ): BranchSearchResponse

    @GET("categories")
    suspend fun categories(): DataResponse<List<CategoryDto>>
}

@Serializable
data class BranchSearchResponse(
    val data: List<BranchResultDto>,
    val pagination: PaginationDto,
)

@Serializable
data class BranchResultDto(
    val id: String,
    val name: String,
    @SerialName("business_name") val businessName: String = "",
    @SerialName("supplier_type") val supplierType: String = "",
    @SerialName("logo_url") val logoUrl: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_km") val distanceKm: Double = 0.0,
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("reviews_count") val reviewsCount: Int = 0,
    @SerialName("base_price") val basePrice: String? = null,
    @SerialName("open_now") val openNow: Boolean = false,
)

@Serializable
data class PaginationDto(val total: Int = 0)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val slug: String = "",
    val active: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
)
