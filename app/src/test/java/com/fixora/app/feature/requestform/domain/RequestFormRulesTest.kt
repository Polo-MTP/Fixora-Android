package com.fixora.app.feature.requestform.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestFormRulesTest {

    @Test
    fun suggestionsDependOnTheServiceAndEndWithOther() {
        assertEquals(listOf("Fuga de agua", "Tubería rota", "Sin agua", "Drenaje tapado", "Otro"), RequestFormRules.suggestions("Plomería"))
        assertTrue("No enfría" in RequestFormRules.suggestions("Climatización"))
        assertEquals("Otro", RequestFormRules.suggestions("Algo nuevo").last())
    }

    @Test
    fun theChosenProblemGoesFirstInTheDescription() {
        assertEquals("Fuga de agua. Gotea el lavabo", RequestFormRules.description("Fuga de agua", "  Gotea el lavabo "))
        assertEquals("Fuga de agua", RequestFormRules.description("Fuga de agua", ""))
        assertEquals("Gotea el lavabo", RequestFormRules.description("Otro", "Gotea el lavabo"))
        assertEquals("", RequestFormRules.description(null, "  "))
        assertEquals(RequestFormRules.MAX_DESCRIPTION, RequestFormRules.description("Fuga", "x".repeat(3000)).length)
    }
}
