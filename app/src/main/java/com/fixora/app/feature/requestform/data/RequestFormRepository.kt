package com.fixora.app.feature.requestform.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.requestform.domain.RequestDraft
import com.fixora.app.feature.requestform.domain.TimeSlot
import com.fixora.app.feature.servicerequest.domain.parseApiInstant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.create
import java.time.LocalDate
import javax.inject.Singleton

/** Interfaz para probar el formulario sin red. */
interface RequestFormRepository {
    /** Sube las fotos (JPEG) y regresa sus llaves. */
    suspend fun uploadPhotos(photos: List<ByteArray>): ApiResult<List<String>>

    suspend fun freeSlots(branchId: String, categoryId: String, date: LocalDate): ApiResult<List<TimeSlot>>

    /** Regresa el id de la solicitud creada. */
    suspend fun create(draft: RequestDraft): ApiResult<String>
}

class RemoteRequestFormRepository(
    private val api: RequestFormApi,
    private val json: Json,
) : RequestFormRepository {

    override suspend fun uploadPhotos(photos: List<ByteArray>): ApiResult<List<String>> {
        val keys = mutableListOf<String>()
        for ((index, photo) in photos.withIndex()) {
            val part = MultipartBody.Part.createFormData("file", "solicitud-${index + 1}.jpg", photo.toRequestBody(JPEG))
            when (val upload = safeApiCall(json) { api.uploadFile(PHOTOS, part) }) {
                is ApiResult.Success -> keys += upload.data.data.fileKey
                is ApiResult.Failure -> return upload
            }
        }
        return ApiResult.Success(keys)
    }

    override suspend fun freeSlots(branchId: String, categoryId: String, date: LocalDate) = safeApiCall(json) {
        api.freeSlots(branchId, categoryId, date.toString()).data.mapNotNull { slot ->
            val start = parseApiInstant(slot.startsAt) ?: return@mapNotNull null
            val end = parseApiInstant(slot.endsAt) ?: return@mapNotNull null
            TimeSlot(start, end)
        }
    }

    override suspend fun create(draft: RequestDraft) = safeApiCall(json) {
        val body = CreateRequestBody(
            clientUuid = draft.clientUuid,
            branchId = draft.branchId,
            categoryId = draft.categoryId,
            description = draft.description,
            addressId = draft.savedAddressId,
            address = draft.currentAddress?.takeIf { draft.savedAddressId == null }?.let {
                NewAddressDto(
                    street = it.street,
                    numExt = it.number.ifBlank { "S/N" },
                    colony = it.colony,
                    municipality = it.municipality,
                    state = it.state,
                    postalCode = it.postalCode,
                    latitude = it.position.latitude,
                    longitude = it.position.longitude,
                )
            },
            attachmentKeys = draft.photoKeys.takeIf { it.isNotEmpty() },
            scheduledAt = draft.scheduledAt?.toString(),
        )
        api.create(body).data.id
    }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()
        val PHOTOS = "photos".toRequestBody("text/plain".toMediaType())
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RequestFormModule {
    @Provides
    @Singleton
    fun provideRequestFormApi(retrofit: Retrofit): RequestFormApi = retrofit.create()

    @Provides
    @Singleton
    fun provideRequestFormRepository(api: RequestFormApi, json: Json): RequestFormRepository = RemoteRequestFormRepository(api, json)
}
