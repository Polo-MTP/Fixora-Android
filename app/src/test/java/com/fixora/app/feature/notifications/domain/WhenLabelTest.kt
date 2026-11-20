package com.fixora.app.feature.notifications.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WhenLabelTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private val now = Instant.parse("2026-11-20T19:00:00Z") // 13:00 en CDMX

    @Test
    fun recentIsShownInMinutesAndHours() {
        assertEquals(WhenLabel.Now, WhenLabel.of(now.minusSeconds(20), now, zone))
        assertEquals(WhenLabel.Minutes(5), WhenLabel.of(now.minusSeconds(300), now, zone))
        assertEquals(WhenLabel.Hours(3), WhenLabel.of(now.minusSeconds(3 * 3600), now, zone))
    }

    @Test
    fun olderUsesYesterdayOrTheDate() {
        assertEquals(WhenLabel.Yesterday, WhenLabel.of(Instant.parse("2026-11-19T20:00:00Z"), now, zone))
        assertEquals(WhenLabel.Date(LocalDate.of(2026, 11, 12)), WhenLabel.of(Instant.parse("2026-11-12T18:00:00Z"), now, zone))
    }

    @Test
    fun aClockAheadOfThePhoneCountsAsNow() {
        assertEquals(WhenLabel.Now, WhenLabel.of(now.plusSeconds(30), now, zone))
    }

    @Test
    fun kindDecidesTheIcon() {
        val base = AppNotification("1", "x", "t", "m", null, null, false, null)
        assertEquals(NotificationKind.Service, base.copy(entityType = "service_request").kind)
        assertEquals(NotificationKind.Account, base.copy(type = "verificacion_proveedor").kind)
        assertEquals(NotificationKind.Report, base.copy(type = "reporte").kind)
        assertEquals(NotificationKind.Other, base.kind)
    }
}
