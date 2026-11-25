package com.fixora.app.feature.servicerequest.data

import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.availability.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seguimiento del técnico: la ruta real y la de respaldo (ANDROID-43). */
class TrackingMapperTest {

    private val json = NetworkModule.provideJson()

    private fun tracking(body: String) = json.decodeFromString<TrackingDto>(body).toTripTracking()

    @Test
    fun aRealRouteKeepsItsStreetsInOrder() {
        val result = tracking(
            """{"status":"en_camino","technician_location":{"latitude":25.59,"longitude":-103.36},
            "route_eta":{"duration_seconds":540,"distance_meters":2300,"is_fallback":false,
            "geometry":{"type":"LineString","coordinates":[[-103.36,25.59],[-103.365,25.595],[-103.368,25.602]]}}}""",
        )

        val route = result.route!!
        assertEquals(Coordinates(25.59, -103.36), result.technician)
        assertEquals(3, route.path.size)
        // GeoJSON va [longitud, latitud].
        assertEquals(Coordinates(25.595, -103.365), route.path[1])
        assertFalse(route.approximate)
    }

    @Test
    fun withoutTheRouteServerTheEstimateIsMarkedAsApproximate() {
        val result = tracking(
            """{"status":"en_camino","route_eta":{"duration_seconds":480,"distance_meters":1469,"is_fallback":true,
            "geometry":{"type":"LineString","coordinates":[[-103.36,25.59],[-103.368,25.602]]}}}""",
        )

        val route = result.route!!
        assertTrue(route.approximate)
        // Por ahora se sigue dibujando la línea entre los dos puntos, hasta tener servidor propio.
        assertEquals(2, route.path.size)
    }

    @Test
    fun anOlderApiWithoutTheFlagIsNotApproximate() {
        val result = tracking("""{"route_eta":{"duration_seconds":60,"distance_meters":100}}""")

        assertFalse(result.route!!.approximate)
    }
}
