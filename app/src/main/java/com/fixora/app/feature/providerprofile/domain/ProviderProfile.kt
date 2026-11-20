package com.fixora.app.feature.providerprofile.domain

import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.portfolio.domain.PortfolioWork

/** Ficha pública de una sucursal: lo que ve el cliente al tocar un proveedor (ANDROID-11). */
data class ProviderProfile(
    val id: String,
    val name: String,
    val businessName: String,
    val independent: Boolean,
    val description: String,
    val logoUrl: String?,
    /** Su solicitud de proveedor fue aprobada por Fixora. */
    val verified: Boolean,
    val plates: List<String>,
    val rating: Double,
    val reviewsCount: Int,
    val totalJobs: Int,
    val acceptingRequests: Boolean,
    val colony: String,
    val municipality: String,
    val position: Coordinates?,
    val schedules: List<DaySchedule>,
    val services: List<ProviderService>,
    val works: List<PortfolioWork>,
    val certificates: List<PublicCertificate>,
    /** Las más recientes primero; la API manda como máximo 5 aunque [reviewsCount] sea mayor. */
    val latestReviews: List<Review> = emptyList(),
) {
    val title: String get() = businessName.ifBlank { name }
    val hasReviews: Boolean get() = reviewsCount > 0

    /** Precio base del servicio elegido en el mapa o, si no hay, el más barato que ofrece. */
    fun basePriceFor(categoryId: String?): BasePrice? {
        services.firstOrNull { it.id == categoryId }?.let { return BasePrice(it.basePrice, isMinimum = false) }
        val cheapest = services.minByOrNull { it.basePrice.toDoubleOrNull() ?: Double.MAX_VALUE } ?: return null
        return BasePrice(cheapest.basePrice, isMinimum = services.size > 1)
    }
}

/** Reseña de un cliente; [comment] vacío si solo dejó estrellas. */
data class Review(val rating: Int, val comment: String, val author: String)

/** [isMinimum]: es el más barato de varios servicios, se muestra como «Desde». */
data class BasePrice(val amount: String, val isMinimum: Boolean)

data class ProviderService(
    val id: String,
    val name: String,
    /** Con dos decimales, como lo manda la API: «250.00». */
    val basePrice: String,
    val durationMinutes: Int,
)

/** Certificado aprobado que el proveedor eligió mostrar; puede ser PDF o foto. */
data class PublicCertificate(val id: String, val name: String, val url: String, val isPdf: Boolean)

/** Horario de un día; [dayOfWeek] va de 0 (domingo) a 6 (sábado), como en la API. */
data class DaySchedule(val dayOfWeek: Int, val open: String, val close: String)

/** Días seguidos con el mismo horario, por ejemplo «lunes a viernes de 8:00 a 18:00». */
data class ScheduleGroup(val firstDay: Int, val lastDay: Int, val open: String, val close: String) {
    val allDay: Boolean get() = open == "00:00" && (close == "23:59" || close == "24:00")
    val singleDay: Boolean get() = firstDay == lastDay
}

object ScheduleRules {
    /** Orden de la semana para el cliente: de lunes (1) a domingo (0). */
    private val WEEK = listOf(1, 2, 3, 4, 5, 6, 0)

    /** Junta los días seguidos que abren igual. Sin horario activo regresa una lista vacía. */
    fun group(schedules: List<DaySchedule>): List<ScheduleGroup> {
        val byDay = schedules.associateBy { it.dayOfWeek }
        val groups = mutableListOf<ScheduleGroup>()
        for (day in WEEK) {
            val schedule = byDay[day] ?: continue
            val last = groups.lastOrNull()
            val follows = last != null && WEEK.indexOf(last.lastDay) == WEEK.indexOf(day) - 1
            if (follows && last.open == schedule.open && last.close == schedule.close) {
                groups[groups.lastIndex] = last.copy(lastDay = day)
            } else {
                groups += ScheduleGroup(day, day, schedule.open, schedule.close)
            }
        }
        return groups
    }

    /** «08:00» → «8:00», como se lee en México. */
    fun shortTime(time: String): String = if (time.length == 5 && time.startsWith('0')) time.drop(1) else time
}
