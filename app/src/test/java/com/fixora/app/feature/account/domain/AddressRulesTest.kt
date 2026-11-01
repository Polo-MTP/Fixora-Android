package com.fixora.app.feature.account.domain

import com.fixora.app.feature.auth.domain.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressRulesTest {

    private fun address(id: String, isMain: Boolean = false) =
        Address(id, "Alias $id", "Calle", "1", "", "Colonia", "01000", "Municipio", "Estado", "", isMain)

    private fun List<Address>.mainIds() = filter { it.isMain }.map { it.id }

    @Test
    fun firstAddress_becomesMain() {
        val result = AddressRules.save(emptyList(), address("1", isMain = false))
        assertEquals(listOf("1"), result.mainIds())
    }

    @Test
    fun savingNewMain_unmarksThePrevious() {
        val result = AddressRules.save(listOf(address("1", isMain = true)), address("2", isMain = true))
        assertEquals(listOf("2"), result.mainIds())
        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun editingTheMain_cannotLeaveListWithoutMain() {
        val list = listOf(address("1", isMain = true), address("2"))
        val result = AddressRules.save(list, address("1", isMain = false).copy(alias = "Casa"))
        assertEquals(listOf("1"), result.mainIds())
        assertEquals("Casa", result.first().alias)
    }

    @Test
    fun deletingTheMain_promotesTheFirstRemaining() {
        val list = listOf(address("1", isMain = true), address("2"), address("3"))
        val result = AddressRules.delete(list, "1")
        assertEquals(listOf("2"), result.mainIds())
        assertEquals(2, result.size)
    }

    @Test
    fun setMain_leavesExactlyOne() {
        val list = listOf(address("1", isMain = true), address("2"), address("3"))
        assertEquals(listOf("3"), AddressRules.setMain(list, "3").mainIds())
    }

    @Test
    fun postalCode_requiresFiveDigits() {
        assertNull(AccountValidator.postalCode("03100"))
        assertEquals(FieldError.PostalCodeInvalid, AccountValidator.postalCode("0310"))
        assertEquals(FieldError.PostalCodeInvalid, AccountValidator.postalCode("03a00"))
    }

    @Test
    fun newPassword_mustDifferFromCurrent() {
        assertEquals(FieldError.PasswordUnchanged, AccountValidator.passwordChanged("Prueba123", "Prueba123"))
        assertNull(AccountValidator.passwordChanged("Prueba123", "Nueva1234"))
    }
}
