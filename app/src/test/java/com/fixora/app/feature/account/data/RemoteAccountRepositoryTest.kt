package com.fixora.app.feature.account.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.NetworkModule
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.PersonalData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class RemoteAccountRepositoryTest {

    private val json = NetworkModule.provideJson()
    private lateinit var server: MockWebServer
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var repository: RemoteAccountRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<AccountApi>()
        userId = MutableStateFlow("1")
        repository = RemoteAccountRepository(api, json, userId)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    private fun data(payload: String) = """{"success":true,"message":"ok","data":$payload}"""

    private fun address(id: String, alias: String, isMain: Boolean, street: String = "Morelos") =
        """{"id":"$id","alias":"$alias","is_main":$isMain,"reference":null,"street":"$street","num_ext":"10",
           |"num_int":null,"colony":"Centro","municipality":"Torreón","state":"Coahuila","postal_code":"27000",
           |"country":"México","latitude":null,"longitude":null}""".trimMargin()

    private fun take(): RecordedRequest = server.takeRequest(2, TimeUnit.SECONDS)!!

    private fun RecordedRequest.jsonBody(): JsonObject = json.parseToJsonElement(body!!.utf8()).jsonObject

    private fun JsonObject.text(key: String) = (this[key] as JsonPrimitive).content

    @Test
    fun personalData_loadsFromApiAndTurnsNullsIntoEmptyText() = runBlocking {
        respond(
            data(
                """{"user":{"id":"1","email":"ana@correo.com","role":"cliente","state":"activo"},
                   |"personal_data":{"name":"Ana","lastname_p":"Pérez","lastname_m":null,
                   |"birth_date":"1995-04-12","phone":null}}""".trimMargin(),
            ),
        )

        val data = repository.personalData.first()

        assertEquals(PersonalData("Ana", "Pérez", "", LocalDate.of(1995, 4, 12), ""), data)
        assertEquals("/api/me", take().url.encodedPath)
    }

    @Test
    fun updatePersonalData_sendsEmptyTextToClearOptionalFields() = runBlocking {
        respond(data("""{"name":"Ana","lastname_p":"Pérez","lastname_m":null,"birth_date":null,"phone":null}"""))

        val result = repository.updatePersonalData(PersonalData(" Ana ", "Pérez", "", null, ""))

        assertTrue(result is ApiResult.Success)
        val request = take()
        assertEquals("PATCH", request.method)
        val body = request.jsonBody()
        assertEquals("Ana", body.text("name"))
        // Con "" la API borra el campo; si no se manda, lo deja como estaba.
        assertEquals("", body.text("lastname_m"))
        assertEquals("", body.text("birth_date"))
        assertEquals("", body.text("phone"))
        assertEquals("Ana", repository.personalData.first().name)
    }

    @Test
    fun addresses_failedLoadShowsErrorAndRetryEmitsTheList() = runBlocking {
        respond("""{"error":"INTERNAL_SERVER_ERROR","message":"Error interno del servidor."}""", code = 500)

        // La primera carga falla: la lista no llega y queda el error para mostrarlo.
        assertNull(withTimeoutOrNull(1_000) { repository.addresses.first() })
        assertEquals(500, (repository.addressesError.value as ApiError.Http).status)

        respond(data("[${address("7", "Casa", isMain = true)}]"))
        repository.refreshAddresses()

        assertEquals(listOf("Casa"), repository.addresses.first().map { it.alias })
        assertNull(repository.addressesError.value)
    }

    @Test
    fun createAddress_sendsIsMainAndReloadsTheList() = runBlocking {
        respond(data("[]"))
        repository.addresses.first()
        take()

        respond(data(address("3", "Casa", isMain = true)), code = 201)
        respond(data("[${address("3", "Casa", isMain = true)}]"))
        val result = repository.saveAddress(sampleAddress(id = null, alias = "Casa", isMain = true))

        assertTrue(result is ApiResult.Success)
        val create = take()
        assertEquals("POST", create.method)
        assertEquals("true", create.jsonBody().text("is_main"))
        assertEquals("/api/me/addresses", take().url.encodedPath)
        assertEquals(listOf("3"), repository.addresses.first().map { it.id })
    }

    @Test
    fun editAddress_onlyAliasChanged_sendsOnlyTheAlias() = runBlocking {
        respond(data("[${address("5", "Casa", isMain = true)}]"))
        repository.addresses.first()
        take()

        respond(data(address("5", "Mi casa", isMain = true)))
        respond(data("[${address("5", "Mi casa", isMain = true)}]"))
        repository.saveAddress(repository.addresses.first().single().copy(alias = "Mi casa"))

        val body = take().jsonBody()
        // Sin datos de ubicación, la API no crea otra fila y la dirección conserva su id.
        assertEquals(setOf("alias"), body.keys)
        assertEquals("Mi casa", repository.addresses.first().single().alias)
    }

    @Test
    fun editAddress_markedAsMain_usesTheNewIdThatTheApiReturns() = runBlocking {
        respond(data("[${address("1", "Casa", isMain = true)},${address("2", "Trabajo", isMain = false)}]"))
        repository.addresses.first()
        take()

        // Cambió la calle: la API crea otra fila con id 9 y conserva is_main = false.
        respond(data(address("9", "Trabajo", isMain = false, street = "Juárez")))
        respond("""{"success":true,"message":"ok","data":{"id":"9","is_main":true}}""")
        respond(data("[${address("9", "Trabajo", isMain = true, street = "Juárez")},${address("1", "Casa", isMain = false)}]"))
        val edited = repository.addresses.first().first { it.id == "2" }.copy(street = "Juárez", isMain = true)
        val result = repository.saveAddress(edited)

        assertTrue(result is ApiResult.Success)
        assertEquals("/api/me/addresses/2", take().url.encodedPath)
        val setMain = take()
        assertEquals("PATCH", setMain.method)
        assertEquals("/api/me/addresses/9/main", setMain.url.encodedPath)
        assertEquals("9", repository.addresses.first().single { it.isMain }.id)
    }

    @Test
    fun deleteMainAddress_whenReloadFails_promotesTheNextOneLocally() = runBlocking {
        respond(data("[${address("1", "Casa", isMain = true)},${address("2", "Trabajo", isMain = false)}]"))
        repository.addresses.first()
        take()

        respond("""{"success":true,"message":"ok","data":{"address_id":"1","active":false,"was_main":true}}""")
        respond("""{"error":"INTERNAL_SERVER_ERROR","message":"Error interno del servidor."}""", code = 500)
        val result = repository.deleteAddress("1")

        // El borrado sí se hizo; la lista se ajusta con las mismas reglas que la API.
        assertTrue(result is ApiResult.Success)
        assertEquals("DELETE", take().method)
        val remaining = repository.addresses.first()
        assertEquals(listOf("2"), remaining.map { it.id })
        assertTrue(remaining.single().isMain)
    }

    @Test
    fun anotherUser_neverSeesTheDataOfThePreviousOne() = runBlocking {
        respond(data("[${address("1", "Casa de Ana", isMain = true)}]"))
        assertEquals("Casa de Ana", repository.addresses.first().single().alias)
        take()

        userId.value = "2"
        respond(data("[${address("8", "Casa de Luis", isMain = true)}]"))

        assertEquals("Casa de Luis", repository.addresses.first().single().alias)
        assertEquals("/api/me/addresses", take().url.encodedPath)
    }

    @Test
    fun changePassword_sendsCurrentAndNewPassword() = runBlocking {
        respond(data("""{"password_changed":true,"other_sessions_revoked":true}"""))

        val result = repository.changePassword("Actual123!", "Nueva123!")

        assertTrue(result is ApiResult.Success)
        val request = take()
        assertEquals("/api/me/password", request.url.encodedPath)
        assertEquals("Actual123!", request.jsonBody().text("current_password"))
        assertEquals("Nueva123!", request.jsonBody().text("new_password"))
    }

    @Test
    fun changePassword_wrongCurrent_returnsTheApiMessage() = runBlocking {
        respond(
            """{"error":"ACCOUNT_INCORRECT_CURRENT_PASSWORD","message":"La contraseña actual es incorrecta."}""",
            code = 400,
        )

        val error = (repository.changePassword("mala", "Nueva123!") as ApiResult.Failure).error as ApiError.Http

        assertEquals("ACCOUNT_INCORRECT_CURRENT_PASSWORD", error.code)
        assertEquals("La contraseña actual es incorrecta.", error.message)
    }

    private fun sampleAddress(id: String?, alias: String, isMain: Boolean) = Address(
        id = id,
        alias = alias,
        street = "Morelos",
        numExt = "10",
        numInt = "",
        colony = "Centro",
        postalCode = "27000",
        municipality = "Torreón",
        state = "Coahuila",
        reference = "",
        isMain = isMain,
    )
}
