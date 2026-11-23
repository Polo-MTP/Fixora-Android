package com.fixora.app.feature.tracking.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.session.Session
import com.fixora.app.core.session.SessionTokens
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.availability.location.Movement
import com.fixora.app.feature.servicerequest.data.ServiceRequestRepository
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripReporterTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeSocket : TripSocket {
        private val channel = Channel<String>(Channel.UNLIMITED)
        override val incoming: ReceiveChannel<String> = channel
        val sent = mutableListOf<String>()
        var closed = false
        fun receive(text: String) = channel.trySend(text)
        fun drop() = channel.close()
        override fun send(text: String): Boolean {
            if (closed) return false
            sent += text
            return true
        }
        override fun close() {
            closed = true
            channel.close()
        }
    }

    private val sockets = mutableListOf<FakeSocket>()
    private val factory = TripSocketFactory { FakeSocket().also { sockets += it } }

    private var token = "token-1"
    private val tokens = object : SessionTokens {
        override suspend fun current() = Session("7", "tecnico@fixora.test", "empleado", "activo", token, "refresh")
        override suspend fun updateTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun clear() = Unit
    }

    private var status = RequestStatus.OnTheWay
    private var requestError: ApiError? = null
    private var statusChecks = 0
    private val requests = ServiceRequestRepository {
        statusChecks++
        requestError?.let { ApiResult.Failure(it) } ?: ApiResult.Success(request(status))
    }

    private val positions = MutableSharedFlow<Movement>()
    private var askedInterval = 0L
    private var askedDistance = 0f
    private val location = object : LocationSource {
        override fun hasPermission() = true
        override suspend fun current(): Coordinates? = null
        override fun updates(intervalMs: Long): Flow<Coordinates> = emptyFlow()
        override fun movementUpdates(intervalMs: Long, minDistanceMeters: Float): Flow<Movement> {
            askedInterval = intervalMs
            askedDistance = minDistanceMeters
            return positions
        }
    }

    private val reporter = TripReporter(factory, tokens, requests, location, json)

    private fun request(status: RequestStatus) = ServiceRequest(
        id = "15", status = status, isEmergency = false, description = "Fuga", categoryName = "Plomería", clientName = "Carla",
        clientRating = 0.0, zone = "", position = null, basePrice = "300.00", photoUrls = emptyList(), createdAt = null,
        expiresAt = null, scheduledAt = null, technicianName = "juan",
    )

    private fun String.field(name: String) = json.parseToJsonElement(this).jsonObject[name]?.jsonPrimitive?.content

    private val movement = Movement(Coordinates(25.60, -103.36), heading = 90.0, speed = 8.5, timeMillis = 1_763_748_000_000)

    @Test
    fun authenticatesAndThenSendsEachPosition() = runTest {
        val run = async { reporter.run("15") }
        runCurrent()

        val socket = sockets.single()
        assertEquals("authenticate", socket.sent.single().field("type"))
        assertEquals("token-1", socket.sent.single().field("token"))

        socket.receive("""{"type":"authenticated","user":{"id":"7"}}""")
        runCurrent()
        assertEquals(TripReporter.INTERVAL_MS, askedInterval)
        assertEquals(TripReporter.MIN_DISTANCE_METERS, askedDistance)

        positions.emit(movement)
        runCurrent()
        val sent = socket.sent.last()
        assertEquals("technician_location", sent.field("type"))
        assertEquals("15", sent.field("requestId"))
        assertEquals("25.6", sent.field("latitude"))
        assertEquals("-103.36", sent.field("longitude"))
        assertEquals("90.0", sent.field("heading"))
        assertEquals("8.5", sent.field("speed"))
        assertEquals("2025-11-21T18:00:00Z", sent.field("timestamp"))

        // Llegó: la API rechaza la posición y deja de mandar.
        socket.receive("""{"type":"error","code":"NOT_IN_TRANSIT","message":"..."}""")
        runCurrent()
        assertEquals(TripEnd.NotInTransit, run.await())
        assertTrue(socket.closed)
    }

    @Test
    fun ifTheConnectionDropsItReconnectsWithTheCurrentToken() = runTest {
        val run = async { reporter.run("15") }
        runCurrent()
        sockets.single().receive("""{"type":"authenticated"}""")
        runCurrent()

        // Se renovó el token mientras tanto y se cae la señal.
        token = "token-2"
        sockets.single().drop()
        runCurrent()
        assertEquals(1, sockets.size)

        advanceTimeBy(TripReporter.MIN_BACKOFF_MS + 1)
        runCurrent()
        assertEquals(2, sockets.size)
        assertEquals("token-2", sockets.last().sent.single().field("token"))

        run.cancel()
    }

    @Test
    fun withoutInternetItKeepsTryingInsteadOfGivingUp() = runTest {
        requestError = ApiError.NoConnection
        val run = async { reporter.run("15") }
        runCurrent()
        assertTrue(sockets.isEmpty())

        advanceTimeBy(TripReporter.MIN_BACKOFF_MS + 1)
        runCurrent()
        assertEquals(2, statusChecks)

        requestError = null
        advanceTimeBy(TripReporter.MIN_BACKOFF_MS * 2 + 1)
        runCurrent()
        assertEquals(1, sockets.size)
        assertFalse(run.isCompleted)

        run.cancel()
    }

    @Test
    fun ifItIsNoLongerOnTheWayItDoesNotConnect() = runTest {
        status = RequestStatus.Arrived

        assertEquals(TripEnd.NotInTransit, reporter.run("15"))
        assertTrue(sockets.isEmpty())
    }

    @Test
    fun whileStoppedItStillNoticesTheTripEnded() = runTest {
        val run = async { reporter.run("15") }
        runCurrent()
        sockets.single().receive("""{"type":"authenticated"}""")
        runCurrent()

        status = RequestStatus.Canceled
        advanceTimeBy(TripReporter.STATUS_CHECK_MS + 1)
        runCurrent()

        assertEquals(TripEnd.NotInTransit, run.await())
        assertTrue(sockets.single().closed)
    }

    @Test
    fun anotherTechniciansRequestStops() = runTest {
        val run = async { reporter.run("15") }
        runCurrent()
        sockets.single().receive("""{"type":"authenticated"}""")
        runCurrent()
        positions.emit(movement)
        sockets.single().receive("""{"type":"error","code":"INSUFFICIENT_PERMISSIONS"}""")
        runCurrent()

        assertEquals(TripEnd.NotAllowed, run.await())
    }
}
