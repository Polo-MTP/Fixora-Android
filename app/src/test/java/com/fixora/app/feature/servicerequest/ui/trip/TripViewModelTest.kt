package com.fixora.app.feature.servicerequest.ui.trip

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.payment.data.PaymentRepository
import com.fixora.app.feature.payment.domain.AdjustmentReason
import com.fixora.app.feature.payment.domain.AdjustmentStatus
import com.fixora.app.feature.payment.domain.Debts
import com.fixora.app.feature.payment.domain.Payment
import com.fixora.app.feature.payment.domain.PaymentMethod
import com.fixora.app.feature.payment.domain.PaymentStatus
import com.fixora.app.feature.payment.domain.PaymentSummary
import com.fixora.app.feature.payment.domain.PriceAdjustment
import com.fixora.app.feature.payment.ui.AdjustmentDraft
import com.fixora.app.feature.servicerequest.data.EvidenceProblem
import com.fixora.app.feature.servicerequest.data.EvidenceReadResult
import com.fixora.app.feature.servicerequest.data.TripRepository
import com.fixora.app.feature.servicerequest.data.TripRoute
import com.fixora.app.feature.servicerequest.data.TripStep
import com.fixora.app.feature.servicerequest.data.TripTracking
import com.fixora.app.feature.servicerequest.domain.CancelRules
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
        var requestCalls = 0
        var adjustments = emptyList<PriceAdjustment>()
        var payments = emptyList<Payment>()
        var summary: PaymentSummary? = null
        override suspend fun request(id: String) = ApiResult.Success(
            ServiceRequest(
                id = id, status = status, isEmergency = false, description = "Fuga", categoryName = "Plomería",
                clientName = "Carla Prueba", clientRating = 0.0, zone = "Torreón Jardín", position = Coordinates(25.6054, -103.3593),
                basePrice = "300.00", photoUrls = emptyList(), createdAt = null, expiresAt = null, scheduledAt = null,
                technicianName = "juan", street = "Av. Morelos 845", agreedPrice = "300.00",
                adjustments = adjustments, payments = payments, paymentSummary = summary,
            ),
        ).also { requestCalls++ }
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

        val uploads = mutableListOf<String>()
        /** Nombres de archivo cuya subida falla. */
        val failingUploads = mutableSetOf<String>()
        val cancels = mutableListOf<Pair<String, List<String>>>()
        var cancelError: ApiError? = null
        override suspend fun uploadEvidence(name: String, mimeType: String, bytes: ByteArray): ApiResult<String> {
            uploads += name
            if (name in failingUploads) return ApiResult.Failure(ApiError.NoConnection)
            return ApiResult.Success("evidences/7/$name")
        }
        override suspend fun cancel(id: String, reason: String, evidenceKeys: List<String>): ApiResult<Unit> {
            cancels += reason to evidenceKeys
            cancelError?.let { return ApiResult.Failure(it) }
            status = RequestStatus.Canceled
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

    private class FakePayments : PaymentRepository {
        val proposals = mutableListOf<Pair<AdjustmentDraft, String>>()
        val responses = mutableListOf<Triple<String, Boolean, String?>>()
        var error: ApiError? = null
        var onPropose: () -> Unit = {}
        override suspend fun proposeAdjustment(
            requestId: String, amountCents: Long, reason: AdjustmentReason, description: String?, clientUuid: String,
        ): ApiResult<Unit> {
            proposals += AdjustmentDraft(amountCents, reason, description) to clientUuid
            error?.let { return ApiResult.Failure(it) }
            onPropose()
            return ApiResult.Success(Unit)
        }
        override suspend fun respondPayment(requestId: String, paymentId: String, received: Boolean, reason: String?): ApiResult<Unit> {
            responses += Triple(paymentId, received, reason)
            return error?.let { ApiResult.Failure(it) } ?: ApiResult.Success(Unit)
        }
        override suspend fun respondAdjustment(requestId: String, adjustmentId: String, approve: Boolean) = ApiResult.Success(Unit)
        override suspend fun registerPayment(requestId: String, method: PaymentMethod, amountCents: Long, reference: String?, clientUuid: String) =
            ApiResult.Success(Unit)
        override suspend fun debts() = ApiResult.Success(Debts("0.00", emptyList()))
        override suspend fun sendReminder(requestId: String) = ApiResult.Success(Unit)
    }

    private val payments = FakePayments()
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
        viewModel = TripViewModel(SavedStateHandle(mapOf("requestId" to "15")), repository, location, trip, payments) { _, _ ->
            error("En las pruebas la evidencia se agrega con addEvidence")
        }
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

    private fun pendingAdjustment() = PriceAdjustment("4", "120.00", AdjustmentReason.ExtraMaterials, null, AdjustmentStatus.Pending, null)

    @Test
    fun atTheHomeItProposesAnAdjustmentAndCannotFinishUntilTheClientAnswers() {
        create(RequestStatus.Arrived)
        assertTrue(state.canProposeAdjustment)
        payments.onPropose = { repository.adjustments = listOf(pendingAdjustment()) }

        viewModel.openAdjustment()
        viewModel.proposeAdjustment(AdjustmentDraft(12_000, AdjustmentReason.ExtraMaterials, null))
        scheduler.runCurrent()

        assertFalse(state.showAdjustmentSheet)
        assertTrue(state.waitsAdjustmentAnswer)
        assertFalse(state.canProposeAdjustment)
        // Pendiente todavía no suma al total.
        assertEquals(30_000L, state.request?.totalCents)
        // Sin respuesta del cliente el botón Finalizar no hace nada.
        viewModel.advance()
        scheduler.runCurrent()
        assertTrue(repository.steps.isEmpty())

        // El cliente lo aprueba: se ve solo, sin recargar a mano, y ya puede finalizar.
        repository.adjustments = listOf(pendingAdjustment().copy(status = AdjustmentStatus.Approved))
        scheduler.advanceTimeBy(TripViewModel.WATCH_INTERVAL_MS + 1)
        scheduler.runCurrent()
        assertFalse(state.waitsAdjustmentAnswer)
        assertEquals(42_000L, state.request?.totalCents)
        viewModel.advance()
        scheduler.runCurrent()
        assertEquals(TripStep.Finish, repository.steps.single().first)
    }

    @Test
    fun retryingAFailedAdjustmentUsesTheSameKey() {
        create(RequestStatus.Arrived)
        payments.error = ApiError.NoConnection

        viewModel.openAdjustment()
        viewModel.proposeAdjustment(AdjustmentDraft(5_000, AdjustmentReason.Other, "Llave de paso"))
        scheduler.runCurrent()
        assertTrue(state.showAdjustmentSheet)
        assertEquals(ApiError.NoConnection, state.paymentError)

        payments.error = null
        viewModel.proposeAdjustment(AdjustmentDraft(5_000, AdjustmentReason.Other, "Llave de paso"))
        scheduler.runCurrent()
        assertEquals(2, payments.proposals.size)
        assertEquals(payments.proposals[0].second, payments.proposals[1].second)
        assertFalse(state.showAdjustmentSheet)
    }

    @Test
    fun finishedItConfirmsThePaymentTheClientRegistered() {
        create(RequestStatus.Completed)
        repository.payments = listOf(Payment("7", "300.00", PaymentMethod.Cash, null, PaymentStatus.Pending, null, null))
        repository.summary = PaymentSummary("300.00", "0.00", "300.00", "300.00", true)
        // Con saldo se revisa cada pocos segundos si el cliente ya registró su pago.
        scheduler.advanceTimeBy(TripViewModel.WATCH_INTERVAL_MS + 1)
        scheduler.runCurrent()
        assertEquals("7", state.request?.pendingPayment?.id)

        repository.payments = listOf(Payment("7", "300.00", PaymentMethod.Cash, null, PaymentStatus.Confirmed, null, null))
        repository.summary = PaymentSummary("300.00", "300.00", "0.00", "0.00", false)
        viewModel.confirmPayment()
        scheduler.runCurrent()

        assertEquals(listOf(Triple("7", true, null as String?)), payments.responses)
        assertTrue(state.request?.paymentSummary?.isPaid == true)
        // Ya pagado deja de consultar.
        val calls = repository.requestCalls
        scheduler.advanceTimeBy(TripViewModel.WATCH_INTERVAL_MS * 3)
        scheduler.runCurrent()
        assertEquals(calls, repository.requestCalls)
    }

    @Test
    fun ifThePaymentDidNotArriveItSaysWhy() {
        repositoryWithPendingPayment()

        viewModel.openRejectPayment()
        viewModel.rejectPayment("No llegó la transferencia")
        scheduler.runCurrent()

        assertEquals(listOf(Triple("7", false, "No llegó la transferencia")), payments.responses)
        assertFalse(state.showRejectPaymentSheet)
    }

    private fun repositoryWithPendingPayment() {
        create(RequestStatus.Completed)
        repository.payments = listOf(Payment("7", "300.00", PaymentMethod.Transfer, "ABC", PaymentStatus.Pending, null, null))
        repository.summary = PaymentSummary("300.00", "0.00", "300.00", "300.00", true)
        scheduler.advanceTimeBy(TripViewModel.WATCH_INTERVAL_MS + 1)
        scheduler.runCurrent()
    }

    // ANDROID-21: cancelar con evidencia.

    private fun photo(name: String) = EvidenceReadResult.Ready(name, "image/jpeg", byteArrayOf(1, 2, 3))

    private fun addEvidence(result: EvidenceReadResult) {
        viewModel.addEvidence { result }
        scheduler.runCurrent()
    }

    @Test
    fun onlyAnAcceptedServiceThatHasNotEndedCanBeCanceled() {
        for (status in listOf(RequestStatus.Accepted, RequestStatus.OnTheWay, RequestStatus.Arrived)) {
            create(status)
            assertTrue(status.name, state.canCancel)
        }
        create(RequestStatus.Completed)
        assertFalse(state.canCancel)
        viewModel.openCancel()
        assertFalse(state.showCancelSheet)
    }

    @Test
    fun withoutAReasonItIsNotCanceled() {
        create(RequestStatus.OnTheWay)
        viewModel.openCancel()

        viewModel.confirmCancel()
        scheduler.runCurrent()

        assertTrue(state.cancelForm.reasonMissing)
        assertTrue(repository.cancels.isEmpty())
    }

    @Test
    fun theEvidenceIsUploadedAndItsKeysGoWithTheCancellation() {
        create(RequestStatus.OnTheWay)
        viewModel.openCancel()
        viewModel.onCancelReasonChange("  Se ponchó una llanta  ")
        addEvidence(photo("llanta.jpg"))
        addEvidence(EvidenceReadResult.Ready("reporte.pdf", "application/pdf", byteArrayOf(9)))
        assertFalse(state.cancelForm.withoutEvidence)

        viewModel.confirmCancel()
        scheduler.runCurrent()

        assertEquals(listOf("llanta.jpg", "reporte.pdf"), repository.uploads)
        assertEquals(
            listOf("Se ponchó una llanta" to listOf("evidences/7/llanta.jpg", "evidences/7/reporte.pdf")),
            repository.cancels,
        )
        assertTrue(state.canceled)
        assertFalse(state.showCancelSheet)
        // Ya no comparte su ubicación con el cliente.
        assertEquals(1, trip.stopped)
    }

    @Test
    fun withoutEvidenceItWarnsAndStillCancels() {
        create(RequestStatus.Accepted)
        viewModel.openCancel()
        viewModel.onCancelReasonChange("El cliente no contesta")
        assertTrue(state.cancelForm.withoutEvidence)

        viewModel.confirmCancel()
        scheduler.runCurrent()

        assertEquals(listOf("El cliente no contesta" to emptyList<String>()), repository.cancels)
        assertTrue(state.canceled)
    }

    @Test
    fun ifAnUploadFailsTheRetryDoesNotUploadAgainWhatWasAlreadyUp() {
        create(RequestStatus.Arrived)
        viewModel.openCancel()
        viewModel.onCancelReasonChange("Falta material")
        addEvidence(photo("uno.jpg"))
        addEvidence(photo("dos.jpg"))
        repository.failingUploads += "dos.jpg"

        viewModel.confirmCancel()
        scheduler.runCurrent()

        assertEquals(ApiError.NoConnection, state.cancelForm.error)
        assertTrue(repository.cancels.isEmpty())
        assertFalse(state.canceled)

        repository.failingUploads.clear()
        viewModel.confirmCancel()
        scheduler.runCurrent()

        assertEquals(listOf("uno.jpg", "dos.jpg", "dos.jpg"), repository.uploads)
        assertEquals(listOf("evidences/7/uno.jpg", "evidences/7/dos.jpg"), repository.cancels.single().second)
        assertTrue(state.canceled)
    }

    @Test
    fun aFileThatCannotBeUsedIsExplainedAndNotAdded() {
        create(RequestStatus.OnTheWay)
        viewModel.openCancel()

        addEvidence(EvidenceReadResult.Rejected(EvidenceProblem.TooLarge))

        assertEquals(EvidenceProblem.TooLarge, state.cancelForm.problem)
        assertTrue(state.cancelForm.evidence.isEmpty())
    }

    @Test
    fun noMoreThanFiveFiles() {
        create(RequestStatus.OnTheWay)
        viewModel.openCancel()
        repeat(6) { addEvidence(photo("foto$it.jpg")) }

        assertEquals(CancelRules.MAX_EVIDENCE, state.cancelForm.evidence.size)
        assertFalse(state.cancelForm.canAddEvidence)

        viewModel.removeEvidence(state.cancelForm.evidence.first().id)
        assertTrue(state.cancelForm.canAddEvidence)
    }

    @Test
    fun ifTheServiceWasNoLongerActiveTheErrorShowsAndItReloads() {
        create(RequestStatus.OnTheWay)
        viewModel.openCancel()
        viewModel.onCancelReasonChange("No llego")
        repository.cancelError = ApiError.Http(409, "INVALID_STATUS_TRANSITION", "La solicitud no se encuentra en un estado que permita cancelación.")
        repository.status = RequestStatus.Canceled
        val loadsBefore = repository.requestCalls

        viewModel.confirmCancel()
        scheduler.runCurrent()

        assertFalse(state.canceled)
        assertTrue(state.cancelForm.error is ApiError.Http)
        assertTrue(repository.requestCalls > loadsBefore)
        assertEquals(RequestStatus.Canceled, state.request?.status)
    }
}
