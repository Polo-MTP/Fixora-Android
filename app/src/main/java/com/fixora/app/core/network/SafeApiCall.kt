package com.fixora.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Ejecuta una llamada de Retrofit y convierte cualquier falla en [ApiError].
 *
 *     when (val result = safeApiCall(json) { authApi.login(request) }) { ... }
 */
suspend fun <T> safeApiCall(json: Json, call: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(call())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val (code, message) = parseErrorBody(json, body)
        ApiResult.Failure(ApiError.Http(e.code(), code, message))
    } catch (e: InterruptedIOException) {
        // SocketTimeoutException y el timeout total de OkHttp heredan de esta clase.
        ApiResult.Failure(ApiError.Timeout)
    } catch (e: IOException) {
        ApiResult.Failure(ApiError.NoConnection)
    } catch (e: SerializationException) {
        ApiResult.Failure(ApiError.Unknown)
    }

/**
 * La API responde errores en dos formas, según dónde se generan:
 * - controladores de auth: `{ "error": "CODE", "message": "..." }`
 * - manejador global: `{ "success": false, "error": { "code": "CODE", "message": "..." } }`
 */
internal fun parseErrorBody(json: Json, body: String?): Pair<String?, String?> {
    if (body.isNullOrBlank()) return null to null
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null to null
    return when (val error = root["error"]) {
        is JsonPrimitive -> error.contentOrNull to root.string("message")
        is JsonObject -> error.string("code") to error.string("message")
        else -> null to root.string("message")
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private val JsonPrimitive.contentOrNull: String?
    get() = if (isString) content else null
