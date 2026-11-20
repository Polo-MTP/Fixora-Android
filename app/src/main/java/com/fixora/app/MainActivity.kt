package com.fixora.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.notifications.AppVisibility
import com.fixora.app.core.notifications.IncomingAlarms
import com.fixora.app.core.notifications.PushMessage
import com.fixora.app.core.notifications.PushNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var notifier: PushNotifier

    @Inject lateinit var incomingAlarms: IncomingAlarms

    /** Notificación tocada que abrió (o trajo al frente) la app y aún no se atiende. */
    private var openedNotification by mutableStateOf<PushMessage?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openedByAlarm = handleAlarm(intent)
        // Al recrearse (por ejemplo al girar) el Intent es el mismo: ya se atendió.
        if (savedInstanceState == null) openedNotification = PushMessage.fromExtras(intent.extras)
        setContent {
            FixoraTheme {
                FixoraApp(
                    openedNotification = openedNotification,
                    onNotificationHandled = { openedNotification = null },
                    incomingAlarms = incomingAlarms.incoming,
                    skipIntro = openedByAlarm,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isVisible = true
    }

    override fun onStop() {
        AppVisibility.isVisible = false
        super.onStop()
    }

    // Con la app abierta (launchMode singleTop) la notificación llega aquí en lugar de onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarm(intent)
        PushMessage.fromExtras(intent.extras)?.let { openedNotification = it }
    }

    /**
     * Si la abrió una alarma de solicitud entrante (ANDROID-13): se muestra encima de la pantalla
     * bloqueada, enciende la pantalla y la alarma deja de sonar porque ya se atendió.
     */
    private fun handleAlarm(intent: Intent): Boolean {
        val alarmId = intent.getIntExtra(PushNotifier.EXTRA_ALARM_NOTIFICATION_ID, NO_ALARM)
        if (alarmId == NO_ALARM) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        // La pantalla de alarma sigue sonando hasta que se responde.
        notifier.cancelAlarm(alarmId)
        return true
    }

    private companion object {
        const val NO_ALARM = -1
    }
}
