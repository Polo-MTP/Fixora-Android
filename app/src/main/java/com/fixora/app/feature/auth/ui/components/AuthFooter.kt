package com.fixora.app.feature.auth.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.Slate500

/** Pie de las pantallas de cuenta: una pregunta y un enlace, por ejemplo «¿No tienes cuenta? Regístrate». */
@Composable
fun AuthFooter(
    question: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("$question ", style = MaterialTheme.typography.bodyMedium, color = Slate500)
        FixoraTextLink(action, onAction)
    }
}
