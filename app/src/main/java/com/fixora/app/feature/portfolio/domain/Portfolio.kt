package com.fixora.app.feature.portfolio.domain

/** Un trabajo que el proveedor muestra en su ficha: título, descripción y fotos opcionales. */
data class PortfolioWork(
    val id: String,
    val title: String,
    val description: String,
    /** Enlaces firmados a las fotos, vigentes unas horas. */
    val photoUrls: List<String>,
)

/** Revisión del administrador. Un certificado solo se puede mostrar en la ficha si está aprobado. */
enum class CertificateStatus { Pending, Approved, Rejected }

data class Certificate(
    val id: String,
    val name: String,
    val status: CertificateStatus,
    /** Motivo del administrador, sobre todo cuando lo rechaza. */
    val comment: String?,
    val isPublic: Boolean,
    /** yyyy-MM-dd */
    val uploadDate: String?,
)

/** Reglas del portafolio y los certificados; la API valida lo mismo. */
object PortfolioRules {
    const val TITLE_MAX_LENGTH = 255
    const val DESCRIPTION_MAX_LENGTH = 1000
    const val MAX_PHOTOS = 10

    /** Límites de la API: fotos de 5 MB y documentos de 10 MB. */
    const val PHOTO_MAX_BYTES = 5L * 1024 * 1024
    const val DOCUMENT_MAX_BYTES = 10L * 1024 * 1024

    /** Tipos que acepta la API para un certificado. */
    val CERTIFICATE_MIME_TYPES = arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")

    // Mismo patrón que la API (NON_PUBLISHABLE_DOC_PATTERN): estos nunca se muestran en la ficha.
    private val nonPublishable = Regex(
        "identificaci[oó]n|ine|pasaporte|domicilio|comprobante|situaci[oó]n fiscal|rfc|acta\\s+constitutiva|poder\\s+notarial|penales",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Los certificados son los documentos opcionales que se pueden publicar. Los obligatorios y los
     * de identidad son parte de la solicitud de registro y no se muestran aquí.
     */
    fun isCertificate(typeName: String, mandatory: Boolean) = !mandatory && !nonPublishable.containsMatchIn(typeName)
}
