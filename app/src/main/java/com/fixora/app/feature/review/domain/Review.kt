package com.fixora.app.feature.review.domain

import java.time.Instant

/** Quién califica a quién (API-39). */
enum class ReviewDirection(val apiValue: String) {
    ClientToProvider("cliente_a_proveedor"),
    ProviderToClient("proveedor_a_cliente"),
    ;

    companion object {
        fun from(value: String?): ReviewDirection? = entries.firstOrNull { it.apiValue == value }
    }
}

/** Foto o video adjunto a una reseña; el enlace vence en unas horas. */
data class ReviewEvidence(val url: String, val isVideo: Boolean)

data class ServiceReview(
    val id: String,
    val direction: ReviewDirection?,
    val rating: Int,
    val comment: String,
    /** Ya viene corto desde la API: «Roberto M.». */
    val authorName: String,
    val createdAt: Instant?,
    val evidence: List<ReviewEvidence> = emptyList(),
)

/** Las reseñas de una solicitud y si a la cuenta todavía le toca calificar. */
data class RequestReviews(
    val myDirection: ReviewDirection,
    val canReview: Boolean,
    val reviews: List<ServiceReview>,
) {
    /** La que ya escribió la cuenta, si la hay. */
    val mine: ServiceReview? get() = reviews.firstOrNull { it.direction == myDirection }
}

/** Una página de reseñas de una sucursal, de la más nueva a la más vieja. */
data class ReviewsPage(val items: List<ServiceReview>, val page: Int, val totalPages: Int) {
    val hasMore: Boolean get() = page < totalPages
}

/** Lo mismo que revisa la API al guardar una reseña. */
object ReviewRules {
    const val MAX_COMMENT_LENGTH = 1000
    const val MAX_EVIDENCE = 3

    /** Fotos o video MP4; a diferencia de la cancelación, sin PDF. */
    val EVIDENCE_MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "video/mp4")
}
