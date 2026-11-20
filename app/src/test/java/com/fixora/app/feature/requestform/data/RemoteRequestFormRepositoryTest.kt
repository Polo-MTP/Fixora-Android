package com.fixora.app.feature.requestform.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.requestform.domain.CurrentAddress
import com.fixora.app.feature.requestform.domain.RequestDraft
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class RemoteRequestFormRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteRequestFormRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<RequestFormApi>()
        repository = RemoteRequestFormRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private fun take(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun RecordedRequest.jsonBody(): JsonObject = json.parseToJsonElement(checkNotNull(body).utf8()).jsonObject

    private val draft = RequestDraft(
        clientUuid = "6f1c2b9e-3d1a-4b2c-9e8f-1a2b3c4d5e6f",
        branchId = "2",
        categoryId = "1",
        description = "Fuga de agua. Gotea el lavabo",
        savedAddressId = "14",
        currentAddress = null,
        photoKeys = listOf("photos/30/a.jpg"),
        scheduledAt = null,
    )

    @Test
    fun withASavedAddressSendsItsId() = runBlocking {
        respond("""{"success":true,"data":{"id":"31","status":"pendiente"}}""", code = 201)

        val id = (repository.create(draft) as ApiResult.Success).data

        val body = take().jsonBody()
        assertEquals("31", id)
        assertEquals("14", body["address_id"]?.jsonPrimitive?.content)
        assertFalse("address" in body)
        assertFalse("scheduled_at" in body)
        assertEquals("photos/30/a.jpg", body["attachment_keys"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("6f1c2b9e-3d1a-4b2c-9e8f-1a2b3c4d5e6f", body["client_uuid"]?.jsonPrimitive?.content)
    }

    @Test
    fun withTheCurrentLocationSendsTheAddressAndTheAppointment() = runBlocking {
        respond("""{"success":true,"data":{"id":"32"}}""", code = 201)
        val here = CurrentAddress("Av. Juárez", "", "Centro", "Cuauhtémoc", "CDMX", "06000", Coordinates(19.43, -99.13))

        repository.create(
            draft.copy(savedAddressId = null, currentAddress = here, photoKeys = emptyList(), scheduledAt = Instant.parse("2026-11-21T16:00:00Z")),
        )

        val body = take().jsonBody()
        val address = body["address"]!!.jsonObject
        assertFalse("address_id" in body)
        assertFalse("attachment_keys" in body)
        assertEquals("S/N", address["num_ext"]?.jsonPrimitive?.content)
        assertEquals("06000", address["postal_code"]?.jsonPrimitive?.content)
        assertEquals("2026-11-21T16:00:00Z", body["scheduled_at"]?.jsonPrimitive?.content)
    }

    @Test
    fun freeSlotsOfADay() = runBlocking {
        respond("""{"success":true,"data":[{"starts_at":"2026-11-21T15:00:00.000Z","ends_at":"2026-11-21T16:00:00.000Z"}]}""")

        val slots = (repository.freeSlots("2", "1", LocalDate.of(2026, 11, 21)) as ApiResult.Success).data

        val url = take().url
        assertEquals("/api/branches/2/availability", url.encodedPath)
        assertEquals("1", url.queryParameter("category_id"))
        assertEquals("2026-11-21", url.queryParameter("date"))
        assertEquals(Instant.parse("2026-11-21T15:00:00Z"), slots.single().startsAt)
    }

    @Test
    fun photosAreUploadedOneByOne() = runBlocking {
        respond("""{"success":true,"data":{"file_key":"photos/30/uno.jpg"}}""", code = 201)
        respond("""{"success":true,"data":{"file_key":"photos/30/dos.jpg"}}""", code = 201)

        val keys = (repository.uploadPhotos(listOf(byteArrayOf(1), byteArrayOf(2))) as ApiResult.Success).data

        assertEquals(listOf("photos/30/uno.jpg", "photos/30/dos.jpg"), keys)
        val first = take()
        assertEquals("/api/files/upload", first.url.encodedPath)
        assertTrue(checkNotNull(first.body).utf8().contains("name=\"category\""))
    }

    @Test
    fun providerNotAcceptingIsAnApiError() = runBlocking {
        respond("""{"success":false,"error":"BRANCH_NOT_ACCEPTING","message":"La sucursal no acepta solicitudes."}""", code = 409)

        val error = (repository.create(draft) as ApiResult.Failure).error

        assertEquals(409, (error as ApiError.Http).status)
    }
}
