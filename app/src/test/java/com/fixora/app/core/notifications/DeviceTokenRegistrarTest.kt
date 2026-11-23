package com.fixora.app.core.notifications

import com.fixora.app.core.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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

class DeviceTokenRegistrarTest {

    private class FakeTokens(var token: String? = "tok-1") : PushTokenProvider {
        var deleted = false
        override suspend fun currentToken() = token
        override suspend fun deleteToken() {
            deleted = true
        }
    }

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var tokens: FakeTokens
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var scope: CoroutineScope
    private lateinit var registrar: DeviceTokenRegistrar

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<DeviceTokenApi>()
        tokens = FakeTokens()
        userId = MutableStateFlow(null)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        registrar = DeviceTokenRegistrar(api, tokens, userId, json, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun respondOk(code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json")
            .body("""{"success":true,"message":"ok"}""").build(),
    )

    private fun take(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun RecordedRequest.jsonBody(): JsonObject = json.parseToJsonElement(checkNotNull(body).utf8()).jsonObject

    @Test
    fun registersTheTokenWhenTheSessionStarts() {
        respondOk(201)
        registrar.start()
        assertEquals(0, server.requestCount)

        userId.value = "7"

        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/me/devices", request.url.encodedPath)
        assertEquals(
            JsonObject(mapOf("token" to JsonPrimitive("tok-1"), "platform" to JsonPrimitive("android"))),
            request.jsonBody(),
        )
    }

    @Test
    fun newTokenIsRegisteredOnlyWithSession() = runBlocking {
        registrar.onNewToken("tok-2")
        assertEquals(0, server.requestCount)

        respondOk(201)
        userId.value = "7"
        registrar.onNewToken("tok-2")

        assertEquals(JsonPrimitive("tok-2"), take().jsonBody()["token"])
    }

    @Test
    fun theSameTokenIsRegisteredOnlyOnce() = runBlocking {
        respondOk(201)
        registrar.start()

        // La primera vez llegan juntos el inicio de sesión y onNewToken con el mismo token.
        userId.value = "7"
        registrar.onNewToken("tok-1")
        registrar.onNewToken("tok-1")

        take()
        assertEquals(1, server.requestCount)
    }

    @Test
    fun afterLogoutTheNextLoginRegistersAgain() = runBlocking {
        respondOk(201)
        userId.value = "7"
        registrar.register()

        respondOk()
        registrar.unregister()
        respondOk(201)
        registrar.register()

        assertEquals(
            listOf("/api/me/devices", "/api/me/devices/deactivate", "/api/me/devices"),
            List(3) { take().url.encodedPath },
        )
    }

    @Test
    fun logoutDeactivatesInTheApiAndDeletesTheToken() = runBlocking {
        userId.value = "7"
        respondOk()

        registrar.unregister()

        val request = take()
        assertEquals("/api/me/devices/deactivate", request.url.encodedPath)
        assertEquals(JsonObject(mapOf("token" to JsonPrimitive("tok-1"))), request.jsonBody())
        assertTrue(tokens.deleted)
    }

    @Test
    fun logoutDeletesTheTokenEvenIfTheApiFails() = runBlocking {
        respondOk(code = 500)

        registrar.unregister()

        assertTrue(tokens.deleted)
    }

    @Test
    fun withoutFirebaseNothingIsSent() = runBlocking {
        tokens.token = null
        userId.value = "7"

        assertNull(registrar.register())
        registrar.unregister()

        assertEquals(0, server.requestCount)
    }
}
