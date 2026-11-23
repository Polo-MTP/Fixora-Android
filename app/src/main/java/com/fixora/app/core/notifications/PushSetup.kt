package com.fixora.app.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.fixora.app.BuildConfig
import com.fixora.app.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Arranque de las notificaciones push (ANDROID-6). Firebase se inicia con los valores de
 * app/env/<flavor>.properties en lugar de google-services.json; si están vacíos la app funciona
 * igual, solo que sin push.
 */
object PushSetup {
    /** Canal de las notificaciones normales; el AndroidManifest lo declara como el de por defecto. */
    const val GENERAL_CHANNEL_ID = "general"

    /**
     * Canal de las alarmas de solicitud entrante (ANDROID-13): sonido propio, vibración larga y
     * visible en la pantalla bloqueada. El id es el que manda la API en `channel_id`.
     */
    const val ALARM_CHANNEL_ID = "emergency_alarms"

    /** Inicia Firebase y crea el canal. Se llama una vez, en FixoraApplication. */
    fun initialize(context: Context) {
        createChannels(context)
        if (FirebaseApp.getApps(context).isEmpty()) {
            firebaseOptions()?.let { FirebaseApp.initializeApp(context, it) }
        }
    }

    /** true si Firebase quedó iniciado (hay configuración para este flavor). */
    fun isEnabled(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private fun firebaseOptions(): FirebaseOptions? {
        val values = listOf(
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID,
        )
        if (values.any { it.isBlank() }) return null
        return FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
    }

    // Los canales existen desde Android 8 (minSdk 26). Una vez creado, el sonido de un canal solo
    // lo cambia el usuario en Ajustes: para cambiarlo desde la app habría que usar otro id.
    private fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val general = NotificationChannel(
            GENERAL_CHANNEL_ID,
            context.getString(R.string.push_channel_general),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.push_channel_general_description) }

        val alarmSound = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.alarma_solicitud}".toUri()
        val alarm = NotificationChannel(
            ALARM_CHANNEL_ID,
            context.getString(R.string.push_channel_alarm),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.push_channel_alarm_description)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 1000)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannels(listOf(general, alarm))
    }
}
