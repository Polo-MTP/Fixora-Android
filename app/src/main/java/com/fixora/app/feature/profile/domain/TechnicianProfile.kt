package com.fixora.app.feature.profile.domain

import com.fixora.app.feature.auth.domain.FieldError

/** Lo que el cliente verá del técnico antes de que llegue: foto, biografía y vehículo. */
data class TechnicianProfile(
    val employeeId: String,
    /** Enlace firmado a la foto, vigente unas horas; null si no tiene. */
    val photoUrl: String?,
    val bio: String,
    val vehicle: Vehicle?,
)

data class Vehicle(
    val plate: String,
    val brand: String,
    val model: String,
    val color: String,
) {
    /** «Nissan NP300 blanco» */
    val description get() = listOf(brand, model, color.lowercase()).filter { it.isNotBlank() }.joinToString(" ")
}

/** Reglas del perfil; la API valida lo mismo. */
object TechnicianProfileRules {
    const val BIO_MAX_LENGTH = 1000

    // Las placas en México tienen de 5 a 8 caracteres; con guiones y espacios caben en 10.
    const val PLATE_MAX_LENGTH = 10

    /** Mayúsculas, sin espacios al inicio o al final, como las guarda la API. */
    fun normalizePlate(value: String) = value.trim().uppercase()

    fun plate(value: String): FieldError? = when {
        value.isBlank() -> FieldError.Required
        normalizePlate(value).none { it.isLetterOrDigit() } -> FieldError.PlateInvalid
        else -> null
    }
}
