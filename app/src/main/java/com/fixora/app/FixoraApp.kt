package com.fixora.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.notifications.PushMessage
import com.fixora.app.core.notifications.RequestNotificationPermission
import com.fixora.app.core.ui.OfflineBanner
import com.fixora.app.feature.intro.ui.IntroScreen
import com.fixora.app.navigation.FixoraNavHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val INTRO_FADE_OUT_MS = 350
private const val LOGOUT_FADE_IN_MS = 200
private const val LOGOUT_FADE_OUT_MS = 400

/**
 * Raíz de la app. La intro se dibuja como una capa encima de la navegación: así la landing
 * se arma desde el arranque, oculta debajo, y al terminar la intro solo hay que desvanecerla,
 * sin el salto de construir la pantalla siguiente en ese momento.
 */
@Composable
fun FixoraApp(
    viewModel: AppViewModel = hiltViewModel(),
    openedNotification: PushMessage? = null,
    onNotificationHandled: () -> Unit = {},
    incomingAlarms: Flow<PushMessage> = emptyFlow(),
    skipIntro: Boolean = false,
) {
    // rememberSaveable: al girar la pantalla la intro no se repite. Si la abrió una alarma no se
    // muestra: hay que ver la solicitud cuanto antes.
    var showIntro by rememberSaveable { mutableStateOf(!skipIntro) }
    val sessionState by viewModel.sessionState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isLoggingOut by viewModel.isLoggingOut.collectAsState()

    // Se pide al terminar la intro para que el diálogo no aparezca encima de la animación.
    RequestNotificationPermission(enabled = sessionState is SessionState.LoggedIn && !showIntro)

    Box(Modifier.fillMaxSize()) {
        // La sesión guardada se lee en milisegundos, mucho antes de que acabe la intro: para
        // entonces ya se sabe si se empieza en la landing o en el inicio.
        if (sessionState != SessionState.Loading) {
            FixoraNavHost(
                homeArea = (sessionState as? SessionState.LoggedIn)?.area,
                openedNotification = openedNotification,
                onNotificationHandled = onNotificationHandled,
                incomingAlarms = incomingAlarms,
            )
        }
        // Al cerrar sesión (ANDROID-44): tapa el salto a la landing, que se arma debajo.
        AnimatedVisibility(
            visible = isLoggingOut,
            enter = fadeIn(tween(LOGOUT_FADE_IN_MS)),
            exit = fadeOut(tween(LOGOUT_FADE_OUT_MS)),
        ) {
            LoggingOutScreen()
        }
        AnimatedVisibility(
            visible = showIntro,
            enter = EnterTransition.None,
            exit = fadeOut(tween(INTRO_FADE_OUT_MS)),
        ) {
            IntroScreen(onFinished = { showIntro = false })
        }
        OfflineBanner(
            visible = !isOnline && !showIntro,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** «Cerrando sesión…» mientras se avisa a la API y se borra la sesión; no deja tocar lo de abajo. */
@Composable
private fun LoggingOutScreen() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Mist50)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Orange500, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            Text(
                stringResource(R.string.logging_out),
                style = MaterialTheme.typography.titleMedium,
                color = Navy900,
            )
        }
    }
}

