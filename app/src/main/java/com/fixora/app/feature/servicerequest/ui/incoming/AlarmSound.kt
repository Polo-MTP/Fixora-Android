package com.fixora.app.feature.servicerequest.ui.incoming

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.fixora.app.R

private val VibrationPattern = longArrayOf(0, 600, 250, 600, 250, 1000, 800)

/**
 * Sonido y vibración de la alarma mientras [active]: se repiten hasta que se responde, vence o se
 * sale de la pantalla. Usa el mismo sonido que el canal de alarmas.
 */
@Composable
fun AlarmSoundEffect(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose {}
        val player = startSound(context)
        val vibrator = vibrator(context)
        vibrator?.vibrate(VibrationEffect.createWaveform(VibrationPattern, 0))
        onDispose {
            runCatching {
                player?.stop()
                player?.release()
            }
            vibrator?.cancel()
        }
    }
}

private fun startSound(context: Context): MediaPlayer? = runCatching {
    MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        setDataSource(context, "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.alarma_solicitud}".toUri())
        isLooping = true
        prepare()
        start()
    }
}.getOrNull()

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService<Vibrator>()
    }
