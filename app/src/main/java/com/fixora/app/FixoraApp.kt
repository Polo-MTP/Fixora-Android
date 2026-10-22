package com.fixora.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fixora.app.feature.intro.ui.IntroScreen
import com.fixora.app.navigation.FixoraNavHost

private const val INTRO_FADE_OUT_MS = 350

/**
 * Raíz de la app. La intro se dibuja como una capa encima de la navegación: así la landing
 * se arma desde el arranque, oculta debajo, y al terminar la intro solo hay que desvanecerla,
 * sin el salto de construir la pantalla siguiente en ese momento.
 */
@Composable
fun FixoraApp() {
    // rememberSaveable: al girar la pantalla la intro no se repite.
    var showIntro by rememberSaveable { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        FixoraNavHost()
        AnimatedVisibility(
            visible = showIntro,
            enter = EnterTransition.None,
            exit = fadeOut(tween(INTRO_FADE_OUT_MS)),
        ) {
            IntroScreen(onFinished = { showIntro = false })
        }
    }
}
