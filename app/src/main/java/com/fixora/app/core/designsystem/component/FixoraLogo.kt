package com.fixora.app.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.White

/** Logotipo de texto «Fixora» con la «o» resaltada en naranja. */
@Composable
fun FixoraLogo(
    modifier: Modifier = Modifier,
    color: Color = White,
) {
    val style = MaterialTheme.typography.headlineMedium
    Row(modifier.clearAndSetSemantics { contentDescription = "Fixora" }) {
        Text("Fix", style = style, color = color)
        Text("o", style = style, color = Orange500, modifier = Modifier.padding(horizontal = 4.dp))
        Text("ra", style = style, color = color)
    }
}
