package com.fixora.app.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RolesTest {

    @Test
    fun client_goesToClientHome() {
        assertEquals(HomeArea.Client, Roles.homeArea("cliente"))
    }

    @Test
    fun providerManagerAndEmployee_shareProviderHome() {
        listOf("proveedor", "gerente", "empleado").forEach {
            assertEquals(it, HomeArea.Provider, Roles.homeArea(it))
        }
    }

    @Test
    fun adminAndSupport_haveNoAppHome() {
        assertNull(Roles.homeArea("administrador"))
        assertNull(Roles.homeArea("soporte"))
        assertNull(Roles.homeArea(""))
    }

    @Test
    fun ignoresCaseAndSpaces() {
        assertEquals(HomeArea.Client, Roles.homeArea(" Cliente "))
    }
}
