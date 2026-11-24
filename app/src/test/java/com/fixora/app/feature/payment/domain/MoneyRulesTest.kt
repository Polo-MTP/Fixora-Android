package com.fixora.app.feature.payment.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MoneyRulesTest {

    @Test
    fun readsAmountsLikeTheApi() {
        assertEquals(35_000L, MoneyRules.parseCents("350"))
        assertEquals(35_050L, MoneyRules.parseCents("350.5"))
        assertEquals(35_005L, MoneyRules.parseCents(" 350.05 "))
        assertNull(MoneyRules.parseCents("350.555"))
        assertNull(MoneyRules.parseCents("-5"))
        assertNull(MoneyRules.parseCents("1,200"))
        assertNull(MoneyRules.parseCents(""))
    }

    @Test
    fun formatsForTheScreenAndForTheApi() {
        assertEquals("$1,650", MoneyRules.format(165_000))
        assertEquals("$350.50", MoneyRules.format("350.50"))
        assertEquals("$0", MoneyRules.format(null))
        assertEquals("$1,234,567.08", MoneyRules.format(123_456_708))
        assertEquals("470.00", MoneyRules.toAmount(47_000))
        assertEquals("0.05", MoneyRules.toAmount(5))
        assertEquals("1650", MoneyRules.toInput(165_000))
        assertEquals("70.50", MoneyRules.toInput(7_050))
    }

    @Test
    fun aDebtCanBeRemindedOncePerDayAndNotWhileAPaymentWaits() {
        val summary = PaymentSummary("470.00", "400.00", "0.00", "70.00", true)
        val debt = Debt("62", "Plomería", null, summary, 0, null, null)
        assertTrue(debt.canRemind)
        assertFalse(debt.copy(nextReminderAt = Instant.parse("2026-11-24T10:00:00Z")).canRemind)
        assertFalse(debt.copy(summary = summary.copy(pendingConfirmation = "70.00")).canRemind)
        assertFalse(summary.isPaid)
        assertTrue(PaymentSummary("470.00", "470.00", "0.00", "0.00", false).isPaid)
    }
}
