package com.fixora.app.feature.emergency.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.DataResponse
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.availability.location.Coordinates
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Singleton

/** Solicitud de emergencia (API-28): solo servicio y ubicación; la API avisa a los técnicos cercanos. */
interface EmergencyApi {
    @POST("service-requests/emergency")
    suspend fun create(@Body body: EmergencyBody): DataResponse<EmergencyCreatedDto>
}

@Serializable
data class EmergencyBody(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("category_id") val categoryId: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class EmergencyCreatedDto(val id: String)

/** Interfaz para probar la pantalla sin red. */
fun interface EmergencyRepository {
    /** Regresa el id de la solicitud; 409 NO_TECHNICIANS_AVAILABLE si no hay técnicos cerca. */
    suspend fun send(clientUuid: String, categoryId: String, position: Coordinates): ApiResult<String>
}

class RemoteEmergencyRepository(
    private val api: EmergencyApi,
    private val json: Json,
) : EmergencyRepository {
    override suspend fun send(clientUuid: String, categoryId: String, position: Coordinates) = safeApiCall(json) {
        api.create(EmergencyBody(clientUuid, categoryId, position.latitude, position.longitude)).data.id
    }
}

@Module
@InstallIn(SingletonComponent::class)
object EmergencyModule {
    @Provides
    @Singleton
    fun provideEmergencyApi(retrofit: Retrofit): EmergencyApi = retrofit.create()

    @Provides
    @Singleton
    fun provideEmergencyRepository(api: EmergencyApi, json: Json): EmergencyRepository = RemoteEmergencyRepository(api, json)
}
