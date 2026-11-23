package com.fixora.app.feature.map.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.map.domain.MapFilters
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

class RemoteMapRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteMapRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<MapApi>()
        repository = RemoteMapRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private fun take(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private val center = Coordinates(19.43, -99.13)

    @Test
    fun searchSendsTheFiltersAndReadsEachBranch() = runBlocking {
        respond(
            """{"success":true,"message":"ok","pagination":{"page":1,"limit":50,"total":72,"total_pages":2},
            "data":[{"id":"2","name":"Matriz","business_name":"Plomería Prueba","supplier_type":"independiente",
            "logo_url":null,"latitude":19.4326,"longitude":-99.1332,"distance_km":0.4,"rating_avg":4.5,
            "reviews_count":3,"base_price":"250.00","open_now":true}]}""",
        )

        val result = (repository.search(
            center,
            MapFilters(radiusKm = 5, availableNow = true, minRating = 4.5, maxPrice = 500),
            categoryId = "1",
        ) as ApiResult.Success).data

        val url = take().url
        assertEquals("/api/search/branches", url.encodedPath)
        assertEquals("19.43", url.queryParameter("lat"))
        assertEquals("-99.13", url.queryParameter("lng"))
        assertEquals("5", url.queryParameter("radius_km"))
        assertEquals("1", url.queryParameter("category_id"))
        assertEquals("true", url.queryParameter("available_now"))
        assertEquals("4.5", url.queryParameter("min_rating"))
        assertEquals("500", url.queryParameter("max_price"))
        assertEquals("distancia", url.queryParameter("sort"))
        assertEquals("50", url.queryParameter("limit"))
        assertEquals(72, result.total)
        val provider = result.providers.single()
        assertTrue(provider.independent)
        assertEquals(Coordinates(19.4326, -99.1332), provider.position)
        assertEquals("250.00", provider.basePrice)
        assertEquals(3, provider.reviewsCount)
    }

    @Test
    fun generalSearchOmitsCategoryAndAvailability() = runBlocking {
        respond("""{"success":true,"data":[],"pagination":{"total":0}}""")

        repository.search(center, MapFilters(), categoryId = null)

        val url = take().url
        assertNull(url.queryParameter("category_id"))
        assertNull(url.queryParameter("available_now"))
        assertNull(url.queryParameter("min_rating"))
        assertNull(url.queryParameter("max_price"))
        assertEquals("10", url.queryParameter("radius_km"))
    }

    @Test
    fun categoriesKeepOnlyActiveInTheirOrder() = runBlocking {
        respond(
            """{"success":true,"data":[
            {"id":"2","name":"Electricidad","slug":"electricidad","active":true,"sort_order":2},
            {"id":"9","name":"Vieja","slug":"vieja","active":false,"sort_order":0},
            {"id":"1","name":"Plomería","slug":"plomeria","active":true,"sort_order":1}]}""",
        )

        val categories = (repository.categories() as ApiResult.Success).data

        assertEquals("/api/categories", take().url.encodedPath)
        assertEquals(listOf("1", "2"), categories.map { it.id })
        assertFalse(categories.any { it.slug == "vieja" })
    }

    @Test
    fun invalidCoordinatesReturnTheApiError() = runBlocking {
        respond("""{"success":false,"error":"BAD_REQUEST","message":"El parámetro lat es obligatorio."}""", code = 400)

        val error = (repository.search(center, MapFilters(), null) as ApiResult.Failure).error

        assertEquals(400, (error as ApiError.Http).status)
    }
}
