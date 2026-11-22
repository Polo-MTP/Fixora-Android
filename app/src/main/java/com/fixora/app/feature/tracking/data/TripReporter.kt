package com.fixora.app.feature.tracking.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.session.SessionTokens
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.availability.location.Movement
import com.fixora.app.feature.servicerequest.data.ServiceRequestRepository
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

/** Por qué dejó de mandar la ubicación. */
enum class TripEnd {
    /** La solicitud ya no va en camino: llegó, se canceló o se completó. */
    NotInTransit,

    /** Esta cuenta no es el técnico asignado o la solicitud no existe. */
    NotAllowed,

    /** Se cerró la sesión. */
    LoggedOut,
}

/**
 * Manda la posición del técnico por WebSocket mientras va en camino al cliente (ANDROID-19), para
 * que el cliente lo vea avanzar en el mapa con su tiempo de llegada.
 *
 * - Pide la ubicación precisa cada [INTERVAL_MS] y solo si se movió [MIN_DISTANCE_METERS]: detenido
 *   en un semáforo no gasta batería ni datos.
 * - Si la conexión se cae (cambio de antena, sin señal) se reconecta sola, esperando cada vez más.
 * - El token de acceso dura 15 minutos y la API lo revisa en cada mensaje: antes de cada conexión
 *   consulta la solicitud por REST, que renueva el token si venció y confirma que sigue en camino.
 * - Revisa el estado cada [STATUS_CHECK_MS] por si el técnico está detenido y nadie le avisa que
 *   la solicitud terminó.
 */
class TripReporter(
    private val sockets: TripSocketFactory,
    private val tokens: SessionTokens,
    private val requests: ServiceRequestRepository,
    private val location: LocationSource,
    private val json: Json,
) {

    /** Corre hasta que la solicitud deja de ir en camino; cancelarlo lo detiene. */
    suspend fun run(requestId: String): TripEnd {
        var backoff = MIN_BACKOFF_MS
        while (true) {
            when (val check = stillInTransit(requestId)) {
                is Check.Ended -> return check.end
                Check.Unknown -> {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }
                Check.InTransit -> Unit
            }
            val token = tokens.current()?.accessToken ?: return TripEnd.LoggedOut
            val outcome = connection(requestId, token, onAuthenticated = { backoff = MIN_BACKOFF_MS })
            if (outcome != null) return outcome
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private sealed interface Check {
        data object InTransit : Check
        data object Unknown : Check
        data class Ended(val end: TripEnd) : Check
    }

    private suspend fun stillInTransit(requestId: String): Check =
        when (val result = requests.request(requestId)) {
            is ApiResult.Success ->
                if (result.data.status == RequestStatus.OnTheWay) Check.InTransit else Check.Ended(TripEnd.NotInTransit)
            is ApiResult.Failure -> when {
                result.error is ApiError.Http && result.error.status == 401 -> Check.Ended(TripEnd.LoggedOut)
                result.error is ApiError.Http && result.error.status in listOf(403, 404) -> Check.Ended(TripEnd.NotAllowed)
                else -> Check.Unknown // Sin internet o API caída: se vuelve a intentar.
            }
        }

    /** Una conexión: null si se cayó y hay que reconectar, o por qué terminó el traslado. */
    private suspend fun connection(requestId: String, token: String, onAuthenticated: () -> Unit): TripEnd? = coroutineScope {
        val socket = sockets.open()
        var sender: Job? = null
        var statusChecker: Job? = null
        var ended: TripEnd? = null
        try {
            socket.send(buildJsonObject { put("type", "authenticate"); put("token", token) }.toString())
            for (text in socket.incoming) {
                val message = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: continue
                when (message["type"]?.jsonPrimitive?.contentOrNull) {
                    "authenticated" -> if (sender == null) {
                        onAuthenticated()
                        sender = launch {
                            location.movementUpdates(INTERVAL_MS, MIN_DISTANCE_METERS).collect { movement ->
                                if (!socket.send(locationMessage(requestId, movement))) socket.close()
                            }
                        }
                        statusChecker = launch {
                            while (true) {
                                delay(STATUS_CHECK_MS)
                                val check = stillInTransit(requestId)
                                if (check is Check.Ended) {
                                    ended = check.end
                                    socket.close()
                                }
                            }
                        }
                    }
                    "error" -> when (message["code"]?.jsonPrimitive?.contentOrNull) {
                        in NOT_IN_TRANSIT_CODES -> return@coroutineScope TripEnd.NotInTransit
                        in NOT_ALLOWED_CODES -> return@coroutineScope TripEnd.NotAllowed
                        // Sesión vencida o mensaje inválido: la API cierra y se reconecta con token nuevo.
                        else -> Unit
                    }
                }
            }
            ended
        } finally {
            sender?.cancel()
            statusChecker?.cancel()
            socket.close()
        }
    }

    private fun locationMessage(requestId: String, movement: Movement): String = buildJsonObject {
        put("type", "technician_location")
        put("requestId", requestId)
        put("latitude", movement.position.latitude)
        put("longitude", movement.position.longitude)
        movement.heading?.let { put("heading", it) }
        movement.speed?.let { put("speed", it) }
        put("timestamp", Instant.ofEpochMilli(movement.timeMillis).toString())
    }.toString()

    companion object {
        /** Cada 10 segundos como máximo: el cliente ve avanzar al técnico sin saturar los datos. */
        const val INTERVAL_MS = 10_000L

        /** Con menos movimiento que esto no se manda nada (detenido en un semáforo, por ejemplo). */
        const val MIN_DISTANCE_METERS = 20f

        const val STATUS_CHECK_MS = 60_000L
        const val MIN_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L

        private val NOT_IN_TRANSIT_CODES = setOf("NOT_IN_TRANSIT")
        private val NOT_ALLOWED_CODES = setOf("INSUFFICIENT_PERMISSIONS", "FORBIDDEN", "NOT_FOUND")
    }
}
