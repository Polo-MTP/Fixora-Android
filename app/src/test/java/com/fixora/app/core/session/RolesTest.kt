package com.fixora.app.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun providerUnderReview_cannotUseTheApp() {
        assertTrue(Roles.isInReview("proveedor", "por_aprobar"))
        assertTrue(Roles.isInReview("gerente", "por_verificar"))
        assertFalse(Roles.isInReview("proveedor", "activo"))
        assertFalse(Roles.isInReview("empleado", " Activo "))
    }

    @Test
    fun clientStatusIsNotAProviderReview() {
        // El cliente sin verificar lo detiene la API al iniciar sesión, no esta regla.
        assertFalse(Roles.isInReview("cliente", "por_verificar"))
        assertFalse(Roles.isInReview("administrador", "por_aprobar"))
    }
}
