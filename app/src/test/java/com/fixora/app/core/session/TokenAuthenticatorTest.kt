package com.fixora.app.core.session

import com.fixora.app.core.network.NetworkModule
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

class TokenAuthenticatorTest {

    private class FakeTokens(var session: Session?) : SessionTokens {
        override suspend fun current() = session
        override suspend fun updateTokens(accessToken: String, refreshToken: String) {
            session = session?.copy(accessToken = accessToken, refreshToken = refreshToken)
        }
        override suspend fun clear() {
            session = null
        }
    }

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var tokens: FakeTokens
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = FakeTokens(Session("1", "ana@correo.com", "cliente", "activo", "access-viejo", "refresh-viejo"))
        val sessionApi = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<SessionApi>()
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens, sessionApi, json))
            .build()
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String = "{}") = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private fun callProtected() = client.newCall(Request.Builder().url(server.url("/api/protegido")).build()).execute()

    @Test
    fun expiredToken_isRefreshedAndRequestRetried() {
        respond(401, """{"error":"TOKEN_EXPIRED"}""")
        respond(200, """{"message":"ok","tokens":{"accessToken":"access-nuevo","refreshToken":"refresh-nuevo","tokenType":"Bearer","expiresIn":"15m"}}""")
        respond(200, """{"ok":true}""")

        val response = callProtected()

        assertEquals(200, response.code)
        assertEquals("Bearer access-viejo", server.takeRequest().headers["Authorization"])
        val refresh = server.takeRequest()
        assertEquals("/api/auth/refresh", refresh.url.encodedPath)
        assertEquals("""{"refreshToken":"refresh-viejo"}""", refresh.body?.utf8())
        assertEquals("Bearer access-nuevo", server.takeRequest().headers["Authorization"])
        assertEquals("refresh-nuevo", tokens.session?.refreshToken)
    }

    @Test
    fun rejectedRefresh_clearsSession() {
        respond(401)
        respond(401, """{"error":"INVALID_SESSION","message":"La sesión no es válida o ha expirado."}""")

        assertEquals(401, callProtected().code)
        assertNull(tokens.session)
    }

    @Test
    fun serverErrorOnRefresh_keepsSession() {
        respond(401)
        respond(500)

        assertEquals(401, callProtected().code)
        assertEquals("refresh-viejo", tokens.session?.refreshToken)
    }

    @Test
    fun withoutSession_noTokenAndNoRefresh() {
        tokens.session = null
        respond(401)

        assertEquals(401, callProtected().code)
        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals(1, server.requestCount)
    }
}
