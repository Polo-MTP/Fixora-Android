package com.fixora.app.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

class SafeApiCallTest {

    @Serializable
    data class Greeting(val text: String)

    interface TestApi {
        @GET("greeting")
        suspend fun greeting(): Greeting
    }

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var api: TestApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .client(OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TestApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    @Test
    fun success_ignoresUnknownFields() = runBlocking {
        respond(200, """{"text":"hola","extra":1}""")
        assertEquals(ApiResult.Success(Greeting("hola")), safeApiCall(json) { api.greeting() })
    }

    @Test
    fun authError_flatFormat() = runBlocking {
        respond(401, """{"error":"INVALID_CREDENTIALS","message":"Correo o contraseña incorrectos."}""")
        assertEquals(
            ApiResult.Failure(ApiError.Http(401, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos.")),
            safeApiCall(json) { api.greeting() },
        )
    }

    @Test
    fun globalError_nestedFormat() = runBlocking {
        respond(404, """{"success":false,"error":{"code":"APP_ERROR","message":"Ruta no encontrada","statusCode":404}}""")
        assertEquals(
            ApiResult.Failure(ApiError.Http(404, "APP_ERROR", "Ruta no encontrada")),
            safeApiCall(json) { api.greeting() },
        )
    }

    @Test
    fun errorWithoutJsonBody() = runBlocking {
        respond(502, "<html>Bad Gateway</html>")
        assertEquals(ApiResult.Failure(ApiError.Http(502, null, null)), safeApiCall(json) { api.greeting() })
    }

    @Test
    fun invalidSuccessBody_isUnknown() = runBlocking {
        respond(200, """{"otro":"campo"}""")
        assertEquals(ApiResult.Failure(ApiError.Unknown), safeApiCall(json) { api.greeting() })
    }

    @Test
    fun slowServer_isTimeout() = runBlocking {
        server.enqueue(
            MockResponse.Builder().body("""{"text":"tarde"}""").headersDelay(2, TimeUnit.SECONDS).build(),
        )
        assertEquals(ApiResult.Failure(ApiError.Timeout), safeApiCall(json) { api.greeting() })
    }

    @Test
    fun serverDown_isNoConnection() = runBlocking {
        server.close()
        assertEquals(ApiResult.Failure(ApiError.NoConnection), safeApiCall(json) { api.greeting() })
    }
}
