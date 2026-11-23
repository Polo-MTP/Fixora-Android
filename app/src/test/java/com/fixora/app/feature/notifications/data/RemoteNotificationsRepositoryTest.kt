package com.fixora.app.feature.notifications.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.TimeUnit

class RemoteNotificationsRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteNotificationsRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<NotificationsApi>()
        repository = RemoteNotificationsRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String = """{"success":true,"message":"ok"}""") = server.enqueue(
        MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private fun take(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    @Test
    fun pageReadsTheListAndTheUnreadCount() = runBlocking {
        respond(
            """{"success":true,"message":"ok","unread_count":3,
            "pagination":{"page":1,"limit":20,"total":25,"total_pages":2},
            "data":[{"id":"9","type":"solicitud_aceptada","title":" Actualización de Solicitud #23 ","message":"Aceptada.",
                     "entity_type":"service_request","entity_id":"23","read_at":null,"created_at":"2026-11-20T18:30:00.000Z"},
                    {"id":"8","type":"verificacion_proveedor","title":"Aprobada","message":"","entity_type":null,
                     "entity_id":null,"read_at":"2026-11-20T18:00:00.000Z","created_at":"2026-11-19T10:00:00.000Z"}]}""",
        )

        val page = (repository.page(1) as ApiResult.Success).data

        val url = take().url
        assertEquals("/api/me/notifications", url.encodedPath)
        assertEquals("1", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("limit"))
        assertEquals(null, url.queryParameter("unread"))
        assertEquals(listOf("9", "8"), page.items.map { it.id })
        assertEquals("Actualización de Solicitud #23", page.items[0].title)
        assertFalse(page.items[0].isRead)
        assertTrue(page.items[1].isRead)
        assertTrue(page.hasMore)
        assertEquals(3, repository.unreadCount.value)
    }

    @Test
    fun theBellCountsOnlyUnread() = runBlocking {
        respond("""{"success":true,"data":[],"unread_count":5,"pagination":{"page":1,"total_pages":5}}""")

        repository.refreshUnread()

        val url = take().url
        assertEquals("true", url.queryParameter("unread"))
        assertEquals("1", url.queryParameter("limit"))
        assertEquals(5, repository.unreadCount.value)
    }

    @Test
    fun readingLowersTheCount() = runBlocking {
        respond("""{"success":true,"data":[],"unread_count":2,"pagination":{"page":1,"total_pages":1}}""")
        repository.refreshUnread()
        take()

        respond()
        repository.markRead("9")
        assertEquals("/api/me/notifications/9/read", take().url.encodedPath)
        assertEquals(1, repository.unreadCount.value)

        respond()
        repository.markAllRead()
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/me/notifications/read-all", request.url.encodedPath)
        assertEquals(0, repository.unreadCount.value)
    }
}
