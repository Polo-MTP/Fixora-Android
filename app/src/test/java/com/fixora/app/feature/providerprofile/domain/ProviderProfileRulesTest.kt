package com.fixora.app.feature.providerprofile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderProfileRulesTest {

    @Test
    fun weekdaysWithTheSameHoursAreGrouped() {
        val schedules = (1..5).map { DaySchedule(it, "08:00", "18:00") } + DaySchedule(6, "09:00", "14:00")

        assertEquals(
            listOf(ScheduleGroup(1, 5, "08:00", "18:00"), ScheduleGroup(6, 6, "09:00", "14:00")),
            ScheduleRules.group(schedules),
        )
    }

    @Test
    fun sundayGoesLastAndJoinsSaturday() {
        val schedules = (0..6).map { DaySchedule(it, "00:00", "23:59") }

        val group = ScheduleRules.group(schedules).single()

        assertEquals(ScheduleGroup(1, 0, "00:00", "23:59"), group)
        assertTrue(group.allDay)
    }

    @Test
    fun aGapSplitsTheGroup() {
        val schedules = listOf(DaySchedule(1, "08:00", "18:00"), DaySchedule(3, "08:00", "18:00"))

        assertEquals(2, ScheduleRules.group(schedules).size)
        assertTrue(ScheduleRules.group(emptyList()).isEmpty())
    }

    @Test
    fun timesDropTheLeadingZero() {
        assertEquals("8:00", ScheduleRules.shortTime("08:00"))
        assertEquals("0:00", ScheduleRules.shortTime("00:00"))
        assertEquals("18:30", ScheduleRules.shortTime("18:30"))
    }

    @Test
    fun basePriceUsesTheServiceFromTheMapOrTheCheapest() {
        val profile = profile(
            ProviderService("1", "Plomería", "350.00", 60),
            ProviderService("2", "Electricidad", "280.00", 60),
        )

        assertEquals(BasePrice("350.00", isMinimum = false), profile.basePriceFor("1"))
        assertEquals(BasePrice("280.00", isMinimum = true), profile.basePriceFor(null))
        assertEquals(BasePrice("280.00", isMinimum = true), profile.basePriceFor("9"))
        assertEquals(BasePrice("350.00", isMinimum = false), profile(ProviderService("1", "Plomería", "350.00", 60)).basePriceFor(null))
        assertNull(profile().basePriceFor(null))
    }

    private fun profile(vararg services: ProviderService) = ProviderProfile(
        id = "1", name = "Matriz", businessName = "Negocio", independent = false, description = "", logoUrl = null,
        verified = false, plates = emptyList(), rating = 0.0, reviewsCount = 0, totalJobs = 0, acceptingRequests = true,
        colony = "", municipality = "", position = null, schedules = emptyList(), services = services.toList(),
        works = emptyList(), certificates = emptyList(),
    )
}
