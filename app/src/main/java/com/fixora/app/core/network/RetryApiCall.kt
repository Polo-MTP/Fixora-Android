package com.fixora.app.core.network

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Identificador que la app genera para una acción que crea algo en la API (una solicitud, un
 * pago...). Se manda en el cuerpo como `client_uuid`: si la respuesta se pierde y la app reintenta,
 * la API reconoce el mismo valor y no crea un duplicado.
 *
 * Se genera UNA vez por acción del usuario, antes de [retryApiCall], nunca dentro del bloque:
 *
 *     val clientUuid = newClientUuid()
 *     retryApiCall(json) { requestApi.create(CreateRequest(clientUuid = clientUuid, ...)) }
 */
fun newClientUuid(): String = UUID.randomUUID().toString()

/**
 * Como [safeApiCall], pero reintenta cuando no hubo conexión o se agotó el tiempo, esperando un
 * poco más cada vez. Los errores de la API (4xx, 5xx) no se reintentan: repetirlos daría lo mismo.
 */
suspend fun <T> retryApiCall(
    json: Json,
    attempts: Int = 3,
    firstDelayMs: Long = 500,
    call: suspend () -> T,
): ApiResult<T> {
    var waitMs = firstDelayMs
    repeat(attempts - 1) {
        val result = safeApiCall(json, call)
        if (result !is ApiResult.Failure || !result.error.isRetryable) return result
        delay(waitMs)
        waitMs *= 3
    }
    return safeApiCall(json, call)
}

private val ApiError.isRetryable: Boolean
    get() = this is ApiError.NoConnection || this is ApiError.Timeout
