package com.fixora.app.feature.servicerequest.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.profile.data.TechnicianProfileApi
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json

/** Lo que necesita la pantalla de alarma (ANDROID-13): ver la solicitud y responderla. */
interface IncomingRequestRepository {
    suspend fun request(id: String): ApiResult<ServiceRequest>

    /** Acepta como el técnico de esta cuenta. */
    suspend fun accept(id: String): ApiResult<Unit>

    suspend fun reject(id: String, reason: String): ApiResult<Unit>
}

class RemoteIncomingRequestRepository(
    private val api: ServiceRequestApi,
    private val profileApi: TechnicianProfileApi,
    private val requests: ServiceRequestRepository,
    private val json: Json,
) : IncomingRequestRepository {

    override suspend fun request(id: String) = requests.request(id)

    // La API pide qué técnico acepta: es el registro de empleado de esta cuenta.
    override suspend fun accept(id: String): ApiResult<Unit> = safeApiCall(json) {
        val employeeId = profileApi.getMyProfile().data.id
        api.accept(id, AcceptRequest(employeeId))
    }

    override suspend fun reject(id: String, reason: String) = safeApiCall(json) {
        api.reject(id, RejectRequest(reason.trim()))
    }
}

@Module
@InstallIn(SingletonComponent::class)
object IncomingRequestModule {
    @Provides
    fun provideIncomingRequestRepository(
        api: ServiceRequestApi,
        profileApi: TechnicianProfileApi,
        requests: ServiceRequestRepository,
        json: Json,
    ): IncomingRequestRepository = RemoteIncomingRequestRepository(api, profileApi, requests, json)
}
