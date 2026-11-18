package com.fixora.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
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

    // Los canales existen desde Android 8 (minSdk 26). El de alarmas de emergencia
    // ("emergency_alarms", con sonido propio) llega con «Alarma de solicitud entrante» (Sprint 4).
    private fun createChannels(context: Context) {
        val channel = NotificationChannel(
            GENERAL_CHANNEL_ID,
            context.getString(R.string.push_channel_general),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.push_channel_general_description) }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }
}
