package com.fixora.app.feature.servicerequest.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.servicerequest.domain.CancelRules
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import com.fixora.app.feature.servicerequest.domain.parseApiInstant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

/** Ruta al cliente con lo que falta para llegar. */
data class TripRoute(
    /** Puntos de la ruta en orden; vacío si la API aún no la calcula. */
    val path: List<Coordinates>,
    val durationSeconds: Long,
    val distanceMeters: Long,
    val arrivesAt: Instant?,
)

data class TripTracking(val technician: Coordinates?, val route: TripRoute?)

/** A qué estado avanza el técnico (ANDROID-20). */
enum class TripStep(val apiValue: String) { Start("en_camino"), Arrive("llegado"), Finish("completada") }

/** El traslado del técnico: ver la solicitud, su ruta y avanzar de estado. Interfaz para probar sin red. */
interface TripRepository {
    suspend fun request(id: String): ApiResult<ServiceRequest>
    suspend fun tracking(id: String): ApiResult<TripTracking>

    /** La API pide dónde está el técnico al salir, al llegar y al terminar. */
    suspend fun advance(id: String, step: TripStep, position: Coordinates): ApiResult<Unit>

    /** Sube una evidencia de la cancelación y devuelve su llave (ANDROID-21). */
    suspend fun uploadEvidence(name: String, mimeType: String, bytes: ByteArray): ApiResult<String>

    /** El técnico cancela ya aceptada: el motivo es obligatorio y la evidencia, opcional. */
    suspend fun cancel(id: String, reason: String, evidenceKeys: List<String>): ApiResult<Unit>
}

class RemoteTripRepository(
    private val api: ServiceRequestApi,
    private val requests: ServiceRequestRepository,
    private val json: Json,
) : TripRepository {

    override suspend fun request(id: String) = requests.request(id)

    override suspend fun tracking(id: String): ApiResult<TripTracking> = safeApiCall(json) {
        val data = api.tracking(id).data
        TripTracking(
            technician = data.technicianLocation?.let { p -> p.latitude?.let { lat -> p.longitude?.let { Coordinates(lat, it) } } },
            route = data.routeEta?.let { eta ->
                TripRoute(
                    // GeoJSON va [longitud, latitud].
                    path = eta.geometry?.coordinates.orEmpty().mapNotNull { point ->
                        if (point.size >= 2) Coordinates(point[1], point[0]) else null
                    },
                    durationSeconds = eta.durationSeconds.toLong(),
                    distanceMeters = eta.distanceMeters.toLong(),
                    arrivesAt = parseApiInstant(eta.etaTimestamp),
                )
            },
        )
    }

    override suspend fun advance(id: String, step: TripStep, position: Coordinates) = safeApiCall(json) {
        api.changeStatus(id, StatusChangeRequest(step.apiValue, position.latitude, position.longitude))
    }

    override suspend fun uploadEvidence(name: String, mimeType: String, bytes: ByteArray) = safeApiCall(json) {
        // La API revisa la extensión: el nombre siempre lleva la que corresponde al tipo.
        val fileName = "evidencia.${CancelRules.extensionFor(mimeType)}"
        val part = MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody(mimeType.toMediaType()))
        api.uploadEvidence(EvidencesCategory, part).data.fileKey
    }

    override suspend fun cancel(id: String, reason: String, evidenceKeys: List<String>) = safeApiCall(json) {
        api.cancel(id, CancelRequest(reason = reason, evidenceKeys = evidenceKeys.takeIf { it.isNotEmpty() }))
    }

    private companion object {
        val EvidencesCategory = "evidences".toRequestBody("text/plain".toMediaType())
    }
}

@Module
@InstallIn(SingletonComponent::class)
object TripModule {
    @Provides
    fun provideTripRepository(api: ServiceRequestApi, requests: ServiceRequestRepository, json: Json): TripRepository =
        RemoteTripRepository(api, requests, json)
}
