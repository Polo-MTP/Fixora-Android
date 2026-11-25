package com.fixora.app.feature.review.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.review.domain.ReviewDirection
import com.fixora.app.feature.review.domain.ReviewEvidence
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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
import java.util.concurrent.TimeUnit

/** Reseñas contra el formato real de API-39 (ANDROID-26). */
class RemoteReviewRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteReviewRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<ReviewApi>()
        repository = RemoteReviewRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    @Test
    fun readsWhoRatesAndTheReviewsOfTheRequest() = runBlocking {
        respond(
            """{"success":true,"data":{"request_id":"31","my_direction":"proveedor_a_cliente","can_review":false,
            "reviews":[{"id":"4","request_id":"31","direction":"proveedor_a_cliente","rating":5,"comment":" Puntual ",
            "author_id":"9","author_name":"Juan P.","reviewee_id":"4","client_uuid":null,
            "created_at":"2026-11-24T20:10:00.000Z",
            "evidence":[{"id":"1","mime_type":"video/mp4","size_bytes":10,"url":"https://f/v.mp4"},
                        {"id":"2","mime_type":"image/jpeg","size_bytes":10,"url":null}]}]}}""",
        )

        val data = (repository.requestReviews("31") as ApiResult.Success).data

        assertEquals("/api/service-requests/31/reviews", server.takeRequest(2, TimeUnit.SECONDS)!!.target)
        assertEquals(ReviewDirection.ProviderToClient, data.myDirection)
        assertFalse(data.canReview)
        val mine = data.mine!!
        assertEquals("Puntual", mine.comment)
        assertEquals(Instant.parse("2026-11-24T20:10:00Z"), mine.createdAt)
        // Sin enlace no se muestra.
        assertEquals(listOf(ReviewEvidence("https://f/v.mp4", isVideo = true)), mine.evidence)
    }

    @Test
    fun sendsTheRatingWithItsUuidAndOnlyTheFieldsThatHaveSomething() = runBlocking {
        respond("""{"success":true,"data":{"id":"5","direction":"cliente_a_proveedor","rating":4,"comment":null,"author_name":"Ana P."}}""", code = 201)

        val result = repository.create("31", 4, null, emptyList(), "6a0b6d2e-5f41-4c4b-9b1a-1f1f2f3f4f5f")

        assertTrue(result is ApiResult.Success)
        val sent = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", sent.method)
        val body = json.parseToJsonElement(sent.body!!.utf8()).jsonObject
        assertEquals(setOf("rating", "client_uuid"), body.keys)
        assertEquals("4", body["rating"]!!.jsonPrimitive.content)
    }

    @Test
    fun alreadyRatedReturnsTheApiMessage() = runBlocking {
        respond(
            """{"success":false,"error":{"code":"REVIEW_ALREADY_EXISTS","message":"Esta solicitud ya fue calificada en este sentido."}}""",
            code = 409,
        )

        val error = (repository.create("31", 5, "Bien", emptyList(), "6a0b6d2e-5f41-4c4b-9b1a-1f1f2f3f4f5f") as ApiResult.Failure).error

        assertEquals(ApiError.Http(409, "REVIEW_ALREADY_EXISTS", "Esta solicitud ya fue calificada en este sentido."), error)
    }

    @Test
    fun branchReviewsComeByPages() = runBlocking {
        respond(
            """{"success":true,"data":{"items":[{"id":"8","rating":3,"comment":"Regular","author_name":"Luis G.",
            "created_at":"2026-11-20T10:00:00.000Z","evidence":[]}],
            "pagination":{"page":2,"limit":10,"total":11,"total_pages":2}}}""",
        )

        val page = (repository.branchReviews("12", 2, 10) as ApiResult.Success).data

        assertEquals("/api/branches/12/reviews?page=2&limit=10", server.takeRequest(2, TimeUnit.SECONDS)!!.target)
        assertEquals(1, page.items.size)
        assertFalse(page.hasMore)
    }
}
