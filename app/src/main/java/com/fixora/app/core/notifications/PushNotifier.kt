package com.fixora.app.core.notifications

import android.Manifest
import android.app.Notification
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

    /**
     * Alarma de solicitud entrante (ANDROID-13): suena con el canal de alarmas hasta que se atiende,
     * se abre a pantalla completa con el celular bloqueado (si Android lo permite) y desaparece a
     * los 5 minutos, cuando la solicitud ya venció. Una misma solicitud reemplaza su aviso.
     */
    fun showAlarm(title: String, body: String, data: Map<String, String>) {
        if (!canNotify()) return

        val id = alarmNotificationId(data[PushMessage.KEY_ENTITY_ID])
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
            putExtra(EXTRA_ALARM_NOTIFICATION_ID, id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, PushSetup.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.fixora_navy_800))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(ALARM_TIMEOUT_MS)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.push_alarm_open), pendingIntent)
        if (canUseFullScreenIntent()) builder.setFullScreenIntent(pendingIntent, true)

        // Insistente: el sonido se repite hasta que se toca o se descarta, como una alarma.
        val notification = builder.build().apply { flags = flags or Notification.FLAG_INSISTENT }
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Se quitó el permiso justo ahora: no hay nada que mostrar.
        }
    }

    /** Quita la alarma (por ejemplo al abrirla) para que deje de sonar. */
    fun cancelAlarm(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    /**
     * Android 14 solo deja abrir a pantalla completa a las apps que el usuario autoriza (salvo las
     * de llamadas y alarmas); sin permiso queda como aviso flotante.
     */
    fun canUseFullScreenIntent(): Boolean = NotificationManagerCompat.from(context).canUseFullScreenIntent()

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val KEY_IS_ALARM = "is_alarm"

        /** Extra con el id de la alarma que abrió la app, para quitarla y mostrar sobre el bloqueo. */
        const val EXTRA_ALARM_NOTIFICATION_ID = "alarm_notification_id"

        /** La solicitud vence a los 5 minutos: después no tiene caso seguir avisando. */
        const val ALARM_TIMEOUT_MS = 5 * 60 * 1000L

        /** Mismo id para la misma solicitud; las que no traen solicitud usan uno fijo. */
        fun alarmNotificationId(entityId: String?): Int =
            ALARM_ID_BASE + (entityId?.toLongOrNull()?.rem(1_000_000)?.toInt() ?: 0)

        private const val ALARM_ID_BASE = 700_000_000
    }
}
