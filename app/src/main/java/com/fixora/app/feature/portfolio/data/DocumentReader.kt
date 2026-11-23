package com.fixora.app.feature.portfolio.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.fixora.app.feature.portfolio.domain.PortfolioRules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Por qué no se pudo usar el archivo elegido. */
enum class DocumentProblem { Unreadable, WrongType, TooLarge }

sealed interface DocumentReadResult {
    data class Ready(val document: PickedDocument) : DocumentReadResult
    data class Rejected(val problem: DocumentProblem) : DocumentReadResult
}

/**
 * Lee el certificado elegido (PDF o imagen) y revisa el tipo y el tamaño antes de subirlo, para no
 * esperar una subida que la API va a rechazar.
 */
@Singleton
class DocumentReader @Inject constructor(@param:ApplicationContext private val context: Context) {

    suspend fun read(uri: Uri): DocumentReadResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)?.lowercase()
        if (mimeType == null || mimeType !in PortfolioRules.CERTIFICATE_MIME_TYPES) {
            return@withContext DocumentReadResult.Rejected(DocumentProblem.WrongType)
        }

        var name = "certificado"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.let { value -> name = value }
                    if (!it.isNull(1)) size = it.getLong(1)
                }
            }
        }
        if (size > PortfolioRules.DOCUMENT_MAX_BYTES) {
            return@withContext DocumentReadResult.Rejected(DocumentProblem.TooLarge)
        }

        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext DocumentReadResult.Rejected(DocumentProblem.Unreadable)
        when {
            bytes.isEmpty() -> DocumentReadResult.Rejected(DocumentProblem.Unreadable)
            bytes.size > PortfolioRules.DOCUMENT_MAX_BYTES -> DocumentReadResult.Rejected(DocumentProblem.TooLarge)
            else -> DocumentReadResult.Ready(PickedDocument(bytes, mimeType, name))
        }
    }
}
