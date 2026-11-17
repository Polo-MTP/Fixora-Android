package com.fixora.app.feature.availability.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.availability.location.Coordinates
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

class AvailabilityRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: AvailabilityRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<AvailabilityApi>()
        repository = AvailabilityRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private fun availability(online: Boolean, isOpen: Boolean = true) =
        """{"success":true,"message":"ok","data":{"employee_id":"3","online":$online,"location":null,
            "branch":{"id":"1","accepting_request":true,"is_open":$isOpen},"expires_in_seconds":300}}"""

    private fun take(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun RecordedRequest.jsonBody(): JsonObject = json.parseToJsonElement(checkNotNull(body).utf8()).jsonObject

    @Test
    fun refreshReadsTheStateAndTheBranch() = runBlocking {
        respond(availability(online = false, isOpen = false))

        repository.refresh()

        assertEquals("/api/technicians/me/availability", take().url.encodedPath)
        assertEquals(AvailabilityState.Loaded(online = false, branchOpen = false), repository.state.value)
    }

    @Test
    fun accountWithoutTechnicianProfileHidesTheSwitch() = runBlocking {
        respond("""{"success":false,"error":{"code":"NOT_FOUND","message":"Técnico no encontrado."}}""", code = 404)

        repository.refresh()

        assertEquals(AvailabilityState.NotTechnician, repository.state.value)
    }

    @Test
    fun goOnlineSendsThePosition() = runBlocking {
        respond(availability(online = true))

        assertEquals(ApiResult.Success(Unit), repository.goOnline(Coordinates(19.43, -99.13)))

        val request = take()
        assertEquals("PUT", request.method)
        assertEquals(
            JsonObject(
                mapOf("online" to JsonPrimitive(true), "latitude" to JsonPrimitive(19.43), "longitude" to JsonPrimitive(-99.13)),
            ),
            request.jsonBody(),
        )
        assertTrue(repository.isOnline)
    }

    @Test
    fun goOfflineOnlySendsTheFlag() = runBlocking {
        respond(availability(online = false))

        repository.goOffline()

        assertEquals(JsonObject(mapOf("online" to JsonPrimitive(false))), take().jsonBody())
        assertFalse(repository.isOnline)
    }

    @Test
    fun closedBranchRejectsGoingOnlineWithTheApiMessage() = runBlocking {
        respond(availability(online = true))
        repository.goOnline(Coordinates(19.43, -99.13))
        respond(
            """{"success":false,"error":{"code":"OUTSIDE_BRANCH_SCHEDULE","message":"Fuera del horario de la sucursal."}}""",
            code = 409,
        )

        val result = repository.goOnline(Coordinates(19.43, -99.13))

        assertEquals(
            ApiError.Http(409, AvailabilityRepository.OUTSIDE_BRANCH_SCHEDULE, "Fuera del horario de la sucursal."),
            (result as ApiResult.Failure).error,
        )
        assertFalse(repository.isOnline)
    }
}
