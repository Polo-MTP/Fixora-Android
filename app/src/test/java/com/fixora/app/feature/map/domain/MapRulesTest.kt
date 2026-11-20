package com.fixora.app.feature.map.domain

import com.fixora.app.feature.availability.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRulesTest {

    @Test
    fun widerRadiusZoomsOut() {
        val zooms = MapRules.RADIUS_OPTIONS_KM.map { MapRules.zoomFor(it) }
        assertEquals(zooms.sortedDescending(), zooms)
        assertEquals(13.3, MapRules.zoomFor(1), 0.01)
    }

    @Test
    fun distanceMatchesTheApi() {
        // Zócalo al Ángel de la Independencia, el mismo caso que las pruebas de la API (~3.7 km).
        val km = MapRules.distanceKm(Coordinates(19.4326, -99.1332), Coordinates(19.427, -99.1677))
        assertTrue(km in 3.5..4.0)
    }

    @Test
    fun searchHereOnlyAfterMovingAThirdOfTheRadius() {
        val origin = Coordinates(19.4326, -99.1332)
        assertFalse(MapRules.movedEnough(origin, Coordinates(19.44, -99.14), radiusKm = 10))
        assertTrue(MapRules.movedEnough(origin, Coordinates(19.48, -99.13), radiusKm = 10))
    }

    @Test
    fun initialsUseTheBusinessName() {
        assertEquals("PC", provider(business = "Plomería Centro").initials)
        assertEquals("EL", provider(business = "Electrojuan").initials)
        assertEquals("MA", provider(business = " ").initials)
    }

    @Test
    fun matchingIgnoresCaseAndAccents() {
        assertTrue(provider(business = "Cerrajería Rápida").matches("cerrajeria rap"))
        assertTrue(provider(business = "X").matches("  "))
        assertTrue(ServiceCategory("1", "Climatización", "climatizacion").matches("CLIMA"))
        assertFalse(ServiceCategory("1", "Plomería", "plomeria").matches(""))
    }

    private fun provider(business: String) = MapProvider(
        id = "1",
        name = "Matriz",
        businessName = business,
        independent = false,
        logoUrl = null,
        position = Coordinates(0.0, 0.0),
        distanceKm = 0.0,
        rating = 0.0,
        reviewsCount = 0,
        basePrice = null,
        openNow = false,
    )
}
