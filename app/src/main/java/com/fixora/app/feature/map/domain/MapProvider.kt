package com.fixora.app.feature.map.domain

import com.fixora.app.feature.availability.location.Coordinates
import java.text.Normalizer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.sqrt

/** Sucursal de un proveedor que aparece en el mapa (resultado de /search/branches). */
data class MapProvider(
    val id: String,
    val name: String,
    val businessName: String,
    val independent: Boolean,
    val logoUrl: String?,
    val position: Coordinates,
    val distanceKm: Double,
    val rating: Double,
    val reviewsCount: Int,
    /** Precio base del servicio elegido; null en la búsqueda general o si no lo tiene. */
    val basePrice: String?,
    val openNow: Boolean,
) {
    /** Dos letras para el marcador: las iniciales del negocio o, si es una palabra, sus dos primeras. */
    val initials: String
        get() {
            val words = businessName.ifBlank { name }.split(' ').filter { it.isNotBlank() }
            val letters = if (words.size >= 2) "${words[0].first()}${words[1].first()}" else words.firstOrNull()?.take(2).orEmpty()
            return letters.uppercase()
        }

    fun matches(query: String): Boolean {
        val needle = query.normalized()
        return needle.isEmpty() || name.normalized().contains(needle) || businessName.normalized().contains(needle)
    }
}

/** Servicio del catálogo (Plomería, Electricidad...). */
data class ServiceCategory(val id: String, val name: String, val slug: String) {
    fun matches(query: String): Boolean {
        val needle = query.normalized()
        return needle.isNotEmpty() && name.normalized().contains(needle)
    }
}

/** Filtros del mapa que manda la API. */
data class MapFilters(
    val radiusKm: Int = MapRules.DEFAULT_RADIUS_KM,
    val availableNow: Boolean = false,
    /** Calificación promedio mínima; null es cualquiera. Sin reseñas cuenta como 0. */
    val minRating: Double? = null,
    /** Precio base máximo en pesos; null es cualquiera. */
    val maxPrice: Int? = null,
)

data class MapSearchResult(val providers: List<MapProvider>, val total: Int)

object MapRules {
    const val DEFAULT_RADIUS_KM = 10
    val RADIUS_OPTIONS_KM = listOf(2, 5, 10, 20, 50)
    val RATING_OPTIONS = listOf(3.0, 4.0, 4.5)
    val MAX_PRICE_OPTIONS = listOf(300, 500, 800, 1200)

    /** La API regresa como máximo 50 por página; en el mapa se piden los más cercanos. */
    const val MAX_RESULTS = 50

    /** Centro cuando no hay permiso de ubicación o no se pudo obtener: el Zócalo de la CDMX. */
    val DEFAULT_CENTER = Coordinates(19.4326, -99.1332)

    /**
     * Acercamiento para que el radio quepa en la pantalla de un celular. Cada nivel de zoom es el
     * doble de cerca: con 10 km queda en 10, con 1 km en 13.3.
     */
    fun zoomFor(radiusKm: Int): Double = (13.3 - log2(radiusKm.toDouble())).coerceIn(3.0, 16.0)

    /** Si el mapa se movió más que esto desde donde se buscó, se ofrece «Buscar en esta zona». */
    fun movedEnough(origin: Coordinates, center: Coordinates, radiusKm: Int): Boolean =
        distanceKm(origin, center) > radiusKm * 0.3

    /** Distancia en línea recta (fórmula de Haversine), igual que la API. */
    fun distanceKm(a: Coordinates, b: Coordinates): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * earthRadiusKm * asin(sqrt(h))
    }
}

/** Minúsculas y sin acentos, para que «plomeria» encuentre «Plomería». */
internal fun String.normalized(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
