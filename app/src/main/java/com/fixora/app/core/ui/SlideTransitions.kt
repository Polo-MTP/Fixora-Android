package com.fixora.app.core.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

// Transiciones entre pantallas: al avanzar, la nueva entra completa desde la derecha y la
// anterior se recorre un poco a la izquierda; al regresar, al revés. La pantalla que va encima
// se mueve sólida y solo la de abajo se oscurece un poco, para dar sensación de profundidad.
private const val SLIDE_MS = 420
private const val UNDER_ALPHA = 0.6f
private val slideSpec = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)
private val fadeSpec = tween<Float>(SLIDE_MS, easing = FastOutSlowInEasing)

fun slideForwardEnter(): EnterTransition = slideInHorizontally(slideSpec) { it }

fun slideForwardExit(): ExitTransition =
    slideOutHorizontally(slideSpec) { -it / 4 } + fadeOut(fadeSpec, targetAlpha = UNDER_ALPHA)

fun slideBackwardEnter(): EnterTransition =
    slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(fadeSpec, initialAlpha = UNDER_ALPHA)

fun slideBackwardExit(): ExitTransition = slideOutHorizontally(slideSpec) { it }
