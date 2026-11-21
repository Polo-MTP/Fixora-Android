package com.fixora.app.feature.servicerequest.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.emergency.data.EmergencyApi
import com.fixora.app.feature.requestform.data.RequestFormApi
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

class RemoteClientRequestRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteClientRequestRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create<ServiceRequestApi>()
        repository = RemoteClientRequestRepository(
            api, retrofit.create<RequestFormApi>(), retrofit.create<EmergencyApi>(), RemoteServiceRequestRepository(api, json), json,
        )
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private val base = ServiceRequest(
        id = "52", status = RequestStatus.Expired, isEmergency = false, description = "Fuga de agua", categoryName = "Plomería",
        clientName = "", clientRating = 0.0, zone = "", position = Coordinates(25.59, -103.35), basePrice = null,
        photoUrls = emptyList(), createdAt = null, expiresAt = null, scheduledAt = null, technicianName = null,
        categoryId = "1", branchId = "70", addressId = "14", photoKeys = listOf("photos/30/a.jpg"),
    )

    @Test
    fun readsTheDispatchSummary() = runBlocking {
        respond(
            """{"success":true,"data":{"id":"52","status":"pendiente","is_emergency":true,"category":{"id":"1","name":"Plomería"},
            "branch":null,"client":{"name":"Carla","rating_avg":0},"attachments":[],"dispatches":{"sent":4,"rejected":4}}}""",
        )

        val request = (repository.request("52") as ApiResult.Success).data

        assertEquals(4, request.dispatchesSent)
        assertTrue(request.wasRejected)
    }

    @Test
    fun cancelSendsAnEmptyBody() = runBlocking {
        respond("""{"success":true,"data":{"id":"52","status":"cancelada"}}""")

        repository.cancel("52")

        val sent = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/api/service-requests/52/cancel", sent.url.encodedPath)
        assertEquals("{}", checkNotNull(sent.body).utf8())
    }

    @Test
    fun resendingANormalRequestRepeatsItsData() = runBlocking {
        respond("""{"success":true,"data":{"id":"60"}}""", code = 201)

        val id = (repository.resend(base, "6f1c2b9e-3d1a-4b2c-9e8f-1a2b3c4d5e6f") as ApiResult.Success).data

        val sent = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val body = json.parseToJsonElement(checkNotNull(sent.body).utf8()).jsonObject
        assertEquals("/api/service-requests", sent.url.encodedPath)
        assertEquals("70", body["branch_id"]?.jsonPrimitive?.content)
        assertEquals("14", body["address_id"]?.jsonPrimitive?.content)
        assertEquals("photos/30/a.jpg", body["attachment_keys"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("60", id)
    }

    @Test
    fun resendingAnEmergencyUsesItsLocation() = runBlocking {
        respond("""{"success":true,"data":{"id":"61"}}""", code = 201)

        repository.resend(base.copy(isEmergency = true, branchId = null), "6f1c2b9e-3d1a-4b2c-9e8f-1a2b3c4d5e6f")

        val sent = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val body = json.parseToJsonElement(checkNotNull(sent.body).utf8()).jsonObject
        assertEquals("/api/service-requests/emergency", sent.url.encodedPath)
        assertEquals(setOf("client_uuid", "category_id", "latitude", "longitude"), body.keys)
    }
}
