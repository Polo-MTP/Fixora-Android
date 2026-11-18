package com.fixora.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.notifications.PushMessage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Notificación tocada que abrió (o trajo al frente) la app y aún no se atiende. */
    private var openedNotification by mutableStateOf<PushMessage?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Al recrearse (por ejemplo al girar) el Intent es el mismo: ya se atendió.
        if (savedInstanceState == null) openedNotification = PushMessage.fromExtras(intent.extras)
        setContent {
            FixoraTheme {
                FixoraApp(
                    openedNotification = openedNotification,
                    onNotificationHandled = { openedNotification = null },
                )
            }
        }
    }

    // Con la app abierta (launchMode singleTop) la notificación llega aquí en lugar de onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PushMessage.fromExtras(intent.extras)?.let { openedNotification = it }
    }
}
