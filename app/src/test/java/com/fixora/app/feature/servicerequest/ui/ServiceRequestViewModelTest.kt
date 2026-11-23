package com.fixora.app.feature.servicerequest.ui

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
class ServiceRequestViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val start = Instant.parse("2026-11-21T18:00:00Z")

    private class FakeRepository(var request: ServiceRequest) : IncomingRequestRepository {
        var loadError: ApiError? = null
        var acceptError: ApiError? = null
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        override suspend fun request(id: String): ApiResult<ServiceRequest> =
            loadError?.let { ApiResult.Failure(it) } ?: ApiResult.Success(request)
        override suspend fun accept(id: String): ApiResult<Unit> {
            acceptError?.let { return ApiResult.Failure(it) }
            accepted += id
            request = request.copy(status = RequestStatus.Accepted, technicianName = "juan Prueba")
            return ApiResult.Success(Unit)
        }
        override suspend fun reject(id: String, reason: String): ApiResult<Unit> {
            rejected += reason
            return ApiResult.Success(Unit)
        }
    }

    private class FakeLocation : LocationSource {
        var allowed = true
        override fun hasPermission() = allowed
        override suspend fun current() = Coordinates(25.5964, -103.3593)
        override fun updates(intervalMs: Long): Flow<Coordinates> = emptyFlow()
    }

    private fun request(status: RequestStatus = RequestStatus.Pending, expiresIn: Long = 200, scheduled: Boolean = false) = ServiceRequest(
        id = "15", status = status, isEmergency = false, description = "Fuga", categoryName = "Plomería", clientName = "Carla Prueba",
        clientRating = 0.0, zone = "Torreón Jardín", position = Coordinates(25.6054, -103.3593), basePrice = "300.00",
        photoUrls = emptyList(), createdAt = null, expiresAt = start.plusSeconds(expiresIn),
        scheduledAt = if (scheduled) start.plusSeconds(86_400) else null, technicianName = null,
    )

    private val location = FakeLocation()
    private lateinit var repository: FakeRepository
    private lateinit var viewModel: ServiceRequestViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create(request: ServiceRequest = request()) {
        repository = FakeRepository(request)
        viewModel = ServiceRequestViewModel(SavedStateHandle(mapOf("requestId" to "15")), repository, location)
        viewModel.now = { start.plusMillis(scheduler.currentTime) }
        scheduler.runCurrent()
    }

    @Test
    fun pendingShowsCountdownAndDistanceAndCanBeAnswered() {
        create()

        assertEquals(200L, state.secondsLeft)
        assertEquals(1.0, checkNotNull(state.distanceKm), 0.05)
        assertTrue(state.canRespond)

        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()
        assertEquals(195L, state.secondsLeft)
    }

    @Test
    fun acceptingShowsItAccepted() {
        create()

        viewModel.accept()
        scheduler.runCurrent()

        assertEquals(listOf("15"), repository.accepted)
        assertEquals(RequestStatus.Accepted, state.request?.status)
        assertFalse(state.canRespond)
        assertNull(state.secondsLeft)
    }

    @Test
    fun ifSomeoneElseTookItTheErrorShowsAndItReloads() {
        create()
        repository.acceptError = ApiError.Http(409, "REQUEST_NOT_PENDING", "Ya no está pendiente.")
        repository.request = request(status = RequestStatus.Accepted)

        viewModel.accept()
        scheduler.runCurrent()

        assertEquals(repository.acceptError, state.actionError)
        assertFalse(state.canRespond)
    }

    @Test
    fun rejectingWithAReasonClosesTheScreen() {
        create()

        viewModel.reject(" ")
        scheduler.runCurrent()
        assertTrue(repository.rejected.isEmpty())

        viewModel.reject("Me queda muy lejos")
        scheduler.runCurrent()
        assertEquals(listOf("Me queda muy lejos"), repository.rejected)
        assertTrue(state.rejected)
    }

    @Test
    fun expiredCannotBeAnswered() {
        create(request(expiresIn = 1))

        scheduler.advanceTimeBy(2_000)
        scheduler.runCurrent()

        assertEquals(0L, state.secondsLeft)
        assertFalse(state.canRespond)
        viewModel.accept()
        scheduler.runCurrent()
        assertTrue(repository.accepted.isEmpty())
    }

    @Test
    fun scheduledHasNoCountdownButCanBeAnswered() {
        create(request(scheduled = true))

        assertNull(state.secondsLeft)
        assertTrue(state.canRespond)
    }

    @Test
    fun loadErrorCanBeRetried() {
        repository = FakeRepository(request()).apply { loadError = ApiError.NoConnection }
        viewModel = ServiceRequestViewModel(SavedStateHandle(mapOf("requestId" to "15")), repository, location)
        scheduler.runCurrent()
        assertEquals(ApiError.NoConnection, state.error)

        repository.loadError = null
        viewModel.retry()
        scheduler.runCurrent()

        assertNull(state.error)
        assertEquals("15", state.request?.id)
    }
}
