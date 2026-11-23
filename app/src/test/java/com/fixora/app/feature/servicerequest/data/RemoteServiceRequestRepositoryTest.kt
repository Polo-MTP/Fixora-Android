package com.fixora.app.feature.servicerequest.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.parseApiInstant
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.time.Instant
import java.util.concurrent.TimeUnit

class RemoteServiceRequestRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteServiceRequestRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<ServiceRequestApi>()
        repository = RemoteServiceRequestRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    @Test
    fun readsTheRequestTheAlarmOpens() = runBlocking {
        respond(
            """{"success":true,"data":{"id":"15","status":"pendiente","is_emergency":true,
            "description":"  Fuga en el baño  ","category":{"id":"1","name":"Plomería"},
            "branch":{"id":"2","name":"Matriz","phone":null},"technician":null,
            "client":{"name":"Roberto Martínez","rating_avg":4.5},
            "address":{"id":"9","alias":"Casa","street":"Calle","colony":"Juárez","municipality":"Cuauhtémoc","latitude":19.43,"longitude":-99.13},
            "location":null,"base_price":"350.00","agreed_price":null,
            "attachments":[{"file_key":"photos/1/a.jpg","url":"https://f/a"},{"file_key":"x","url":""}],
            "created_at":"2026-11-20T18:30:00.000Z","expires_at":"2026-11-20T18:35:00.000Z","scheduled_at":null}}""",
        )

        val request = (repository.request("15") as ApiResult.Success).data

        assertEquals("/api/service-requests/15", checkNotNull(server.takeRequest(2, TimeUnit.SECONDS)).url.encodedPath)
        assertEquals(RequestStatus.Pending, request.status)
        assertTrue(request.isEmergency)
        assertEquals("Fuga en el baño", request.description)
        assertEquals("Plomería", request.categoryName)
        assertEquals("Juárez, Cuauhtémoc", request.zone)
        // Sin ubicación propia de la solicitud se usa la de su dirección.
        assertEquals(Coordinates(19.43, -99.13), request.position)
        assertEquals(listOf("https://f/a"), request.photoUrls)
        assertEquals(Instant.parse("2026-11-20T18:35:00Z"), request.expiresAt)
        assertNull(request.scheduledAt)
        assertNull(request.technicianName)
    }

    @Test
    fun anotherProvidersRequestIsNotFound() = runBlocking {
        respond("""{"success":false,"error":"NOT_FOUND","message":"No encontrado."}""", code = 404)

        val error = (repository.request("99") as ApiResult.Failure).error

        assertEquals(404, (error as ApiError.Http).status)
    }

    @Test
    fun statusesAndDatesFromTheApi() {
        assertEquals(RequestStatus.OnTheWay, RequestStatus.from("en_curso"))
        assertEquals(RequestStatus.Expired, RequestStatus.from("expirada"))
        assertEquals(RequestStatus.Unknown, RequestStatus.from("otro"))
        assertNotNull(parseApiInstant("2026-11-20T18:30:00"))
        assertNull(parseApiInstant("no es fecha"))
        assertNull(parseApiInstant(null))
    }
}
