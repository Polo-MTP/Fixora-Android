package com.fixora.app.feature.profile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

// Suficiente para la foto de perfil y muy por debajo del límite de 5 MB de la API.
private const val MAX_SIDE_PX = 1280
private const val JPEG_QUALITY = 85

/**
 * Convierte la foto elegida (cámara o galería) en un JPEG listo para subir: la reduce, la
 * endereza según cómo se tomó y le quita los metadatos (ubicación incluida) al recomprimirla.
 */
@Singleton
class PhotoReader @Inject constructor(@ApplicationContext private val context: Context) {

    /** null si el archivo no se pudo leer o no es una imagen. */
    suspend fun readAsJpeg(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { rotationOf(ExifInterface(it)) } ?: 0

            val output = ByteArrayOutputStream()
            scaleAndRotate(decoded, rotation).compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }.getOrNull()
    }

    // Potencia de 2 que deja la imagen todavía más grande que el tamaño final, para escalar después.
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        return sample
    }

    private fun rotationOf(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun scaleAndRotate(bitmap: Bitmap, rotation: Int): Bitmap {
        val scale = minOf(1f, MAX_SIDE_PX.toFloat() / maxOf(bitmap.width, bitmap.height))
        if (scale == 1f && rotation == 0) return bitmap
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
