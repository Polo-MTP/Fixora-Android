package com.fixora.app.feature.servicerequest.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.fixora.app.feature.servicerequest.domain.CancelRules
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Por qué no se pudo usar la evidencia elegida. */
enum class EvidenceProblem { Unreadable, WrongType, TooLarge }

sealed interface EvidenceReadResult {
    class Ready(val name: String, val mimeType: String, val bytes: ByteArray) : EvidenceReadResult
    data class Rejected(val problem: EvidenceProblem) : EvidenceReadResult
}

/** Lee la foto o el archivo elegido. Interfaz para probar el ViewModel sin Android. */
fun interface EvidenceReader {
    suspend fun read(uri: Uri): EvidenceReadResult
}

/**
 * Revisa el tipo y el tamaño antes de subir, para no esperar una subida que la API va a rechazar.
 * Un video largo pasa de 25 MB: se avisa en vez de intentar subirlo.
 */
class AndroidEvidenceReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : EvidenceReader {

    override suspend fun read(uri: Uri): EvidenceReadResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)?.lowercase()
            ?: return@withContext EvidenceReadResult.Rejected(EvidenceProblem.Unreadable)
        if (mimeType !in CancelRules.EVIDENCE_MIME_TYPES) {
            return@withContext EvidenceReadResult.Rejected(EvidenceProblem.WrongType)
        }

        var name = "evidencia.${CancelRules.extensionFor(mimeType)}"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.let { value -> name = value }
                    if (!it.isNull(1)) size = it.getLong(1)
                }
            }
        }
        if (size > CancelRules.EVIDENCE_MAX_BYTES) {
            return@withContext EvidenceReadResult.Rejected(EvidenceProblem.TooLarge)
        }

        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        when {
            bytes == null || bytes.isEmpty() -> EvidenceReadResult.Rejected(EvidenceProblem.Unreadable)
            bytes.size > CancelRules.EVIDENCE_MAX_BYTES -> EvidenceReadResult.Rejected(EvidenceProblem.TooLarge)
            else -> EvidenceReadResult.Ready(name, mimeType, bytes)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EvidenceReaderModule {
    @Binds
    abstract fun bindEvidenceReader(reader: AndroidEvidenceReader): EvidenceReader
}
