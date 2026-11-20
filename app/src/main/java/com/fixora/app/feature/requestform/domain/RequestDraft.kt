package com.fixora.app.feature.requestform.domain

import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.map.domain.normalized
import java.time.Instant

/** Dirección armada con la ubicación actual (no está en la libreta de direcciones). */
data class CurrentAddress(
    val street: String,
    val number: String,
    val colony: String,
    val municipality: String,
    val state: String,
    val postalCode: String,
    val position: Coordinates,
) {
    val line: String get() = listOf("$street $number".trim(), colony).filter { it.isNotBlank() }.joinToString(", ")
}

/** Un horario libre para agendar. */
data class TimeSlot(val startsAt: Instant, val endsAt: Instant)

/** A dónde va el técnico: una dirección guardada o la ubicación actual. */
sealed interface AddressChoice {
    data class Saved(val id: String) : AddressChoice
    data object Current : AddressChoice
}

/** Lo que se manda al tocar «Enviar solicitud». */
data class RequestDraft(
    val clientUuid: String,
    val branchId: String,
    val categoryId: String,
    val description: String,
    val savedAddressId: String?,
    val currentAddress: CurrentAddress?,
    val photoKeys: List<String>,
    val scheduledAt: Instant?,
)

object RequestFormRules {
    const val MAX_PHOTOS = 5
    const val MAX_DESCRIPTION = 2000
    const val SCHEDULE_DAYS = 7

    /** La opción que no agrega texto a la descripción. */
    const val OTHER = "Otro"

    /**
     * Problemas frecuentes por servicio para «¿Qué está pasando?» (Figma «Solicitar servicio»); el
     * técnico lee el elegido al principio de la descripción.
     */
    fun suggestions(serviceName: String): List<String> {
        val key = serviceName.normalized()
        val options = when {
            key.startsWith("plomeria") -> listOf("Fuga de agua", "Tubería rota", "Sin agua", "Drenaje tapado")
            key.startsWith("electrodomestico") -> listOf("No enciende", "Hace ruido", "Gotea", "Instalación")
            key.startsWith("electricidad") -> listOf("Sin luz", "Corto circuito", "Contacto dañado", "Instalación")
            key.startsWith("cerrajeria") -> listOf("Me quedé afuera", "Cambiar chapa", "Llave rota", "Copia de llave")
            key.startsWith("climatizacion") -> listOf("No enfría", "Mantenimiento", "Hace ruido", "Instalación")
            key.startsWith("pintura") -> listOf("Pintar cuarto", "Humedad", "Impermeabilizar", "Retoques")
            key.startsWith("carpinteria") -> listOf("Puerta atorada", "Mueble roto", "Instalación", "A la medida")
            key.startsWith("albanileria") -> listOf("Grietas", "Remodelación", "Piso", "Barda")
            key.startsWith("jardineria") -> listOf("Podar pasto", "Podar árboles", "Limpieza", "Riego")
            key.startsWith("limpieza") -> listOf("Limpieza general", "Desinfección", "Después de obra", "Tapicería")
            else -> listOf("Reparación", "Instalación", "Revisión")
        }
        return options + OTHER
    }

    /** «Fuga de agua. Gotea el tubo del lavabo»; sin problema elegido (u «Otro»), solo el texto. */
    fun description(problem: String?, details: String): String {
        val text = details.trim()
        val title = problem?.takeUnless { it == OTHER }
        return when {
            title == null -> text
            text.isEmpty() -> title
            else -> "$title. $text"
        }.take(MAX_DESCRIPTION)
    }
}
