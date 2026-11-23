package com.fixora.app.feature.payment.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.payment.domain.AdjustmentReason
import com.fixora.app.feature.payment.domain.PaymentMethod
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.time.Instant
import java.util.concurrent.TimeUnit

class RemotePaymentRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemotePaymentRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<PaymentApi>()
        repository = RemotePaymentRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private fun taken(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun RecordedRequest.bodyText(): String = body?.utf8().orEmpty()

    @Test
    fun proposesAnAdjustmentWithItsKey() = runBlocking {
        respond("""{"success":true,"data":{"id":"4","amount":"120.50","reason":"otro","description":"Llave","status":"pendiente"}}""", 201)

        val result = repository.proposeAdjustment("15", 12_050, AdjustmentReason.Other, "  Llave ", "uuid-1")

        assertTrue(result is ApiResult.Success)
        val request = taken()
        assertEquals("/api/service-requests/15/price-adjustments", request.url.encodedPath)
        assertEquals(
            """{"amount":"120.50","reason":"otro","description":"Llave","client_uuid":"uuid-1"}""",
            request.bodyText(),
        )
    }

    @Test
    fun registersAPaymentAndOnlySendsTheReferenceForTransfers() = runBlocking {
        respond("""{"success":true,"data":{"id":"7","amount":"300.00","method":"efectivo","status":"pendiente"}}""", 201)
        repository.registerPayment("15", PaymentMethod.Cash, 30_000, "ABC", "uuid-2")
        val cash = taken()
        assertEquals("/api/service-requests/15/payments", cash.url.encodedPath)
        assertEquals("""{"method":"efectivo","amount":"300.00","client_uuid":"uuid-2"}""", cash.bodyText())

        respond("""{"success":true,"data":{"id":"8","amount":"100.00","method":"transferencia","status":"pendiente"}}""", 201)
        repository.registerPayment("15", PaymentMethod.Transfer, 10_000, " ABC ", "uuid-3")
        assertEquals("""{"method":"transferencia","amount":"100.00","reference":"ABC","client_uuid":"uuid-3"}""", taken().bodyText())
    }

    @Test
    fun respondsToAdjustmentsAndPayments() = runBlocking {
        repeat(3) { respond("""{"success":true,"data":{}}""") }
        repository.respondAdjustment("15", "4", approve = false)
        assertEquals("/api/service-requests/15/price-adjustments/4/reject", taken().url.encodedPath)
        repository.respondPayment("15", "7", received = true, reason = null)
        assertEquals("/api/service-requests/15/payments/7/confirm", taken().url.encodedPath)
        repository.respondPayment("15", "7", received = false, reason = "No llegó")
        val reject = taken()
        assertEquals("/api/service-requests/15/payments/7/reject", reject.url.encodedPath)
        assertEquals("""{"reason":"No llegó"}""", reject.bodyText())
    }

    @Test
    fun readsTheDebtsWithTheirReminders() = runBlocking {
        respond(
            """{"success":true,"data":{"total_due":"580.00","debts":[
            {"request_id":"62","category":"Plomería","completed_at":"2026-11-22T18:00:00.000Z","total_amount":"470.00","paid":"400.00",
             "pending_confirmation":"0.00","balance_due":"70.00","has_debt":true,"reminders_sent":1,
             "last_reminder_at":"2026-11-23T10:00:00.000Z","next_reminder_at":"2026-11-24T10:00:00.000Z"},
            {"request_id":"63","category":null,"completed_at":null,"total_amount":"510.00","paid":"0.00",
             "pending_confirmation":"0.00","balance_due":"510.00","has_debt":true,"reminders_sent":0,
             "last_reminder_at":null,"next_reminder_at":null}]}}""",
        )

        val debts = (repository.debts() as ApiResult.Success).data

        assertEquals("/api/me/debts", taken().url.encodedPath)
        assertEquals("580.00", debts.totalDue)
        assertEquals(Instant.parse("2026-11-24T10:00:00Z"), debts.debts[0].nextReminderAt)
        assertEquals("70.00", debts.debts[0].summary.balanceDue)
        assertEquals("", debts.debts[1].category)
        assertNull(debts.debts[1].lastReminderAt)
        assertTrue(debts.debts[1].canRemind)
    }

    @Test
    fun aReminderTooSoonComesBackWithItsCode() = runBlocking {
        respond(
            """{"success":false,"error":{"code":"REMINDER_TOO_SOON","message":"Ya se le recordó en las últimas 24 horas."}}""",
            409,
        )

        val result = repository.sendReminder("62")

        assertEquals("/api/service-requests/62/debt-reminders", taken().url.encodedPath)
        val error = (result as ApiResult.Failure).error as ApiError.Http
        assertEquals("REMINDER_TOO_SOON", error.code)
    }
}
