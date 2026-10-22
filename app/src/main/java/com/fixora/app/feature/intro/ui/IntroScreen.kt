package com.fixora.app.feature.intro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

// Línea de tiempo de la intro (ms), basada en el video de referencia y comprimida a 2 s:
// aparece el punto, de él brota el pin, se dibuja la llave, pasa un destello y,
// al final, el pin crece hasta llenar la pantalla con el azul de la landing.
private object Timeline {
    const val TOTAL = 2000f
    const val DOT_START = 0f
    const val DOT_END = 300f
    const val PIN_START = 250f
    const val PIN_END = 850f
    const val WRENCH_START = 750f
    const val WRENCH_END = 1200f
    const val SHINE_START = 1150f
    const val SHINE_END = 1550f
    const val EXIT_START = 1600f
    const val EXIT_FADE_END = 1720f
}

private val LogoWidth = 140.dp

/**
 * Intro animada al abrir la app. Empieza en blanco, igual que el splash del sistema,
 * y termina con la pantalla en el azul de la landing para que el cambio no se note.
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

    Canvas(
        modifier
            .fillMaxSize()
            .background(White)
            .semantics { contentDescription = "Fixora" },
    ) {
        drawIntroFrame(isotipo, timeMs())
    }
}

private fun DrawScope.drawIntroFrame(isotipo: Isotipo, t: Float) {
    // Escala y posición del logo: centrado, con LogoWidth de ancho.
    val unit = LogoWidth.toPx() / isotipo.width
    val origin = Offset(
        (size.width - isotipo.width * unit) / 2,
        (size.height - isotipo.height * unit) / 2,
    )

    val dotScale = phase(t, Timeline.DOT_START, Timeline.DOT_END, EaseOutBack)
    val pinGrow = phase(t, Timeline.PIN_START, Timeline.PIN_END, EaseOutBack)
    val wrenchReveal = phase(t, Timeline.WRENCH_START, Timeline.WRENCH_END, EaseInOutCubic)
    val shine = phase(t, Timeline.SHINE_START, Timeline.SHINE_END, LinearEasing)
    val exit = phase(t, Timeline.EXIT_START, Timeline.TOTAL, EaseInCubic)
    val detailsAlpha = 1f - phase(t, Timeline.EXIT_START, Timeline.EXIT_FADE_END, LinearEasing)

    // Al salir, el pin crece desde el centro de su círculo hasta cubrir la esquina más lejana.
    val pinCenterPx = origin + isotipo.pinCenter * unit
    val farthest = maxOf(
        hypot(pinCenterPx.x, pinCenterPx.y),
        hypot(size.width - pinCenterPx.x, pinCenterPx.y),
        hypot(pinCenterPx.x, size.height - pinCenterPx.y),
        hypot(size.width - pinCenterPx.x, size.height - pinCenterPx.y),
    )
    val exitScale = lerp(1f, max(1f, farthest / (isotipo.pinRadius * unit)) * 1.05f, exit)

    translate(origin.x, origin.y) {
        scale(unit, unit, pivot = Offset.Zero) {
            scale(exitScale, exitScale, pivot = isotipo.pinCenter) {
                // Pin: brota hacia arriba desde el punto naranja.
                if (pinGrow > 0f) {
                    scale(lerp(0.2f, 1f, pinGrow), pinGrow, pivot = isotipo.dotCenter) {
                        drawPath(isotipo.pin, Navy800)
                    }
                }

                if (detailsAlpha > 0f) {
                    // Llave: se dibuja de abajo hacia arriba.
                    if (wrenchReveal > 0f) {
                        clipRect(top = lerp(isotipo.height, 0f, wrenchReveal), bottom = isotipo.height + 4f) {
                            drawPath(isotipo.wrench, White, alpha = detailsAlpha)
                        }
                    }
                    // Destello diagonal que cruza el pin.
                    if (shine in 0.001f..0.999f) {
                        clipPath(isotipo.pin) { drawShine(isotipo, shine, detailsAlpha) }
                    }
                }
                // Al salir, el punto se vuelve azul en lugar de desaparecer, para que el pin
                // no se vea con la base cortada mientras crece.
                drawDot(isotipo, dotScale, glow = bump(shine), color = lerp(Navy800, Orange500, detailsAlpha))
            }
        }
    }
}

private fun DrawScope.drawShine(isotipo: Isotipo, progress: Float, alpha: Float) {
    val band = isotipo.width * 0.35f
    val x = lerp(-band * 2, isotipo.width + band, progress)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, White.copy(alpha = 0.45f * alpha), Color.Transparent),
            start = Offset(x, 0f),
            end = Offset(x + band, band * 0.6f),
        ),
        size = isotipo.size,
    )
}

private fun DrawScope.drawDot(isotipo: Isotipo, scale: Float, glow: Float, color: Color) {
    if (scale <= 0f) return
    val radius = isotipo.dotRadius * scale
    if (glow > 0f) {
        val glowRadius = radius * 2.2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Orange500.copy(alpha = 0.6f * glow), Color.Transparent),
                center = isotipo.dotCenter,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = isotipo.dotCenter,
        )
    }
    drawCircle(color, radius, isotipo.dotCenter)
}

/** Avance de 0 a 1 de una fase de la línea de tiempo, con su curva de aceleración. */
private fun phase(t: Float, start: Float, end: Float, easing: Easing): Float =
    if (t <= start) 0f else easing.transform(((t - start) / (end - start)).coerceAtMost(1f))

/** Sube y baja: 0 → 1 → 0 a lo largo de una fase. */
private fun bump(progress: Float): Float = if (progress <= 0f || progress >= 1f) 0f else 1f - abs(2 * progress - 1)

/** Capas del isotipo leídas de ic_fixora_isotipo.xml por su nombre (pin, wrench, dot). */
private class Isotipo(
    val pin: Path,
    val wrench: Path,
    val width: Float,
    val height: Float,
    val dotCenter: Offset,
    val dotRadius: Float,
) {
    val size get() = Size(width, height)
    private val pinBounds: Rect = pin.getBounds()
    val pinRadius get() = pinBounds.width / 2
    val pinCenter get() = Offset(pinBounds.center.x, pinBounds.top + pinRadius)

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
