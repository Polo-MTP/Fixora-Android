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
    headlineLarge = base.headlineLarge.jakarta(),
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
    titleMedium = base.titleMedium.jakarta(),
    titleSmall = base.titleSmall.jakarta(),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.5.sp,
    ),
    bodyMedium = base.bodyMedium.jakarta(),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 21.94.sp,
    ),
    labelLarge = base.labelLarge.jakarta(),
    labelMedium = base.labelMedium.jakarta(),
    labelSmall = base.labelSmall.jakarta(),
)
