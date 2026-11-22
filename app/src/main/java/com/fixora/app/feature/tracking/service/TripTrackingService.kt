package com.fixora.app.feature.tracking.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.fixora.app.MainActivity
import com.fixora.app.R
import com.fixora.app.feature.tracking.data.TripReporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Inicia y detiene el envío de la ubicación del traslado. Interfaz para probar sin Android. */
interface TripTrackingController {
    fun start(requestId: String)
    fun stop()
}

class AndroidTripTrackingController(private val context: Context) : TripTrackingController {
    override fun start(requestId: String) {
        val intent = Intent(context, TripTrackingService::class.java).putExtra(TripTrackingService.EXTRA_REQUEST_ID, requestId)
        // Android no deja iniciar un servicio de ubicación con la app en segundo plano.
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    override fun stop() {
        context.stopService(Intent(context, TripTrackingService::class.java))
    }
}

/**
 * Manda la posición del técnico mientras va en camino (ANDROID-19), aunque bloquee el celular o
 * cambie de app para navegar. Es un servicio en primer plano con notificación fija porque Android
 * lo exige para usar la ubicación en segundo plano. Se detiene solo cuando la solicitud deja de ir
 * en camino (llegó, se canceló) o al cerrar sesión.
 */
@AndroidEntryPoint
class TripTrackingService : Service() {

    @Inject lateinit var reporter: TripReporter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reporting: Job? = null
    private var requestId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_REQUEST_ID)
        if (id == null) {
            if (reporting?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundType())
        } catch (_: Exception) {
            // Sin permiso de ubicación o iniciado desde segundo plano: no se puede seguir.
            stopSelf()
            return START_NOT_STICKY
        }

        // Ya manda la de esta misma solicitud: nada que hacer.
        if (id == requestId && reporting?.isActive == true) return START_NOT_STICKY
        reporting?.cancel()
        requestId = id
        reporting = scope.launch {
            reporter.run(id)
            stopSelf()
        }
        // Si Android lo mata no se reinicia solo: al abrir la solicitud de nuevo se reanuda.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0

    private fun buildNotification() = run {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.trip_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.trip_channel_description) }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.fixora_navy_800))
            .setContentTitle(getString(R.string.trip_notification_title))
            .setContentText(getString(R.string.trip_notification_text))
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        const val EXTRA_REQUEST_ID = "com.fixora.app.extra.TRIP_REQUEST_ID"
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 19
    }
}
