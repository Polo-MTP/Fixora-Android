package com.fixora.app.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.White

/**
 * Campana de los inicios (Figma «Inicio»): círculo blanco con el número de no leídas en naranja.
 * Abre el centro de notificaciones (ANDROID-14) y vuelve a contar al regresar al inicio.
 */
@Composable
fun NotificationBell(onClick: () -> Unit, modifier: Modifier = Modifier, viewModel: NotificationBellViewModel = hiltViewModel()) {
    val unread by viewModel.unreadCount.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    NotificationBellButton(unread, onClick, modifier)
}

@Composable
internal fun NotificationBellButton(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = if (unread > 0) {
        pluralStringResource(R.plurals.notifications_bell_unread, unread, unread)
    } else {
        stringResource(R.string.notifications_title)
    }
    Box(modifier.size(48.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(White)
                .border(1.dp, Mist200, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        ) {
            Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = Navy900, modifier = Modifier.size(22.dp))
        }
        if (unread > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                    .background(Orange500, CircleShape)
                    .border(2.dp, White, CircleShape)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    if (unread > 99) "99+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = White,
                )
            }
        }
    }
}
