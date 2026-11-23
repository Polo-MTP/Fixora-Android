package com.fixora.app.feature.providerprofile.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.providerprofile.domain.DaySchedule
import com.fixora.app.feature.providerprofile.domain.Review
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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

class RemoteProviderProfileRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteProviderProfileRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<ProviderProfileApi>()
        repository = RemoteProviderProfileRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    @Test
    fun readsTheWholePublicProfile() = runBlocking {
        respond(
            """{"success":true,"data":{"id":"2","name":"Matriz","business_name":"Plomería Prueba",
            "supplier_type":"independiente","description":"  Instalaciones  ","logo_url":null,"phone":"55",
            "is_verified":true,"plates":["ABC-123",""],"rating_avg":4.6,"reviews_count":7,"total_jobs":12,
            "accepting_request":false,
            "location":{"colony":"Juárez","municipality":"Cuauhtémoc","latitude":19.43,"longitude":-99.13},
            "schedules":[{"day_of_week":1,"open_time":"08:00","close_time":"18:00","active":true},
                         {"day_of_week":0,"open_time":"09:00","close_time":"13:00","active":false}],
            "categories":[{"category_id":"1","category_name":"Plomería","icon_key":null,"base_price":"250.00","duration_minutes":90}],
            "portfolio":[{"id":"3","branch_id":"2","title":"Baño","description":null,
                          "media":[{"id":"1","file_key":"photos/9/a.jpg","url":"https://f/a"},{"id":"2","url":""}]}],
            "certificates":[{"id":"10","type_name":"DC-3","file_key":"documents/9/dc3.pdf","url":"https://f/dc3"},
                            {"id":"11","type_name":"Foto","file_key":"documents/9/c.jpg","url":"https://f/c"},
                            {"id":"12","type_name":"Sin enlace","file_key":"documents/9/x.pdf","url":""}],
            "latest_reviews":[{"rating":5,"comment":" Muy puntual. ","author_name":"Roberto M."},
                              {"rating":7,"comment":null,"author_name":"Lucía G."}]}}""",
        )

        val profile = (repository.profile("2") as ApiResult.Success).data

        assertEquals("/api/branches/2/public", checkNotNull(server.takeRequest(2, TimeUnit.SECONDS)).url.encodedPath)
        assertTrue(profile.independent)
        assertTrue(profile.verified)
        assertFalse(profile.acceptingRequests)
        assertEquals("Instalaciones", profile.description)
        assertEquals(listOf("ABC-123"), profile.plates)
        assertEquals(Coordinates(19.43, -99.13), profile.position)
        assertEquals(listOf(DaySchedule(1, "08:00", "18:00")), profile.schedules)
        assertEquals(90, profile.services.single().durationMinutes)
        assertEquals(listOf("https://f/a"), profile.works.single().photoUrls)
        assertEquals("", profile.works.single().description)
        assertEquals(listOf(true, false), profile.certificates.map { it.isPdf })
        assertEquals(
            listOf(Review(5, "Muy puntual.", "Roberto M."), Review(5, "", "Lucía G.")),
            profile.latestReviews,
        )
    }

    @Test
    fun withoutCoordinatesThereIsNoPosition() = runBlocking {
        respond("""{"success":true,"data":{"id":"5","name":"Sur","location":{"colony":"","municipality":"","latitude":null,"longitude":null}}}""")

        val profile = (repository.profile("5") as ApiResult.Success).data

        assertNull(profile.position)
        assertEquals("Sur", profile.title)
        assertFalse(profile.hasReviews)
        assertTrue(profile.latestReviews.isEmpty())
    }

    @Test
    fun unknownBranchIsA404() = runBlocking {
        respond("""{"success":false,"error":"NOT_FOUND","message":"Sucursal no encontrada."}""", code = 404)

        val error = (repository.profile("999") as ApiResult.Failure).error

        assertEquals(404, (error as ApiError.Http).status)
    }
}
