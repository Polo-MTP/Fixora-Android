package com.fixora.app.feature.auth.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy200
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.SystemBarsAppearance

// La hoja clara se monta sobre la cabecera azul y la tapa con sus esquinas redondeadas.
private val SheetOverlap = 22.dp

/** Paso actual de un flujo de varias pantallas, para la barra de progreso de la cabecera. */
data class AuthStep(val current: Int, val total: Int)

/**
 * Estructura común de las pantallas de cuenta (diseño A): cabecera azul con las líneas de la
 * landing, botón de regresar, título y subtítulo; abajo, una hoja clara con el formulario.
 * El [footer] queda al fondo de la pantalla cuando el contenido cabe, y al final si hay scroll.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: AnnotatedString,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    step: AuthStep? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SystemBarsAppearance(lightIcons = true)
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Mist50),
    ) {
        val screenHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AuthHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                step = step,
                modifier = Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
            )
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .offset(y = -SheetOverlap)
                    .fillMaxWidth()
                    .heightIn(min = (screenHeight - headerHeight).coerceAtLeast(0.dp))
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(Mist50)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 28.dp + SheetOverlap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                if (footer != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    ) { footer() }
                }
            }
        }
    }
}

@Composable
private fun AuthHeader(
    title: String,
    subtitle: AnnotatedString,
    onBack: () -> Unit,
    step: AuthStep?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Navy800),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_landing_lines),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            alpha = 0.6f,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 34.dp + SheetOverlap),
        ) {
            BackButton(onBack)
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = White)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Navy200)
            if (step != null) {
                Spacer(Modifier.height(20.dp))
                StepProgress(step)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.auth_step, step.current, step.total),
                    style = MaterialTheme.typography.labelSmall,
                    color = Navy200,
                )
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(White.copy(alpha = 0.1f))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        // Reutiliza la flecha de la landing, girada hacia la izquierda.
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = stringResource(R.string.auth_back),
            tint = White,
            modifier = Modifier
                .size(22.dp)
                .rotate(180f),
        )
    }
}

@Composable
private fun StepProgress(step: AuthStep) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(step.total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index < step.current) Orange500 else White.copy(alpha = 0.18f)),
            )
        }
    }
}
