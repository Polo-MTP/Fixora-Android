package com.fixora.app.feature.servicerequest.domain

import com.fixora.app.feature.availability.location.Coordinates
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Estados públicos de una solicitud (API-26). */
enum class RequestStatus {
    Pending, Accepted, Scheduled, OnTheWay, Arrived, Completed, Canceled, Expired, Unknown;

    companion object {
        fun from(value: String): RequestStatus = when (value.trim().lowercase()) {
            "pendiente" -> Pending
            "aceptada" -> Accepted
            "agendada" -> Scheduled
            "en_camino", "en_curso" -> OnTheWay
            "llegado" -> Arrived
            "completada" -> Completed
            "cancelada" -> Canceled
            "expirada" -> Expired
            else -> Unknown
        }
    }
}

/** Solicitud de servicio tal como la ve quien la abre desde un aviso (ANDROID-13). */
data class ServiceRequest(
    val id: String,
    val status: RequestStatus,
    val isEmergency: Boolean,
    val description: String,
    val categoryName: String,
    val clientName: String,
    val clientRating: Double,
    /** Colonia y municipio; la dirección exacta se ve hasta aceptarla. */
    val zone: String,
    /** Dónde es el servicio (la ubicación de la solicitud o, si no, la de su dirección). */
    val position: Coordinates? = null,
    val basePrice: String?,
    val photoUrls: List<String>,
    val createdAt: Instant?,
    val expiresAt: Instant?,
    val scheduledAt: Instant?,
    val technicianName: String?,
    val categoryId: String? = null,
    val branchId: String? = null,
    val branchName: String? = null,
    val branchPhone: String? = null,
    val technicianPhotoUrl: String? = null,
    /** Para reenviarla igual: la dirección guardada y las llaves de sus fotos. */
    val addressId: String? = null,
    val photoKeys: List<String> = emptyList(),
    val agreedPrice: String? = null,
    val acceptedAt: Instant? = null,
    val dispatchesSent: Int = 0,
    val dispatchesRejected: Int = 0,
) {
    /**
     * El proveedor ya dijo que no. Una normal va a un solo negocio: con un rechazo se acabó. Una
     * emergencia sigue abierta mientras quede algún técnico avisado sin responder.
     */
    val wasRejected: Boolean
        get() = status == RequestStatus.Pending && dispatchesRejected > 0 &&
            (!isEmergency || dispatchesRejected >= dispatchesSent)
}

/**
 * Las fechas llegan como ISO 8601 («2026-11-20T13:27:28.626Z»); algunas sin zona, que la API
 * guarda en UTC. Si no se puede leer queda en null.
 */
fun parseApiInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }.getOrNull()
}
