package com.fixora.app.feature.portfolio.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.portfolio.domain.Certificate
import com.fixora.app.feature.portfolio.domain.CertificateStatus
import com.fixora.app.feature.portfolio.domain.PortfolioRules
import com.fixora.app.feature.portfolio.domain.PortfolioWork
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Archivo elegido para subirlo como certificado. */
class PickedDocument(val bytes: ByteArray, val mimeType: String, val fileName: String)

/**
 * Evidencias de trabajo y certificados del proveedor (ANDROID-8). No guarda nada en memoria: cada
 * pantalla pide lo que muestra, porque los enlaces de las fotos vencen en unas horas.
 */
class PortfolioRepository(
    private val api: PortfolioApi,
    private val json: Json,
) {
    suspend fun works(branchId: String): ApiResult<List<PortfolioWork>> = safeApiCall(json) {
        api.getWorks(branchId).data.map { it.toDomain() }
    }

    /**
     * Sube cada foto (JPEG) y luego registra el trabajo con sus llaves. Si alguna foto falla, no se
     * registra el trabajo.
     */
    suspend fun addWork(branchId: String, title: String, description: String, photos: List<ByteArray>): ApiResult<Unit> {
        val keys = when (val uploads = upload(photos)) {
            is ApiResult.Success -> uploads.data
            is ApiResult.Failure -> return uploads
        }
        val request = WorkRequest(
            title = title.trim(),
            description = description.trim().ifEmpty { null },
            media = keys.map(::MediaRequest),
        )
        return safeApiCall(json) {
            api.addWork(branchId, request)
            Unit
        }
    }

    suspend fun deleteWork(workId: String): ApiResult<Unit> = safeApiCall(json) { api.deleteWork(workId) }

    /** Sube [photos] (JPEG) y las agrega al trabajo; devuelve el trabajo con todas sus fotos. */
    suspend fun addPhotos(workId: String, photos: List<ByteArray>): ApiResult<PortfolioWork> {
        val keys = when (val uploads = upload(photos)) {
            is ApiResult.Success -> uploads.data
            is ApiResult.Failure -> return uploads
        }
        return safeApiCall(json) { api.addMedia(workId, MediaListRequest(keys.map(::MediaRequest))).data.toDomain() }
    }

    /** Sube cada foto y devuelve sus llaves; si una falla, no sigue. */
    private suspend fun upload(photos: List<ByteArray>): ApiResult<List<String>> {
        val keys = mutableListOf<String>()
        for ((index, photo) in photos.withIndex()) {
            val part = MultipartBody.Part.createFormData("file", "trabajo-${index + 1}.jpg", photo.toRequestBody(JPEG))
            when (val upload = safeApiCall(json) { api.uploadFile(PhotosCategory, part) }) {
                is ApiResult.Success -> keys += upload.data.data.fileKey
                is ApiResult.Failure -> return upload
            }
        }
        return ApiResult.Success(keys)
    }

    /** Solo los documentos que son certificados (opcionales y publicables), del más nuevo al más viejo. */
    suspend fun certificates(): ApiResult<List<Certificate>> = safeApiCall(json) {
        api.getMyApplication().data.documents
            .filter { PortfolioRules.isCertificate(it.typeName, it.mandatory) }
            .map { it.toCertificate() }
            .sortedWith(compareByDescending<Certificate> { it.uploadDate }.thenByDescending { it.id.toLongOrNull() })
    }

    suspend fun uploadCertificate(document: PickedDocument): ApiResult<Unit> = safeApiCall(json) {
        val part = MultipartBody.Part.createFormData(
            "file",
            document.fileName,
            document.bytes.toRequestBody(document.mimeType.toMediaType()),
        )
        api.uploadCertificate(part)
        Unit
    }

    suspend fun setVisibility(certificateId: String, isPublic: Boolean): ApiResult<Unit> = safeApiCall(json) {
        api.setVisibility(certificateId, VisibilityRequest(isPublic))
        Unit
    }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()
        val PhotosCategory = "photos".toRequestBody("text/plain".toMediaType())
    }
}

private fun WorkDto.toDomain() = PortfolioWork(
    id = id,
    title = title,
    description = description.orEmpty(),
    photoUrls = media.map { it.url }.filter { it.isNotBlank() },
)

internal fun DocumentDto.toCertificate() = Certificate(
    id = id,
    name = typeName,
    status = when (validation?.result?.lowercase()) {
        "aprobado" -> CertificateStatus.Approved
        "rechazado" -> CertificateStatus.Rejected
        else -> CertificateStatus.Pending
    },
    comment = validation?.comment?.takeIf { it.isNotBlank() },
    isPublic = isPublic,
    uploadDate = uploadDate?.take(10),
)
