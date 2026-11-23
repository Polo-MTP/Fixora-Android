package com.fixora.app.feature.map.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.map.domain.MapFilters
import com.fixora.app.feature.map.domain.MapProvider
import com.fixora.app.feature.map.domain.MapRules
import com.fixora.app.feature.map.domain.MapSearchResult
import com.fixora.app.feature.map.domain.ServiceCategory
import kotlinx.serialization.json.Json

/** Proveedores cercanos y servicios para el mapa (ANDROID-10). Interfaz para probar sin red. */
interface MapRepository {
    suspend fun search(center: Coordinates, filters: MapFilters, categoryId: String?): ApiResult<MapSearchResult>

    suspend fun categories(): ApiResult<List<ServiceCategory>>
}

class RemoteMapRepository(
    private val api: MapApi,
    private val json: Json,
) : MapRepository {

    override suspend fun search(center: Coordinates, filters: MapFilters, categoryId: String?) = safeApiCall(json) {
        val response = api.searchBranches(
            latitude = center.latitude,
            longitude = center.longitude,
            radiusKm = filters.radiusKm,
            categoryId = categoryId,
            availableNow = true.takeIf { filters.availableNow },
            minRating = filters.minRating,
            maxPrice = filters.maxPrice,
            // En el mapa importan los más cercanos: si hay más de 50, se quedan fuera los lejanos.
            sort = "distancia",
            limit = MapRules.MAX_RESULTS,
        )
        MapSearchResult(providers = response.data.map { it.toDomain() }, total = response.pagination.total)
    }

    override suspend fun categories() = safeApiCall(json) {
        api.categories().data
            .filter { it.active }
            .sortedBy { it.sortOrder }
            .map { ServiceCategory(id = it.id, name = it.name, slug = it.slug) }
    }

    private fun BranchResultDto.toDomain() = MapProvider(
        id = id,
        name = name,
        businessName = businessName,
        independent = supplierType == "independiente",
        logoUrl = logoUrl,
        position = Coordinates(latitude, longitude),
        distanceKm = distanceKm,
        rating = ratingAvg,
        reviewsCount = reviewsCount,
        basePrice = basePrice,
        openNow = openNow,
    )
}
