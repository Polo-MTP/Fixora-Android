package com.fixora.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fixora.app.R
import com.fixora.app.core.network.ApiError

/**
 * Texto para mostrarle al usuario. En los errores 4xx la API ya manda un mensaje en español
 * pensado para el usuario («Correo o contraseña incorrectos.»), así que se usa tal cual.
 */
@Composable
fun ApiError.message(): String = when (this) {
    ApiError.NoConnection -> stringResource(R.string.error_no_connection)
    ApiError.Timeout -> stringResource(R.string.error_timeout)
    is ApiError.Http -> message?.takeIf { status in 400..499 } ?: stringResource(R.string.error_server)
    ApiError.Unknown -> stringResource(R.string.error_unknown)
}
