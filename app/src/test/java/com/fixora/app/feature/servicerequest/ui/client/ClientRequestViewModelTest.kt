package com.fixora.app.feature.servicerequest.ui.client

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.servicerequest.data.ClientRequestRepository
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClientRequestViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val start = Instant.parse("2026-11-21T18:00:00Z")

    private class FakeRepository(var request: ServiceRequest) : ClientRequestRepository {
        var loads = 0
        var canceled = 0
        var cancelError: ApiError? = null
        val resent = mutableListOf<Pair<ServiceRequest, String>>()
        override suspend fun request(id: String): ApiResult<ServiceRequest> {
            loads++
            return ApiResult.Success(request)
        }
        override suspend fun cancel(id: String): ApiResult<Unit> {
            canceled++
            cancelError?.let { return ApiResult.Failure(it) }
            request = request.copy(status = RequestStatus.Canceled)
            return ApiResult.Success(Unit)
        }
        override suspend fun resend(request: ServiceRequest, clientUuid: String): ApiResult<String> {
            resent += request to clientUuid
            return ApiResult.Success("60")
        }
    }

    private fun request(
        status: RequestStatus = RequestStatus.Pending,
        emergency: Boolean = false,
        sent: Int = 1,
        rejected: Int = 0,
        expiresIn: Long = 280,
        addressId: String? = "14",
    ) = ServiceRequest(
        id = "52", status = status, isEmergency = emergency, description = "Fuga de agua", categoryName = "Plomería",
        clientName = "", clientRating = 0.0, zone = "Torreón Jardín", position = Coordinates(25.59, -103.35),
        basePrice = "380.00", photoUrls = emptyList(), createdAt = null, expiresAt = start.plusSeconds(expiresIn),
        scheduledAt = null, technicianName = null, categoryId = "1", branchId = if (emergency) null else "70",
        branchName = "Plomería Laguna", addressId = addressId, dispatchesSent = sent, dispatchesRejected = rejected,
    )

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: ClientRequestViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create(request: ServiceRequest) {
        repository = FakeRepository(request)
        viewModel = ClientRequestViewModel(SavedStateHandle(mapOf("requestId" to "52")), repository)
        viewModel.now = { start.plusMillis(scheduler.currentTime) }
        scheduler.runCurrent()
    }

    @Test
    fun waitsWithACountdownAndAsksTheApiAgain() {
        create(request())
        assertEquals(ClientPhase.Waiting, state.phase)
        assertEquals(280L, state.secondsLeft)

        scheduler.advanceTimeBy(ClientRequestViewModel.POLL_INTERVAL_MS + 1)
        scheduler.runCurrent()
        assertEquals(276L, state.secondsLeft)
        assertEquals(2, repository.loads)
    }

    @Test
    fun aRejectionShowsAtOnce() {
        create(request())
        repository.request = request(rejected = 1)

        scheduler.advanceTimeBy(ClientRequestViewModel.POLL_INTERVAL_MS + 1)
        scheduler.runCurrent()

        assertEquals(ClientPhase.Rejected, state.phase)
        assertTrue(state.canResend)
    }

    @Test
    fun anEmergencyKeepsWaitingWhileSomeoneCanStillAnswer() {
        create(request(emergency = true, sent = 4, rejected = 3))
        assertEquals(ClientPhase.Waiting, state.phase)

        repository.request = request(emergency = true, sent = 4, rejected = 4)
        scheduler.advanceTimeBy(ClientRequestViewModel.POLL_INTERVAL_MS + 1)
        scheduler.runCurrent()
        assertEquals(ClientPhase.Rejected, state.phase)
    }

    @Test
    fun whenTimeRunsOutItShowsExpired() {
        create(request(expiresIn = 2))

        scheduler.advanceTimeBy(3_000)
        scheduler.runCurrent()

        assertEquals(ClientPhase.Expired, state.phase)
    }

    @Test
    fun acceptedShowsTheTechnician() {
        create(request(status = RequestStatus.OnTheWay).copy(technicianName = "Ricardo Salinas"))

        assertEquals(ClientPhase.Accepted, state.phase)
        assertEquals(null, state.secondsLeft)
    }

    @Test
    fun cancelingWhileWaiting() {
        create(request())

        viewModel.cancel()
        scheduler.runCurrent()

        assertEquals(1, repository.canceled)
        assertEquals(ClientPhase.Canceled, state.phase)
        assertFalse(state.isCanceling)
    }

    @Test
    fun ifItWasAcceptedMeanwhileTheCancelErrorShowsAndItReloads() {
        create(request())
        repository.cancelError = ApiError.Http(409, "INVALID_STATUS_TRANSITION", "Ya no puede cancelarse.")
        repository.request = request(status = RequestStatus.Accepted)

        viewModel.cancel()
        scheduler.runCurrent()

        assertEquals(ClientPhase.Accepted, state.phase)
        assertEquals(repository.cancelError, state.actionError)
    }

    @Test
    fun resendingOpensTheNewRequestWithANewUuid() {
        create(request(status = RequestStatus.Expired))

        viewModel.resend()
        scheduler.runCurrent()

        assertEquals("60", state.resentRequestId)
        assertEquals(36, repository.resent.single().second.length)
    }

    @Test
    fun withoutASavedAddressItCannotBeResent() {
        create(request(status = RequestStatus.Expired, addressId = null))

        assertFalse(state.canResend)
        viewModel.resend()
        scheduler.runCurrent()
        assertTrue(repository.resent.isEmpty())
    }
}
