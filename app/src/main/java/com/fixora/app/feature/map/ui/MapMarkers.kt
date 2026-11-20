package com.fixora.app.feature.map.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.feature.map.domain.MapProvider
import com.fixora.app.feature.map.domain.ServiceCategory

/** Ícono de cada servicio por su slug; los que no tienen uno propio usan la llave inglesa. */
@DrawableRes
fun ServiceCategory.iconRes(): Int = when (slug) {
    "plomeria" -> R.drawable.ic_service_plumbing
    "electricidad" -> R.drawable.ic_service_electric
    "cerrajeria" -> R.drawable.ic_service_locksmith
    "climatizacion" -> R.drawable.ic_service_climate
    "pintura-impermeabilizacion" -> R.drawable.ic_service_paint
    "albanileria" -> R.drawable.ic_service_masonry
    "electrodomesticos" -> R.drawable.ic_service_appliances
    "jardineria" -> R.drawable.ic_service_gardening
    "limpieza-desinfeccion" -> R.drawable.ic_service_cleaning
    else -> R.drawable.ic_service_general
}

/**
 * Cómo se ve un marcador (Figma «Fixora - Mapa»): círculo azul marino para las empresas y naranja
 * para los técnicos independientes, con el ícono del servicio elegido o, en «Todos», las iniciales.
 * El elegido lleva un anillo naranja y es más grande.
 */
data class MarkerLook(
    @param:DrawableRes val icon: Int?,
    val initials: String,
    val independent: Boolean,
    val selected: Boolean,
) {
    /** Nombre de la imagen dentro del estilo del mapa; la misma apariencia reutiliza la imagen. */
    val imageId: String get() = "pin-${icon ?: initials}-${if (independent) "i" else "e"}-${if (selected) "s" else "n"}"
}

fun MapProvider.markerLook(category: ServiceCategory?, selected: Boolean) = MarkerLook(
    icon = category?.iconRes(),
    initials = initials,
    independent = independent,
    selected = selected,
)

/** Dibuja el marcador como imagen para MapLibre. */
fun MarkerLook.toBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val circle = 36f * density
    val border = 2.5f * density
    val ring = if (selected) 4f * density else 0f
    val size = (circle + ring * 2 + 4 * density).toInt()
    val center = size / 2f
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Sombra suave para que se distinga sobre calles claras.
    paint.color = 0x33000000
    canvas.drawCircle(center, center + density, circle / 2 + ring, paint)
    if (selected) {
        paint.color = Orange500.toArgb()
        canvas.drawCircle(center, center, circle / 2 + ring, paint)
    }
    paint.color = White.toArgb()
    canvas.drawCircle(center, center, circle / 2, paint)
    paint.color = (if (independent) Orange500 else Navy900).toArgb()
    canvas.drawCircle(center, center, circle / 2 - border, paint)

    val iconRes = icon
    if (iconRes != null) {
        val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate() ?: return bitmap
        drawable.setTint(White.toArgb())
        val half = (10 * density).toInt()
        drawable.setBounds((center - half).toInt(), (center - half).toInt(), (center + half).toInt(), (center + half).toInt())
        drawable.draw(canvas)
    } else {
        paint.color = White.toArgb()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13 * density
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(initials, center, center - (paint.descent() + paint.ascent()) / 2, paint)
    }
    return bitmap
}
