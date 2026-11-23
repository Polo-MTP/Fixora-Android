package com.fixora.app.feature.availability.ui

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.DataResponse
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.availability.data.AvailabilityApi
import com.fixora.app.feature.availability.data.AvailabilityDto
import com.fixora.app.feature.availability.data.AvailabilityRepository
import com.fixora.app.feature.availability.data.AvailabilityRequest
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.availability.service.OnlineServiceController
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AvailabilityViewModelTest {

    private class FakeApi : AvailabilityApi {
        var online = false
        var updateError: HttpException? = null
        val updates = mutableListOf<AvailabilityRequest>()

        override suspend fun get() = DataResponse(AvailabilityDto(online))

        override suspend fun update(body: AvailabilityRequest): DataResponse<AvailabilityDto> {
            updates += body
            updateError?.let { throw it }
            online = body.online
            return DataResponse(AvailabilityDto(online))
        }
    }

    private class FakeLocation(var granted: Boolean = true, var position: Coordinates? = Coordinates(19.4, -99.1)) :
        LocationSource {
        override fun hasPermission() = granted
        override suspend fun current() = position
        override fun updates(intervalMs: Long): Flow<Coordinates> = emptyFlow()
    }

    private class FakeService : OnlineServiceController {
        var running = false
        override fun start() {
            running = true
        }
        override fun stop() {
            running = false
        }
    }

    private lateinit var api: FakeApi
    private lateinit var location: FakeLocation
    private lateinit var service: FakeService
    private lateinit var viewModel: AvailabilityViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakeApi()
        location = FakeLocation()
        service = FakeService()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create() {
        viewModel = AvailabilityViewModel(AvailabilityRepository(api, NetworkModule.provideJson()), location, service)
    }

    @Test
    fun goingOnlineSendsTheLocationAndStartsReporting() {
        create()

        viewModel.onToggle(true)

        assertEquals(listOf(AvailabilityRequest(true, 19.4, -99.1)), api.updates)
        assertTrue(state.isOnline)
        assertTrue(service.running)
        assertFalse(state.isChanging)
    }

    @Test
    fun withoutPermissionItAsksFirst() = runBlocking {
        location.granted = false
        create()

        viewModel.onToggle(true)

        withTimeout(1_000) { viewModel.requestPermission.first() }
        assertTrue(api.updates.isEmpty())

        viewModel.onPermissionResult(granted = false)
        assertEquals(AvailabilityProblem.LocationDenied, state.problem)
        assertFalse(service.running)
    }

    @Test
    fun withoutLocationItStaysOffline() {
        location.position = null
        create()

        viewModel.onToggle(true)

        assertEquals(AvailabilityProblem.LocationUnavailable, state.problem)
        assertTrue(api.updates.isEmpty())
        assertFalse(state.isOnline)
    }

    @Test
    fun closedBranchShowsTheApiError() {
        api.updateError = HttpException(
            Response.error<Any>(
                409,
                """{"error":"OUTSIDE_BRANCH_SCHEDULE","message":"Fuera de horario."}""".toResponseBody("application/json".toMediaType()),
            ),
        )
        create()

        viewModel.onToggle(true)

        assertEquals(
            AvailabilityProblem.Api(ApiError.Http(409, "OUTSIDE_BRANCH_SCHEDULE", "Fuera de horario.")),
            state.problem,
        )
        assertFalse(service.running)
    }

    @Test
    fun goingOfflineStopsReportingAndTellsTheApi() {
        create()
        viewModel.onToggle(true)

        viewModel.onToggle(false)

        assertFalse(service.running)
        assertEquals(AvailabilityRequest(online = false), api.updates.last())
        assertFalse(state.isOnline)
    }

    @Test
    fun ifTheApiSaysOnlineWhenOpeningItResumesReporting() {
        api.online = true

        create()

        assertTrue(state.isOnline)
        assertTrue(service.running)
    }
}
