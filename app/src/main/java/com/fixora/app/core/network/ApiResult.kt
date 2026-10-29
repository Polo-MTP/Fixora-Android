package com.fixora.app.core.network

/** Resultado de una llamada a la API: los ViewModels nunca reciben excepciones de red. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    /** No hay internet o no se pudo llegar al servidor. */
    data object NoConnection : ApiError

    /** El servidor tardó demasiado en responder. */
    data object Timeout : ApiError

    /**
     * La API respondió con un código de error. [code] y [message] vienen del cuerpo de la
     * respuesta (por ejemplo INVALID_CREDENTIALS y «Correo o contraseña incorrectos.»).
     */
    data class Http(val status: Int, val code: String?, val message: String?) : ApiError

    /** Respuesta que no se pudo leer u otro error inesperado. */
    data object Unknown : ApiError
}
