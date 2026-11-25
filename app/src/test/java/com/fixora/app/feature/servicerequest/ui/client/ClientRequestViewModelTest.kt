package com.fixora.app.feature.servicerequest.ui.client

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.payment.data.PaymentRepository
import com.fixora.app.feature.payment.domain.AdjustmentReason
import com.fixora.app.feature.payment.domain.AdjustmentStatus
import com.fixora.app.feature.payment.domain.Debts
import com.fixora.app.feature.payment.domain.Payment
import com.fixora.app.feature.payment.domain.PaymentMethod
import com.fixora.app.feature.payment.domain.PaymentStatus
import com.fixora.app.feature.payment.domain.PaymentSummary
import com.fixora.app.feature.payment.domain.PriceAdjustment
import com.fixora.app.feature.payment.ui.PaymentDraft
import com.fixora.app.feature.servicerequest.data.ClientRequestRepository
import com.fixora.app.feature.servicerequest.data.TripRoute
import com.fixora.app.feature.servicerequest.data.TripTracking
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
        val cancelReasons = mutableListOf<String?>()
        var trackingCalls = 0
        var tracking = TripTracking(null, null)
        override suspend fun tracking(id: String): ApiResult<TripTracking> {
            trackingCalls++
            return ApiResult.Success(tracking)
        }
        override suspend fun cancel(id: String, reason: String?): ApiResult<Unit> {
            canceled++
            cancelReasons += reason
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

    private inner class FakePayments : PaymentRepository {
        val adjustmentAnswers = mutableListOf<Pair<String, Boolean>>()
        val registered = mutableListOf<Pair<PaymentDraft, String>>()
        var error: ApiError? = null
        override suspend fun respondAdjustment(requestId: String, adjustmentId: String, approve: Boolean): ApiResult<Unit> {
            adjustmentAnswers += adjustmentId to approve
            error?.let { return ApiResult.Failure(it) }
            val current = repository.request
            repository.request = current.copy(
                adjustments = current.adjustments.map {
                    if (it.id == adjustmentId) it.copy(status = if (approve) AdjustmentStatus.Approved else AdjustmentStatus.Rejected) else it
                },
            )
            return ApiResult.Success(Unit)
        }
        override suspend fun registerPayment(
            requestId: String, method: PaymentMethod, amountCents: Long, reference: String?, clientUuid: String,
        ): ApiResult<Unit> {
            registered += PaymentDraft(method, amountCents, reference) to clientUuid
            error?.let { return ApiResult.Failure(it) }
            repository.request = repository.request.copy(
                payments = listOf(Payment("7", "380.00", method, reference, PaymentStatus.Pending, null, null)),
                paymentSummary = PaymentSummary("380.00", "0.00", "380.00", "380.00", true),
            )
            return ApiResult.Success(Unit)
        }
        override suspend fun proposeAdjustment(requestId: String, amountCents: Long, reason: AdjustmentReason, description: String?, clientUuid: String) =
            ApiResult.Success(Unit)
        override suspend fun respondPayment(requestId: String, paymentId: String, received: Boolean, reason: String?) = ApiResult.Success(Unit)
        override suspend fun debts() = ApiResult.Success(Debts("0.00", emptyList()))
        override suspend fun sendReminder(requestId: String) = ApiResult.Success(Unit)
    }

    private val payments = FakePayments()
    private lateinit var repository: FakeRepository
    private lateinit var viewModel: ClientRequestViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create(request: ServiceRequest) {
        repository = FakeRepository(request)
        viewModel = ClientRequestViewModel(SavedStateHandle(mapOf("requestId" to "52")), repository, payments)
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

    private fun adjustment(status: AdjustmentStatus = AdjustmentStatus.Pending) =
        PriceAdjustment("4", "120.00", AdjustmentReason.ExtraMaterials, null, status, null)

    @Test
    fun atTheHomeTheClientApprovesTheAdjustment() {
        create(request(status = RequestStatus.Arrived).copy(agreedPrice = "380.00", adjustments = listOf(adjustment())))
        assertEquals("4", state.request?.pendingAdjustment?.id)
        assertEquals(38_000L, state.request?.totalCents)

        viewModel.respondAdjustment(approve = true)
        scheduler.runCurrent()

        assertEquals(listOf("4" to true), payments.adjustmentAnswers)
        assertEquals(null, state.request?.pendingAdjustment)
        assertEquals(50_000L, state.request?.totalCents)
        assertEquals(null, state.respondingAdjustmentId)
    }

    @Test
    fun aRejectedAdjustmentDoesNotCount() {
        create(request(status = RequestStatus.Arrived).copy(agreedPrice = "380.00", adjustments = listOf(adjustment())))

        viewModel.respondAdjustment(approve = false)
        scheduler.runCurrent()

        assertEquals(listOf("4" to false), payments.adjustmentAnswers)
        assertEquals(38_000L, state.request?.totalCents)
    }

    @Test
    fun finishedItRegistersThePaymentOnceEvenIfRetried() {
        val completed = request(status = RequestStatus.Completed).copy(
            agreedPrice = "380.00",
            totalAmount = "380.00",
            paymentSummary = PaymentSummary("380.00", "0.00", "0.00", "380.00", true),
        )
        create(completed)
        assertTrue(state.canRegisterPayment)
        payments.error = ApiError.Timeout

        viewModel.openPayment()
        viewModel.registerPayment(PaymentDraft(PaymentMethod.Cash, 38_000, null))
        scheduler.runCurrent()
        assertTrue(state.showPaymentSheet)
        assertEquals(ApiError.Timeout, state.paymentError)

        payments.error = null
        viewModel.registerPayment(PaymentDraft(PaymentMethod.Cash, 38_000, null))
        scheduler.runCurrent()

        assertEquals(2, payments.registered.size)
        // La misma llave: si el primero sí llegó a la API, no se registra dos veces.
        assertEquals(payments.registered[0].second, payments.registered[1].second)
        assertFalse(state.showPaymentSheet)
        assertFalse(state.canRegisterPayment)
        assertEquals("7", state.request?.pendingPayment?.id)
    }

    @Test
    fun withABalanceItKeepsCheckingUntilTheProviderConfirms() {
        create(
            request(status = RequestStatus.Completed).copy(
                totalAmount = "380.00",
                payments = listOf(Payment("7", "380.00", PaymentMethod.Cash, null, PaymentStatus.Pending, null, null)),
                paymentSummary = PaymentSummary("380.00", "0.00", "380.00", "380.00", true),
            ),
        )
        repository.request = repository.request.copy(
            payments = listOf(Payment("7", "380.00", PaymentMethod.Cash, null, PaymentStatus.Confirmed, null, null)),
            paymentSummary = PaymentSummary("380.00", "380.00", "0.00", "0.00", false),
        )
        scheduler.advanceTimeBy(ClientRequestViewModel.POLL_INTERVAL_MS + 1)
        scheduler.runCurrent()
        assertTrue(state.request?.paymentSummary?.isPaid == true)

        // Ya pagado deja de consultar.
        val loads = repository.loads
        scheduler.advanceTimeBy(ClientRequestViewModel.POLL_INTERVAL_MS * 3)
        scheduler.runCurrent()
        assertEquals(loads, repository.loads)
    }

    // ANDROID-22: seguimiento del técnico.

    private val technicianHere = Coordinates(25.58, -103.36)
    private val route = TripRoute(listOf(technicianHere, Coordinates(25.59, -103.35)), durationSeconds = 540, distanceMeters = 2300, arrivesAt = null)

    @Test
    fun onTheWayItShowsWhereTheTechnicianGoesAndKeepsAsking() {
        create(request(status = RequestStatus.Accepted))
        assertEquals(0, repository.trackingCalls)
        repository.tracking = TripTracking(technicianHere, route)

        repository.request = request(status = RequestStatus.OnTheWay)
        scheduler.advanceTimeBy(ClientRequestViewModel.POLL_INTERVAL_MS + 1)
        scheduler.runCurrent()

        assertEquals(technicianHere, state.technicianPosition)
        assertEquals(route, state.route)
        val calls = repository.trackingCalls
        scheduler.advanceTimeBy(ClientRequestViewModel.FOLLOW_INTERVAL_MS)
        scheduler.runCurrent()
        assertTrue(repository.trackingCalls > calls)
    }

    @Test
    fun whenTheTechnicianArrivesTheAlertSoundsOnceAndTheRouteGoesAway() {
        create(request(status = RequestStatus.OnTheWay))
        repository.tracking = TripTracking(technicianHere, route)
        scheduler.advanceTimeBy(ClientRequestViewModel.FOLLOW_INTERVAL_MS + 1)
        scheduler.runCurrent()
        assertEquals(0, state.arrivalAlerts)

        repository.request = request(status = RequestStatus.Arrived)
        scheduler.advanceTimeBy(ClientRequestViewModel.POLL_INTERVAL_MS + 1)
        scheduler.runCurrent()
        assertEquals(1, state.arrivalAlerts)
        assertEquals(null, state.route)

        // Seguir consultando ya en el domicilio no lo repite.
        scheduler.advanceTimeBy(ClientRequestViewModel.POLL_INTERVAL_MS + 1)
        scheduler.runCurrent()
        assertEquals(1, state.arrivalAlerts)
    }

    @Test
    fun openingWithTheTechnicianAlreadyThereDoesNotSound() {
        create(request(status = RequestStatus.Arrived))

        assertEquals(0, state.arrivalAlerts)
    }

    @Test
    fun afterAcceptingCancelingAsksForTheReason() {
        create(request(status = RequestStatus.OnTheWay))
        viewModel.cancel()
        scheduler.runCurrent()
        // El botón de espera no cancela una aceptada.
        assertEquals(0, repository.canceled)

        viewModel.openCancelAccepted()
        assertTrue(state.showCancelSheet)
        viewModel.cancelAccepted("   ")
        scheduler.runCurrent()
        assertEquals(0, repository.canceled)

        viewModel.cancelAccepted("  Ya se resolvió  ")
        scheduler.runCurrent()

        assertEquals(listOf<String?>("Ya se resolvió"), repository.cancelReasons)
        assertEquals(ClientPhase.Canceled, state.phase)
        assertFalse(state.showCancelSheet)
    }

    @Test
    fun whileWaitingItCancelsWithoutAReason() {
        create(request())

        viewModel.cancel()
        scheduler.runCurrent()

        assertEquals(listOf<String?>(null), repository.cancelReasons)
    }
}
