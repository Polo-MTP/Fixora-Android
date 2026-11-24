package com.fixora.app.feature.servicerequest.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import com.fixora.app.feature.servicerequest.domain.parseApiInstant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/** Interfaz para probar la pantalla sin red. */
fun interface ServiceRequestRepository {
    suspend fun request(id: String): ApiResult<ServiceRequest>
}

class RemoteServiceRequestRepository(
    private val api: ServiceRequestApi,
    private val json: Json,
) : ServiceRequestRepository {

    override suspend fun request(id: String) = safeApiCall(json) { api.getRequest(id).data.toDomain() }

    private fun ServiceRequestDto.toDomain() = ServiceRequest(
        id = id,
        status = RequestStatus.from(status),
        isEmergency = isEmergency,
        description = description.trim(),
        categoryName = category?.name.orEmpty(),
        clientName = client.name.trim(),
        clientRating = client.ratingAvg,
        zone = address?.let { listOf(it.colony, it.municipality).filter(String::isNotBlank).joinToString(", ") }.orEmpty(),
        position = location?.toCoordinates() ?: address?.let { PositionDto(it.latitude, it.longitude) }?.toCoordinates(),
        basePrice = basePrice,
        photoUrls = attachments.map { it.url }.filter { it.isNotBlank() },
        createdAt = parseApiInstant(createdAt),
        expiresAt = parseApiInstant(expiresAt),
        scheduledAt = parseApiInstant(scheduledAt),
        technicianName = technician?.name?.takeIf { it.isNotBlank() },
        categoryId = category?.id,
        branchId = branch?.id,
        branchName = branch?.name?.takeIf { it.isNotBlank() },
        branchPhone = branch?.phone?.takeIf { it.isNotBlank() },
        technicianPhotoUrl = technician?.photoUrl,
        addressId = address?.id,
        photoKeys = attachments.map { it.fileKey }.filter { it.isNotBlank() },
        agreedPrice = agreedPrice,
        acceptedAt = parseApiInstant(acceptedAt),
        arrivedAt = parseApiInstant(arrivedAt),
        completedAt = parseApiInstant(completedAt),
        street = address?.streetLine(),
        addressReference = address?.reference?.trim()?.takeIf { it.isNotEmpty() },
        dispatchesSent = dispatches.sent,
        dispatchesRejected = dispatches.rejected,
        totalAmount = totalAmount,
        adjustments = priceAdjustments.map { it.toDomain() },
        paymentSummary = paymentSummary?.toDomain(),
        payments = payments.map { it.toDomain() },
    )

    private fun AddressDto.streetLine(): String? {
        val street = street?.trim().orEmpty()
        if (street.isEmpty()) return null
        val number = listOfNotNull(
            numExt?.trim()?.takeIf { it.isNotEmpty() },
            numInt?.trim()?.takeIf { it.isNotEmpty() }?.let { "int. $it" },
        ).joinToString(" ")
        return listOf(street, number).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun PositionDto.toCoordinates(): Coordinates? =
        latitude?.let { lat -> longitude?.let { lng -> Coordinates(lat, lng) } }
}

@Module
@InstallIn(SingletonComponent::class)
object ServiceRequestModule {
    @Provides
    @Singleton
    fun provideServiceRequestApi(retrofit: Retrofit): ServiceRequestApi = retrofit.create()

    @Provides
    @Singleton
    fun provideServiceRequestRepository(api: ServiceRequestApi, json: Json): ServiceRequestRepository =
        RemoteServiceRequestRepository(api, json)
}
