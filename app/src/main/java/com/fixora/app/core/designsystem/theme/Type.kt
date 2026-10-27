package com.fixora.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fixora.app.R

// Plus Jakarta Sans es una fuente variable: un solo archivo cubre todos los pesos.
@OptIn(ExperimentalTextApi::class)
private fun plusJakartaSans(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val PlusJakartaSans = FontFamily(
    listOf(
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
    ).map(::plusJakartaSans),
)

private val base = Typography()

private fun TextStyle.jakarta() = copy(fontFamily = PlusJakartaSans)

val Typography = Typography(
    displayLarge = base.displayLarge.jakarta(),
    displayMedium = base.displayMedium.jakarta(),
    // Título principal de la landing
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 42.56.sp,
        letterSpacing = (-0.95).sp,
    ),
    // Título de las pantallas de cuenta (login, registro)
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.5.sp,
        letterSpacing = (-0.75).sp,
    ),
    // Logotipo
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 31.sp,
        lineHeight = 46.5.sp,
        letterSpacing = (-0.775).sp,
    ),
    headlineSmall = base.headlineSmall.jakarta(),
    // Texto de botones principales
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 28.5.sp,
        letterSpacing = (-0.475).sp,
    ),
    // Texto de los botones de formulario
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleSmall = base.titleSmall.jakarta(),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.5.sp,
    ),
    // Subtítulos y textos de apoyo
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.75.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 21.94.sp,
    ),
    // Etiquetas de los campos
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = base.labelMedium.jakarta(),
    // Errores, requisitos y textos pequeños
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
