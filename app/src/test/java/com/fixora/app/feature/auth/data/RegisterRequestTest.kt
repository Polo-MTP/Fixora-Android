package com.fixora.app.feature.auth.data

import com.fixora.app.core.network.NetworkModule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RegisterRequestTest {

    private val json = NetworkModule.provideJson()

    private fun encode(request: RegisterRequest): JsonObject = json.encodeToJsonElement(request).jsonObject

    private val request = RegisterRequest(
        email = "ana@correo.com",
        password = "Prueba123",
        name = "Ana",
        lastNameP = "Pérez",
        lastNameM = "López",
        birthDate = "2000-05-10",
        phone = "5512345678",
    )

    @Test
    fun usesTheFieldNamesOfTheApi() {
        assertEquals(
            setOf("email", "password", "name", "lastname_p", "lastname_m", "birth_date", "phone"),
            encode(request).keys,
        )
        assertEquals(JsonPrimitive("2000-05-10"), encode(request)["birth_date"])
    }

    @Test
    fun omitsMissingMaternalLastName() {
        assertFalse("lastname_m" in encode(request.copy(lastNameM = null)))
    }
}
