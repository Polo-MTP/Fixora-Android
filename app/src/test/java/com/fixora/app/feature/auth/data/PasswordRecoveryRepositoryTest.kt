package com.fixora.app.feature.auth.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
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
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

class PasswordRecoveryRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var repository: PasswordRecoveryRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<AuthApi>()
        repository = PasswordRecoveryRepository(api, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private fun takeRequest(): RecordedRequest = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))

    private fun RecordedRequest.jsonBody(): JsonObject = json.parseToJsonElement(checkNotNull(body).utf8()).jsonObject

    private val ok = """{"success":true,"message":"ok"}"""

    @Test
    fun requestCodeSendsTheNormalizedEmail() = runBlocking {
        respond(ok)

        assertEquals(ApiResult.Success(Unit), repository.requestCode("  Ana@Correo.com "))

        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/forgot-password", request.url.encodedPath)
        assertEquals(JsonObject(mapOf("email" to JsonPrimitive("ana@correo.com"))), request.jsonBody())
    }

    @Test
    fun resetPasswordUsesTheFieldNamesOfTheApi() = runBlocking {
        respond(ok)

        assertEquals(ApiResult.Success(Unit), repository.resetPassword("Ana@Correo.com", "123456", "Nueva123"))

        val request = takeRequest()
        assertEquals("/api/auth/reset-password", request.url.encodedPath)
        assertEquals(
            JsonObject(
                mapOf(
                    "email" to JsonPrimitive("ana@correo.com"),
                    "code" to JsonPrimitive("123456"),
                    "new_password" to JsonPrimitive("Nueva123"),
                ),
            ),
            request.jsonBody(),
        )
    }

    @Test
    fun wrongCodeKeepsTheCodeAndMessageOfTheApi() = runBlocking {
        respond("""{"error":"INVALID_RECOVERY_CODE","message":"El código de recuperación es incorrecto."}""", code = 400)

        assertEquals(
            ApiResult.Failure(ApiError.Http(400, "INVALID_RECOVERY_CODE", "El código de recuperación es incorrecto.")),
            repository.resetPassword("ana@correo.com", "000000", "Nueva123"),
        )
    }

    @Test
    fun emailFailureKeepsItsCode() = runBlocking {
        respond(
            """{"error":"RECOVERY_EMAIL_FAILED","message":"No fue posible enviar el correo de recuperación."}""",
            code = 503,
        )

        val result = repository.requestCode("ana@correo.com")

        assertEquals(PasswordRecoveryRepository.RECOVERY_EMAIL_FAILED, ((result as ApiResult.Failure).error as ApiError.Http).code)
    }
}
