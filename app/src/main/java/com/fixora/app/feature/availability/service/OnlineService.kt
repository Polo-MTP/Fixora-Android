package com.fixora.app.feature.availability.service

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
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.data.AvailabilityRepository
import com.fixora.app.feature.availability.data.AvailabilityRepository.Companion.stopsOnline
import com.fixora.app.feature.availability.location.LocationSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Inicia y detiene OnlineService. Interfaz para probar el ViewModel sin Android. */
interface OnlineServiceController {
    fun start()
    fun stop()
}

class AndroidOnlineServiceController(private val context: Context) : OnlineServiceController {
    override fun start() = ContextCompat.startForegroundService(context, Intent(context, OnlineService::class.java))
    override fun stop() {
        context.stopService(Intent(context, OnlineService::class.java))
    }
}

/**
 * Mientras el técnico está En línea (ANDROID-9) manda su posición a la API cada
 * [REPORT_INTERVAL_MS]; la API la olvida a los 5 minutos, así que si el servicio muere el técnico
 * sale solo del mapa. Es un servicio en primer plano con notificación fija: Android lo exige para
 * usar la ubicación con la app en segundo plano. Se inicia siempre con la app abierta.
 */
@AndroidEntryPoint
class OnlineService : Service() {

    @Inject lateinit var repository: AvailabilityRepository

    @Inject lateinit var location: LocationSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reporting: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_GO_OFFLINE) {
            scope.launch {
                repository.goOffline()
                stopSelf()
            }
            return START_NOT_STICKY
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundType())
        } catch (_: SecurityException) {
            // Se quitó el permiso de ubicación: no se puede seguir en línea.
            stopSelf()
            return START_NOT_STICKY
        }

        if (reporting?.isActive != true) {
            reporting = scope.launch {
                location.updates(REPORT_INTERVAL_MS).collect { position ->
                    val result = repository.goOnline(position)
                    // Sucursal cerrada, perfil inactivo o sesión vencida: ya no está en línea.
                    if (result is ApiResult.Failure && result.error.stopsOnline()) stopSelf()
                }
            }
        }
        // Si Android lo mata, no se reinicia solo: sin la app abierta no puede usar la ubicación.
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
            getString(R.string.availability_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.availability_channel_description) }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val goOffline = PendingIntent.getService(
            this,
            1,
            Intent(this, OnlineService::class.java).setAction(ACTION_GO_OFFLINE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.fixora_navy_800))
            .setContentTitle(getString(R.string.availability_notification_title))
            .setContentText(getString(R.string.availability_notification_text))
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.availability_go_offline), goOffline)
            .build()
    }

    companion object {
        /** Menos de la mitad de los 5 minutos que la API guarda la posición. */
        const val REPORT_INTERVAL_MS = 60_000L

        private const val CHANNEL_ID = "online_status"
        private const val NOTIFICATION_ID = 9
        private const val ACTION_GO_OFFLINE = "com.fixora.app.action.GO_OFFLINE"
    }
}
