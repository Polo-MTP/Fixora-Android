package com.fixora.app.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Sin modo oscuro ni colores dinámicos: el diseño solo define la paleta de marca.
private val FixoraColorScheme = lightColorScheme(
    primary = Orange500,
    onPrimary = White,
    secondary = Navy800,
    onSecondary = White,
    background = White,
    onBackground = Navy900,
    surface = White,
    onSurface = Navy900,
)

@Composable
fun FixoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FixoraColorScheme,
        typography = Typography,
        content = content,
    )
}
