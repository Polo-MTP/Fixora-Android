package com.fixora.app.feature.servicerequest.ui.trip

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.servicerequest.data.TripRepository
import com.fixora.app.feature.servicerequest.data.TripRoute
import com.fixora.app.feature.servicerequest.data.TripStep
import com.fixora.app.feature.servicerequest.data.TripTracking
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import com.fixora.app.feature.tracking.service.TripTrackingController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val here = Coordinates(25.5964, -103.3593)
    private val home = Coordinates(25.6054, -103.3593)

    private class FakeRepository(var status: RequestStatus) : TripRepository {
        val steps = mutableListOf<Pair<TripStep, Coordinates>>()
        var advanceError: ApiError? = null
        var trackingCalls = 0
        var tracking = TripTracking(null, null)
        override suspend fun request(id: String) = ApiResult.Success(
            ServiceRequest(
                id = id, status = status, isEmergency = false, description = "Fuga", categoryName = "Plomería",
                clientName = "Carla Prueba", clientRating = 0.0, zone = "Torreón Jardín", position = Coordinates(25.6054, -103.3593),
                basePrice = "300.00", photoUrls = emptyList(), createdAt = null, expiresAt = null, scheduledAt = null,
                technicianName = "juan", street = "Av. Morelos 845",
            ),
        )
        override suspend fun tracking(id: String): ApiResult<TripTracking> {
            trackingCalls++
            return ApiResult.Success(tracking)
        }
        override suspend fun advance(id: String, step: TripStep, position: Coordinates): ApiResult<Unit> {
            advanceError?.let { return ApiResult.Failure(it) }
            steps += step to position
            status = when (step) {
                TripStep.Start -> RequestStatus.OnTheWay
                TripStep.Arrive -> RequestStatus.Arrived
                TripStep.Finish -> RequestStatus.Completed
            }
            return ApiResult.Success(Unit)
        }
    }

    private inner class FakeLocation : LocationSource {
        var allowed = true
        var position: Coordinates? = here
        override fun hasPermission() = allowed
        override suspend fun current() = position
        override fun updates(intervalMs: Long): Flow<Coordinates> = emptyFlow()
    }

    private class FakeTrip : TripTrackingController {
        val started = mutableListOf<String>()
        var stopped = 0
        override fun start(requestId: String) {
            started += requestId
        }
        override fun stop() {
            stopped++
        }
    }

    private val location = FakeLocation()
    private val trip = FakeTrip()
    private lateinit var repository: FakeRepository
    private lateinit var viewModel: TripViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create(status: RequestStatus) {
        repository = FakeRepository(status)
        viewModel = TripViewModel(SavedStateHandle(mapOf("requestId" to "15")), repository, location, trip)
        scheduler.runCurrent()
    }

    @Test
    fun acceptedStartsTheTripFromWhereTheTechnicianIs() {
        create(RequestStatus.Accepted)
        assertEquals(TripStep.Start, state.nextStep)
        assertEquals(here, state.technician)

        viewModel.advance()
        scheduler.runCurrent()

        assertEquals(listOf(TripStep.Start to here), repository.steps)
        assertEquals(RequestStatus.OnTheWay, state.request?.status)
        assertEquals(TripStep.Arrive, state.nextStep)
        // Ya en camino: el cliente lo ve avanzar.
        assertEquals(listOf("15"), trip.started)
    }

    @Test
    fun onTheWayFollowsTheRouteEveryFewSeconds() {
        create(RequestStatus.OnTheWay)
        repository.tracking = TripTracking(here, TripRoute(listOf(here, home), durationSeconds = 360, distanceMeters = 1_200, arrivesAt = null))

        scheduler.advanceTimeBy(TripViewModel.FOLLOW_INTERVAL_MS + 1)
        scheduler.runCurrent()

        assertEquals(2, repository.trackingCalls)
        assertEquals(360L, state.route?.durationSeconds)
        assertEquals(listOf(here, home), state.route?.path)
    }

    @Test
    fun arrivingStopsSharingAndThenFinishes() {
        create(RequestStatus.OnTheWay)

        viewModel.advance()
        scheduler.runCurrent()
        assertEquals(RequestStatus.Arrived, state.request?.status)
        assertEquals(1, trip.stopped)
        assertNull(state.route)
        val callsAfterArrival = repository.trackingCalls
        scheduler.advanceTimeBy(TripViewModel.FOLLOW_INTERVAL_MS * 3)
        scheduler.runCurrent()
        assertEquals(callsAfterArrival, repository.trackingCalls)

        viewModel.advance()
        scheduler.runCurrent()
        assertEquals(RequestStatus.Completed, state.request?.status)
        assertNull(state.nextStep)
    }

    @Test
    fun withoutPermissionItAsksAndContinuesOnceGranted() {
        location.allowed = false
        create(RequestStatus.Accepted)

        viewModel.advance()
        scheduler.runCurrent()
        assertTrue(state.needsLocation)
        assertTrue(repository.steps.isEmpty())

        viewModel.onLocationPermissionResult(false)
        assertTrue(state.locationDenied)

        // Lo activa en los ajustes y regresa: sale solo, sin volver a tocar el botón.
        location.allowed = true
        viewModel.onResume()
        scheduler.runCurrent()
        assertFalse(state.needsLocation)
        assertEquals(TripStep.Start, repository.steps.single().first)
    }

    @Test
    fun onTheWayWithoutPermissionAsksForItToShareTheLocation() {
        location.allowed = false
        create(RequestStatus.OnTheWay)
        assertTrue(state.needsLocation)
        assertTrue(trip.started.isEmpty())

        location.allowed = true
        viewModel.onLocationPermissionResult(true)
        assertFalse(state.needsLocation)
        assertEquals(listOf("15"), trip.started)
    }

    @Test
    fun withTheGpsOffItSaysSoAndDoesNotAdvance() {
        location.position = null
        create(RequestStatus.Accepted)

        viewModel.advance()
        scheduler.runCurrent()

        assertTrue(state.noPosition)
        assertTrue(repository.steps.isEmpty())
        assertFalse(state.isAdvancing)
    }

    @Test
    fun ifTheClientCanceledTheErrorShowsAndItReloads() {
        create(RequestStatus.Accepted)
        repository.advanceError = ApiError.Http(409, "INVALID_STATUS_TRANSITION", "No se permite.")
        repository.status = RequestStatus.Canceled

        viewModel.advance()
        scheduler.runCurrent()

        assertEquals(repository.advanceError, state.actionError)
        assertEquals(RequestStatus.Canceled, state.request?.status)
        assertNull(state.nextStep)
    }
}
