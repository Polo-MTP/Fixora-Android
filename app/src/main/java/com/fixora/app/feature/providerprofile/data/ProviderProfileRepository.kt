package com.fixora.app.feature.providerprofile.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.portfolio.domain.PortfolioWork
import com.fixora.app.feature.providerprofile.domain.DaySchedule
import com.fixora.app.feature.providerprofile.domain.ProviderProfile
import com.fixora.app.feature.providerprofile.domain.ProviderService
import com.fixora.app.feature.providerprofile.domain.PublicCertificate
import com.fixora.app.feature.providerprofile.domain.Review
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/** Interfaz para probar la pantalla sin red. */
fun interface ProviderProfileRepository {
    suspend fun profile(branchId: String): ApiResult<ProviderProfile>
}

class RemoteProviderProfileRepository(
    private val api: ProviderProfileApi,
    private val json: Json,
) : ProviderProfileRepository {

    override suspend fun profile(branchId: String) = safeApiCall(json) { api.getPublicProfile(branchId).data.toDomain() }

    private fun PublicProfileDto.toDomain() = ProviderProfile(
        id = id,
        name = name,
        businessName = businessName,
        independent = supplierType == "independiente",
        description = description.orEmpty().trim(),
        logoUrl = logoUrl,
        verified = isVerified,
        plates = plates.filter { it.isNotBlank() },
        rating = ratingAvg,
        reviewsCount = reviewsCount,
        totalJobs = totalJobs,
        acceptingRequests = acceptingRequest,
        colony = location.colony,
        municipality = location.municipality,
        position = location.latitude?.let { lat -> location.longitude?.let { lng -> Coordinates(lat, lng) } },
        schedules = schedules.filter { it.active }.map { DaySchedule(it.dayOfWeek, it.openTime.take(5), it.closeTime.take(5)) },
        services = categories.map { ProviderService(it.categoryId, it.categoryName, it.basePrice, it.durationMinutes) },
        works = portfolio.map { work ->
            PortfolioWork(
                id = work.id,
                title = work.title,
                description = work.description.orEmpty(),
                photoUrls = work.media.map { it.url }.filter { it.isNotBlank() },
            )
        },
        certificates = certificates.filter { it.url.isNotBlank() }.map {
            PublicCertificate(it.id, it.typeName, it.url, isPdf = it.fileKey.endsWith(".pdf", ignoreCase = true))
        },
        latestReviews = latestReviews.map { Review(it.rating.coerceIn(1, 5), it.comment.orEmpty().trim(), it.authorName) },
    )
}

@Module
@InstallIn(SingletonComponent::class)
object ProviderProfileModule {
    @Provides
    @Singleton
    fun provideProviderProfileApi(retrofit: Retrofit): ProviderProfileApi = retrofit.create()

    @Provides
    @Singleton
    fun provideProviderProfileRepository(api: ProviderProfileApi, json: Json): ProviderProfileRepository =
        RemoteProviderProfileRepository(api, json)
}
