package com.fixora.app.feature.auth.ui.landing

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraLogo
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy200
import com.fixora.app.core.designsystem.theme.Navy300
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.OrangeSoft
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.SystemBarsAppearance

// En el diseño el radar queda centrado a un 35 % de la altura de la pantalla.
private val RadarAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.31f)

@Composable
fun LandingScreen(
    onStartClick: () -> Unit,
    onBecomeProviderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SystemBarsAppearance(lightIcons = true)

    Box(
        modifier
            .fillMaxSize()
            .background(Navy800),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_landing_lines),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        RadarBeacon(Modifier.align(RadarAlignment))

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 40.dp),
        ) {
            FixoraLogo()
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.landing_title),
                style = MaterialTheme.typography.displaySmall,
                color = White,
            )
            Spacer(Modifier.height(15.dp))
            Text(
                text = stringResource(R.string.landing_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Navy200,
            )
            Spacer(Modifier.height(56.dp))
            StartButton(onClick = onStartClick)
            ProviderLink(
                onClick = onBecomeProviderClick,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
    }
}

/**
 * Radar con el punto naranja: representa a los técnicos cercanos.
 * El punto "respira" como una señal de rastreo: el brillo se apaga casi por completo
 * mientras el punto se atenúa y encoge un poco, sin desaparecer.
 */
@Composable
private fun RadarBeacon(modifier: Modifier = Modifier) {
    // 0 = encendido, 1 = atenuado. Se lee dentro de las lambdas de dibujo para no recomponer en cada frame.
    val pulse = rememberInfiniteTransition(label = "radarBlink").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier
            .size(112.dp)
            .background(OrangeSoft.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, OrangeSoft.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(Orange500.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .drawBehind {
                        val glowAlpha = lerp(0.8f, 0.08f, pulse.value)
                        val glowRadius = size.minDimension / 2 + 12.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Orange500.copy(alpha = glowAlpha), Color.Transparent),
                                radius = glowRadius,
                            ),
                            radius = glowRadius,
                        )
                    }
                    .graphicsLayer {
                        val scale = lerp(1f, 0.85f, pulse.value)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(1f, 0.45f, pulse.value)
                    }
                    .background(Orange500, CircleShape),
            )
        }
    }
}

@Composable
private fun StartButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Navy900),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 4.dp),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.landing_start),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
        )
    }
}

@Composable
private fun ProviderLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val question = stringResource(R.string.landing_provider_question)
    val link = stringResource(R.string.landing_provider_link)
    val linkStyle = TextLinkStyles(
        SpanStyle(
            color = White,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = Navy300)) { append("$question ") }
        withLink(LinkAnnotation.Clickable("provider", linkStyle) { onClick() }) { append(link) }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LandingScreenPreview() {
    FixoraTheme {
        LandingScreen(onStartClick = {}, onBecomeProviderClick = {})
    }
}
