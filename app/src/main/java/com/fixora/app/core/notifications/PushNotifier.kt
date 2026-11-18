package com.fixora.app.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fixora.app.MainActivity
import com.fixora.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Muestra una notificación cuando llega con la app abierta: en primer plano Firebase no la
 * muestra solo. Al tocarla abre MainActivity con los mismos extras que pone el sistema en segundo
 * plano, así las dos se leen igual (PushMessage.fromExtras).
 */
@Singleton
class PushNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun show(title: String, body: String, data: Map<String, String>, channelId: String? = null) {
        if (!canNotify()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val id = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Un canal que este celular aún no tiene (p. ej. el de alarmas) haría que no se mostrara nada.
        val channel = channelId?.takeIf { NotificationManagerCompat.from(context).getNotificationChannel(it) != null }
            ?: PushSetup.GENERAL_CHANNEL_ID
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.fixora_navy_800))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Se quitó el permiso justo ahora: no hay nada que mostrar.
        }
    }

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
