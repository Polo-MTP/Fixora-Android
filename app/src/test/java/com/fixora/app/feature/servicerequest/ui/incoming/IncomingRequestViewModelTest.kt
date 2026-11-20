package com.fixora.app.feature.servicerequest.ui.incoming

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.servicerequest.data.IncomingRequestRepository
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingRequestViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val start = Instant.parse("2026-11-20T18:30:00Z")

    private class FakeRepository(var request: ServiceRequest) : IncomingRequestRepository {
        var acceptError: ApiError? = null
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<Pair<String, String>>()
        var loads = 0

        override suspend fun request(id: String): ApiResult<ServiceRequest> {
            loads++
            return ApiResult.Success(request)
        }

        override suspend fun accept(id: String): ApiResult<Unit> {
            acceptError?.let { return ApiResult.Failure(it) }
            accepted += id
            return ApiResult.Success(Unit)
        }

        override suspend fun reject(id: String, reason: String): ApiResult<Unit> {
            rejected += id to reason
            return ApiResult.Success(Unit)
        }
    }

    private class FakeLocation(var position: Coordinates? = Coordinates(19.4326, -99.1332)) : LocationSource {
        override fun hasPermission() = true
        override suspend fun current() = position
        override fun updates(intervalMs: Long): Flow<Coordinates> = emptyFlow()
    }

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: IncomingRequestViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun request(
        status: RequestStatus = RequestStatus.Pending,
        expiresAt: Instant? = start.plusSeconds(246),
        position: Coordinates? = Coordinates(19.4416, -99.1332),
    ) = ServiceRequest(
        id = "23", status = status, isEmergency = true, description = "", categoryName = "Plomería",
        clientName = "Carla Prueba", clientRating = 0.0, zone = "Centro, Cuauhtémoc", position = position,
        basePrice = "300.00", photoUrls = emptyList(), createdAt = null, expiresAt = expiresAt,
        scheduledAt = null, technicianName = null,
    )

    private fun create(request: ServiceRequest = request()) {
        repository = FakeRepository(request)
        viewModel = IncomingRequestViewModel(SavedStateHandle(mapOf("requestId" to "23")), repository, FakeLocation())
        viewModel.now = { start.plusMillis(scheduler.currentTime) }
        scheduler.runCurrent()
    }

    @Test
    fun countsDownTheTimeToAnswerAndRings() {
        create()

        assertEquals(246L, state.secondsLeft)
        assertTrue(state.isRinging)

        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()
        assertEquals(236L, state.secondsLeft)
    }

    @Test
    fun whenTimeRunsOutItStopsRingingAndCloses() {
        create(request(expiresAt = start.plusSeconds(2)))

        scheduler.advanceTimeBy(3_000)
        scheduler.runCurrent()

        assertEquals(0L, state.secondsLeft)
        assertTrue(state.isClosed)
        assertFalse(state.isRinging)
    }

    @Test
    fun measuresTheDistanceFromTheTechnician() {
        create()

        // 0.009° de latitud son ~1 km.
        assertEquals(1.0, checkNotNull(state.distanceKm), 0.05)
        assertEquals(2, IncomingRequestViewModel.etaMinutes(1.0))
        assertEquals(1, IncomingRequestViewModel.etaMinutes(0.1))
    }

    @Test
    fun acceptingEndsTheAlarm() {
        create()

        viewModel.accept()
        scheduler.runCurrent()

        assertEquals(listOf("23"), repository.accepted)
        assertEquals(IncomingOutcome.Accepted, state.outcome)
        assertFalse(state.isRinging)
    }

    @Test
    fun ifSomeoneElseTookItItReloadsAndShowsItClosed() {
        create()
        repository.acceptError = ApiError.Http(409, "REQUEST_NOT_PENDING", "Ya no está pendiente.")
        repository.request = request(status = RequestStatus.Accepted)

        viewModel.accept()
        scheduler.runCurrent()

        assertEquals(2, repository.loads)
        assertTrue(state.isClosed)
        assertNull(state.outcome)
        assertFalse(state.isAccepting)
    }

    @Test
    fun rejectingNeedsAReason() {
        create()

        viewModel.reject("  ")
        scheduler.runCurrent()
        assertTrue(repository.rejected.isEmpty())

        viewModel.reject("Me queda muy lejos")
        scheduler.runCurrent()
        assertEquals(listOf("23" to "Me queda muy lejos"), repository.rejected)
        assertEquals(IncomingOutcome.Rejected, state.outcome)
    }

    @Test
    fun alreadyCanceledDoesNotRing() {
        create(request(status = RequestStatus.Canceled))

        assertTrue(state.isClosed)
        assertFalse(state.isRinging)
    }

    @Test
    fun ringsEvenBeforeTheRequestLoads() {
        assertTrue(IncomingRequestUiState(isLoading = true).isRinging)
        assertTrue(IncomingRequestUiState(isLoading = false, loadError = ApiError.NoConnection).isRinging)
    }

    @Test
    fun scheduledRequestsHaveNoCountdown() {
        create(request(expiresAt = null))

        assertNull(state.secondsLeft)
        assertTrue(state.isRinging)
    }
}
