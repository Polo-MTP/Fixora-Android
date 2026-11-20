package com.fixora.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.White

private const val RING_ID = "o"

/**
 * Logotipo de texto «Fixora» (Figma): la «o» es un aro naranja del tamaño de una minúscula, pegado
 * a las demás letras y apoyado en la misma línea.
 */
@Composable
fun FixoraLogo(
    modifier: Modifier = Modifier,
    color: Color = White,
    style: TextStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
) {
    val text = buildAnnotatedString {
        append("Fix")
        appendInlineContent(RING_ID, "o")
        append("ra")
    }
    val ring = InlineTextContent(
        // Alto de una minúscula y un poco de aire a los lados, como el ancho de la letra.
        Placeholder(width = 0.62.em, height = 0.56.em, placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.height * 0.24f
            drawCircle(
                color = Orange500,
                radius = (size.height - stroke) / 2,
                style = Stroke(width = stroke),
            )
        }
    }
    Text(
        text = text,
        style = style,
        color = color,
        inlineContent = mapOf(RING_ID to ring),
        modifier = modifier.clearAndSetSemantics { contentDescription = "Fixora" },
    )
}
