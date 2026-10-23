package com.fixora.app.feature.auth.ui

import com.fixora.app.feature.auth.domain.FieldError

/**
 * Valor de un campo de formulario y si el usuario ya salió de él con algo escrito.
 * Los errores se muestran al salir del campo o al intentar enviar, no mientras se escribe.
 */
data class FormField(
    val value: String = "",
    val touched: Boolean = false,
) {
    fun onChange(newValue: String) = copy(value = newValue)

    fun onBlur() = if (value.isNotEmpty()) copy(touched = true) else this
}

/** Devuelve el error solo cuando ya toca mostrarlo. */
internal fun FieldError?.visibleIf(show: Boolean): FieldError? = this?.takeIf { show }
