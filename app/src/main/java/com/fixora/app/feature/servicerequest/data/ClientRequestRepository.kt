package com.fixora.app.feature.servicerequest.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.emergency.data.EmergencyApi
import com.fixora.app.feature.emergency.data.EmergencyBody
import com.fixora.app.feature.requestform.data.CreateRequestBody
import com.fixora.app.feature.requestform.data.RequestFormApi
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json

/** Lo que hace el cliente con su solicitud mientras espera (ANDROID-17). */
interface ClientRequestRepository {
    suspend fun request(id: String): ApiResult<ServiceRequest>

    /** Solo mientras nadie la acepta. */
    suspend fun cancel(id: String): ApiResult<Unit>

    /**
     * La manda otra vez igual (mismo negocio, servicio, descripción, dirección y fotos; o la misma
     * emergencia). [clientUuid] es nuevo para que la API la cree como otra solicitud.
     */
    suspend fun resend(request: ServiceRequest, clientUuid: String): ApiResult<String>
}

class RemoteClientRequestRepository(
    private val api: ServiceRequestApi,
    private val formApi: RequestFormApi,
    private val emergencyApi: EmergencyApi,
    private val requests: ServiceRequestRepository,
    private val json: Json,
) : ClientRequestRepository {

    override suspend fun request(id: String) = requests.request(id)

    override suspend fun cancel(id: String) = safeApiCall(json) { api.cancel(id) }

    override suspend fun resend(request: ServiceRequest, clientUuid: String): ApiResult<String> {
        val categoryId = request.categoryId ?: return ApiResult.Failure(ApiError.Unknown)
        if (request.isEmergency) {
            val position = request.position ?: return ApiResult.Failure(ApiError.Unknown)
            return safeApiCall(json) {
                emergencyApi.create(EmergencyBody(clientUuid, categoryId, position.latitude, position.longitude)).data.id
            }
        }
        val branchId = request.branchId ?: return ApiResult.Failure(ApiError.Unknown)
        val addressId = request.addressId ?: return ApiResult.Failure(ApiError.Unknown)
        return safeApiCall(json) {
            formApi.create(
                CreateRequestBody(
                    clientUuid = clientUuid,
                    branchId = branchId,
                    categoryId = categoryId,
                    description = request.description,
                    addressId = addressId,
                    attachmentKeys = request.photoKeys.takeIf { it.isNotEmpty() },
                ),
            ).data.id
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ClientRequestModule {
    @Provides
    fun provideClientRequestRepository(
        api: ServiceRequestApi,
        formApi: RequestFormApi,
        emergencyApi: EmergencyApi,
        requests: ServiceRequestRepository,
        json: Json,
    ): ClientRequestRepository = RemoteClientRequestRepository(api, formApi, emergencyApi, requests, json)
}
