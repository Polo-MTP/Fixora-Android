package com.fixora.app.feature.chat.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.core.session.Session
import com.fixora.app.core.session.SessionTokens
import com.fixora.app.feature.tracking.data.TripSocketFactory
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
import java.util.concurrent.TimeUnit

class RemoteChatRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteChatRepository
    private val tokens = object : SessionTokens {
        override suspend fun current() = Session("9", "tecnico@fixora.test", "empleado", "activo", "a", "r")
        override suspend fun updateTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun clear() = Unit
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<ChatApi>()
        repository = RemoteChatRepository(api, ChatRealtime(TripSocketFactory { error("sin socket") }, tokens, json), tokens, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private fun taken(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    @Test
    fun readsAPageWithRepliesAndPhotos() = runBlocking {
        respond(
            """{"success":true,"data":{"conversation":{"id":"3","status":"abierta","can_send":true,"unread_count":1},
            "messages":[
              {"id":"40","sender_id":"29","sender_name":"Carla","message":"Así está la fuga","reply_to":{"id":"38","sender_id":"9","message":null,"has_attachments":true},
               "attachments":[{"id":"1","url":"https://f/1"},{"id":"2","url":null}],"client_uuid":"u-1","read_at":null,"created_at":"2026-11-23T18:00:00.000Z"},
              {"id":"41","sender_id":"9","message":"   ","attachments":[],"read_at":"2026-11-23T18:01:00.000Z"}],
            "pagination":{"limit":30,"has_more":true,"next_before":"40","next_after":"41"}}}""",
        )

        val page = (repository.page("15", before = "50") as ApiResult.Success).data

        val request = taken()
        assertEquals("/api/service-requests/15/chat", request.url.encodedPath)
        assertEquals("50", request.url.queryParameter("before"))
        assertNull(request.url.queryParameter("after"))
        assertTrue(page.canSend && page.hasMore)
        assertEquals("40", page.nextBefore)
        val first = page.messages[0]
        assertEquals(listOf("https://f/1"), first.photos.map { it.url })
        assertTrue(first.replyTo!!.hasPhotos)
        assertNull(page.messages[1].text)
        assertEquals("9", repository.me()?.userId)
        assertEquals(false, repository.me()?.isClient)
    }

    @Test
    fun sendsWithItsKeyAndMarksAsRead() = runBlocking {
        respond("""{"success":true,"data":{"id":"42","sender_id":"9","message":"Voy","client_uuid":"u-2"}}""", 201)
        repository.send("15", "u-2", " Voy ", replyToId = "40", photoKeys = emptyList())
        val send = taken()
        assertEquals("/api/service-requests/15/chat/messages", send.url.encodedPath)
        assertEquals("""{"client_uuid":"u-2","message":"Voy","reply_to_id":"40"}""", send.body?.utf8())

        respond("""{"success":true,"data":{"marked":2,"message_ids":["40","41"]}}""")
        repository.markRead("15", "41")
        val read = taken()
        assertEquals("/api/service-requests/15/chat/read", read.url.encodedPath)
        assertEquals("""{"up_to_message_id":"41"}""", read.body?.utf8())
    }
}
