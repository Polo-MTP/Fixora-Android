package com.fixora.app.feature.servicerequest.domain

/**
 * Reglas para que el técnico cancele un servicio ya aceptado (ANDROID-21), las mismas que revisa
 * la API: motivo obligatorio y hasta 5 evidencias (foto, PDF o video MP4) de 25 MB cada una.
 */
object CancelRules {
    const val REASON_MAX_LENGTH = 500
    const val MAX_EVIDENCE = 5
    const val EVIDENCE_MAX_BYTES = 25L * 1024 * 1024

    /** Lo que acepta la categoría «evidences» de la API. */
    val EVIDENCE_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf", "video/mp4")

    /** Después de aceptar y antes de terminar; la API responde 409 en otro estado. */
    fun canCancel(status: RequestStatus): Boolean =
        status == RequestStatus.Accepted ||
            status == RequestStatus.Scheduled ||
            status == RequestStatus.OnTheWay ||
            status == RequestStatus.Arrived

    /** La API revisa que la extensión corresponda al tipo del archivo. */
    fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "application/pdf" -> "pdf"
        "video/mp4" -> "mp4"
        else -> "jpg"
    }
}

/** Qué tipo de evidencia es, para mostrar su ícono. */
enum class EvidenceKind { Photo, Document, Video }

fun evidenceKindOf(mimeType: String): EvidenceKind = when {
    mimeType == "application/pdf" -> EvidenceKind.Document
    mimeType.startsWith("video/") -> EvidenceKind.Video
    else -> EvidenceKind.Photo
}
