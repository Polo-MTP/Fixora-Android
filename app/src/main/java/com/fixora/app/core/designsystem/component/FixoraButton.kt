package com.fixora.app.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Orange200
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.White

/**
 * Botón de la acción principal de una pantalla: naranja, a todo lo ancho. Con [loading] muestra
 * un indicador en lugar del texto e ignora los toques, para no mandar la misma acción dos veces.
 */
@Composable
fun FixoraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Orange500,
            contentColor = White,
            disabledContainerColor = Orange200,
            disabledContentColor = White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp, disabledElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Enlace de texto subrayado, para acciones secundarias como «Regístrate». */
@Composable
fun FixoraTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Navy800,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
        ),
        color = color,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    )
}
