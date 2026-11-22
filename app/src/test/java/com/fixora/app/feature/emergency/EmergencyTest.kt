package com.fixora.app.feature.emergency

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.emergency.data.EmergencyApi
import com.fixora.app.feature.emergency.data.RemoteEmergencyRepository
import com.fixora.app.feature.emergency.ui.EmergencyViewModel
import com.fixora.app.feature.emergency.ui.LocationStatus
import com.fixora.app.feature.map.data.MapRepository
import com.fixora.app.feature.map.domain.MapFilters
import com.fixora.app.feature.map.domain.MapSearchResult
import com.fixora.app.feature.map.domain.ServiceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyTest {

    private class FakeLocation(var granted: Boolean = true, var position: Coordinates? = Coordinates(19.43, -99.13)) : LocationSource {
        override fun hasPermission() = granted
        override suspend fun current() = position
        override fun updates(intervalMs: Long): Flow<Coordinates> = emptyFlow()
    }

    private val categories = object : MapRepository {
        override suspend fun search(center: Coordinates, filters: MapFilters, categoryId: String?) =
            ApiResult.Success(MapSearchResult(emptyList(), 0))

        override suspend fun categories(): ApiResult<List<ServiceCategory>> =
            ApiResult.Success(listOf(ServiceCategory("1", "Plomería", "plomeria"), ServiceCategory("2", "Electricidad", "electricidad")))
    }

    private val sent = mutableListOf<Triple<String, String, Coordinates>>()
    private var sendError: ApiError? = null
    private lateinit var location: FakeLocation
    private lateinit var viewModel: EmergencyViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        location = FakeLocation()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create() {
        viewModel = EmergencyViewModel(SavedStateHandle(), categories, location) { uuid, category, position ->
            sent += Triple(uuid, category, position)
            sendError?.let { ApiResult.Failure(it) } ?: ApiResult.Success("40")
        }
    }

    @Test
    fun onlyNeedsTheServiceAndTheLocation() {
        create()
        assertEquals(LocationStatus.Ready, state.location)
        assertFalse(state.canSend)

        viewModel.onCategorySelected("2")
        assertTrue(state.canSend)
        viewModel.send()

        assertEquals("2", sent.single().second)
        assertEquals(Coordinates(19.43, -99.13), sent.single().third)
        assertEquals("40", state.createdRequestId)
    }

    @Test
    fun withoutPermissionItAsksAndCannotSend() = runBlocking {
        location.granted = false
        create()

        withTimeout(1_000) { viewModel.requestPermission.first() }
        assertEquals(LocationStatus.NoPermission, state.location)
        viewModel.onCategorySelected("1")
        assertFalse(state.canSend)

        location.granted = true
        viewModel.onPermissionResult(true)
        assertTrue(state.canSend)
    }

    @Test
    fun withoutSignalItSaysSo() {
        location.position = null
        create()

        assertEquals(LocationStatus.Unavailable, state.location)
        assertNull(state.position)
    }

    @Test
    fun noTechniciansNearbyShowsTheErrorAndRetryKeepsTheSameUuid() {
        sendError = ApiError.Http(409, "NO_TECHNICIANS_AVAILABLE", "No hay técnicos disponibles cerca para esta categoría.")
        create()
        viewModel.onCategorySelected("1")

        viewModel.send()
        assertEquals(sendError, state.sendError)
        assertNull(state.createdRequestId)

        sendError = null
        viewModel.send()
        assertEquals("40", state.createdRequestId)
        assertEquals(1, sent.map { it.first }.toSet().size)
    }

    @Test
    fun repositorySendsOnlyServiceAndCoordinates() = runBlocking {
        val server = MockWebServer().apply { start() }
        val json = NetworkModule.provideJson()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<EmergencyApi>()
        server.enqueue(
            MockResponse.Builder().code(201).addHeader("Content-Type", "application/json")
                .body("""{"success":true,"data":{"id":"40","status":"pendiente","is_emergency":true}}""").build(),
        )

        val id = (RemoteEmergencyRepository(api, json).send("6f1c2b9e-3d1a-4b2c-9e8f-1a2b3c4d5e6f", "1", Coordinates(19.43, -99.13)) as ApiResult.Success).data

        val request = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val body = json.parseToJsonElement(checkNotNull(request.body).utf8()).jsonObject
        assertEquals("/api/service-requests/emergency", request.url.encodedPath)
        assertEquals(setOf("client_uuid", "category_id", "latitude", "longitude"), body.keys)
        assertEquals(19.43, body["latitude"]!!.jsonPrimitive.double, 0.0)
        assertEquals("40", id)
        server.close()
    }
}
