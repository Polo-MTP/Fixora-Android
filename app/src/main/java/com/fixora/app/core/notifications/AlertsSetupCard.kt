package com.fixora.app.core.notifications

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500

/** Qué le falta al celular para que suene y se vea la alarma de solicitud entrante. */
private enum class MissingAlert { Notifications, FullScreen }

/**
 * Tarjeta del inicio del proveedor (ANDROID-13) que aparece solo si falta un permiso para las
 * alarmas: las notificaciones o, en Android 14+, mostrar a pantalla completa. «Activar» lleva al
 * ajuste del sistema y al volver se revisa otra vez.
 */
@Composable
fun AlertsSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(missingAlert(context)) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) missing = missingAlert(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val current = missing ?: return
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Mist200, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(Orange500.copy(alpha = 0.1f), CircleShape),
        ) {
            Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = Orange500, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.alerts_setup_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Navy900,
            )
            Text(
                stringResource(
                    when (current) {
                        MissingAlert.Notifications -> R.string.alerts_setup_notifications
                        MissingAlert.FullScreen -> R.string.alerts_setup_full_screen
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
            FixoraTextLink(
                text = stringResource(R.string.alerts_setup_action),
                onClick = { openSettings(context, current) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun missingAlert(context: Context): MissingAlert? = when {
    !NotificationManagerCompat.from(context).areNotificationsEnabled() -> MissingAlert.Notifications
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        !NotificationManagerCompat.from(context).canUseFullScreenIntent() -> MissingAlert.FullScreen
    else -> null
}

private fun openSettings(context: Context, missing: MissingAlert) {
    val intent = when {
        missing == MissingAlert.FullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
        else -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Algunos fabricantes no tienen ese ajuste: se abre la ficha de la app.
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
}
