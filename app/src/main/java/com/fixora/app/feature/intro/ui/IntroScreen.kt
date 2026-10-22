package com.fixora.app.feature.intro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.SystemBarsAppearance
import kotlin.math.hypot

// Línea de tiempo de la intro (ms), basada en el video de referencia:
// aparece el punto, de él brota el pin, se dibuja la llave y pasa un destello.
// Al final el logo se desvanece y un círculo azul se abre desde el punto hasta cubrir
// la pantalla con el azul de la landing.
private object Timeline {
    const val DOT_START = 0f
    const val DOT_END = 370f
    const val PIN_START = 310f
    const val PIN_END = 1050f
    const val WRENCH_START = 920f
    const val WRENCH_END = 1480f
    const val SHINE_START = 1420f
    const val SHINE_END = 1910f
    const val LOGO_FADE_START = 2030f
    const val LOGO_FADE_END = 2300f
    const val REVEAL_START = 2130f
    const val TOTAL = 2650f
}

private val LogoWidth = 140.dp

/**
 * Intro animada al abrir la app. Empieza en blanco, igual que el splash del sistema,
 * y termina cubriendo la pantalla con el azul de la landing. Se dibuja encima de la
 * landing (ver FixoraApp), que ya está lista debajo cuando la intro se desvanece.
 * Si el usuario desactivó las animaciones en Android, dura 0 ms y pasa directo.
 */
@Composable
fun IntroScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SystemBarsAppearance(lightIcons = false)

    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        time.animateTo(Timeline.TOTAL, tween(Timeline.TOTAL.toInt(), easing = LinearEasing))
        onFinished()
    }

    IntroLogo(timeMs = { time.value }, modifier = modifier)
}

@Composable
private fun IntroLogo(timeMs: () -> Float, modifier: Modifier = Modifier) {
    val vector = ImageVector.vectorResource(R.drawable.ic_fixora_isotipo)
    val isotipo = remember(vector) { Isotipo.from(vector) }

    Box(
        modifier
            .fillMaxSize()
            .background(White)
            .semantics { contentDescription = "Fixora" },
    ) {
        // El logo se desvanece y se encoge un poco antes de que se abra el círculo.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val fade = phase(timeMs(), Timeline.LOGO_FADE_START, Timeline.LOGO_FADE_END, EaseInOutCubic)
                    alpha = 1f - fade
                    scaleX = lerp(1f, 0.92f, fade)
                    scaleY = scaleX
                },
        ) { drawLogo(isotipo, timeMs()) }

        Canvas(Modifier.fillMaxSize()) { drawReveal(isotipo, timeMs()) }
    }
}

/** Escala y posición del logo: centrado en la pantalla, con LogoWidth de ancho. */
private fun DrawScope.logoUnit(isotipo: Isotipo) = LogoWidth.toPx() / isotipo.width

private fun DrawScope.logoOrigin(isotipo: Isotipo, unit: Float) = Offset(
    (size.width - isotipo.width * unit) / 2,
    (size.height - isotipo.height * unit) / 2,
)

private fun DrawScope.drawLogo(isotipo: Isotipo, t: Float) {
    val unit = logoUnit(isotipo)
    val origin = logoOrigin(isotipo, unit)
    val dotScale = phase(t, Timeline.DOT_START, Timeline.DOT_END, EaseOutBack)
    val pinGrow = phase(t, Timeline.PIN_START, Timeline.PIN_END, EaseOutBack)
    val wrenchReveal = phase(t, Timeline.WRENCH_START, Timeline.WRENCH_END, EaseInOutCubic)
    val shine = phase(t, Timeline.SHINE_START, Timeline.SHINE_END, LinearEasing)

    translate(origin.x, origin.y) {
        scale(unit, unit, pivot = Offset.Zero) {
            // Pin: brota hacia arriba desde el punto naranja.
            if (pinGrow > 0f) {
                scale(lerp(0.2f, 1f, pinGrow), pinGrow, pivot = isotipo.dotCenter) {
                    drawPath(isotipo.pin, Navy800)
                }
            }
            // Llave: se dibuja de abajo hacia arriba.
            if (wrenchReveal > 0f) {
                clipRect(top = lerp(isotipo.height, 0f, wrenchReveal), bottom = isotipo.height + 4f) {
                    drawPath(isotipo.wrench, White)
                }
            }
            // Destello diagonal que cruza el pin.
            if (shine in 0.001f..0.999f) {
                clipPath(isotipo.pin) { drawShine(isotipo, shine) }
            }
            if (dotScale > 0f) {
                drawCircle(Orange500, isotipo.dotRadius * dotScale, isotipo.dotCenter)
            }
        }
    }
}

/** Círculo azul que se abre desde el punto naranja hasta cubrir la esquina más lejana. */
private fun DrawScope.drawReveal(isotipo: Isotipo, t: Float) {
    val reveal = phase(t, Timeline.REVEAL_START, Timeline.TOTAL, EaseInOutCubic)
    if (reveal <= 0f) return
    val unit = logoUnit(isotipo)
    val center = logoOrigin(isotipo, unit) + isotipo.dotCenter * unit
    val farthest = maxOf(
        hypot(center.x, center.y),
        hypot(size.width - center.x, center.y),
        hypot(center.x, size.height - center.y),
        hypot(size.width - center.x, size.height - center.y),
    )
    drawCircle(Navy800, radius = farthest * reveal, center = center)
}

private fun DrawScope.drawShine(isotipo: Isotipo, progress: Float) {
    val band = isotipo.width * 0.35f
    val x = lerp(-band * 2, isotipo.width + band, progress)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, White.copy(alpha = 0.35f), Color.Transparent),
            start = Offset(x, 0f),
            end = Offset(x + band, band * 0.6f),
        ),
        size = Size(isotipo.width, isotipo.height),
    )
}

/** Avance de 0 a 1 de una fase de la línea de tiempo, con su curva de aceleración. */
private fun phase(t: Float, start: Float, end: Float, easing: Easing): Float =
    if (t <= start) 0f else easing.transform(((t - start) / (end - start)).coerceAtMost(1f))

/** Capas del isotipo leídas de ic_fixora_isotipo.xml por su nombre (pin, wrench, dot). */
private class Isotipo(
    val pin: Path,
    val wrench: Path,
    val width: Float,
    val height: Float,
    val dotCenter: Offset,
    val dotRadius: Float,
) {
    companion object {
        fun from(vector: ImageVector): Isotipo {
            val paths = vector.root.filterIsInstance<VectorPath>().associateBy { it.name }
            fun layer(name: String) = paths.getValue(name).let { p -> p.pathData.toPath().apply { fillType = p.pathFillType } }
            val dot = layer("dot").getBounds()
            return Isotipo(
                pin = layer("pin"),
                wrench = layer("wrench"),
                width = vector.viewportWidth,
                height = vector.viewportHeight,
                dotCenter = dot.center,
                dotRadius = dot.width / 2,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun IntroLogoPreview() {
    FixoraTheme {
        IntroLogo(timeMs = { Timeline.SHINE_END })
    }
}
