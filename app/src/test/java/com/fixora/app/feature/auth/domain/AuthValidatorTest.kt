package com.fixora.app.feature.auth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AuthValidatorTest {

    @Test
    fun email() {
        assertEquals(FieldError.EmailEmpty, AuthValidator.email("   "))
        assertEquals(FieldError.EmailInvalid, AuthValidator.email("ana@"))
        assertEquals(FieldError.EmailInvalid, AuthValidator.email("ana@correo.c"))
        assertEquals(FieldError.EmailInvalid, AuthValidator.email("ana maria@correo.com"))
        assertNull(AuthValidator.email("ana@correo.com"))
        assertNull(AuthValidator.email("  ana.maria+fixora@uni.edu.mx  "))
    }

    @Test
    fun passwordRules() {
        val weak = AuthValidator.passwordRules("hola12")
        assertFalse(weak.minLength)
        assertFalse(weak.uppercase)
        assertTrue(weak.lowercase)
        assertTrue(weak.digit)
        assertEquals(FieldError.PasswordWeak, AuthValidator.newPassword("hola12"))

        assertTrue(AuthValidator.passwordRules("Ñandú2026").allMet)
        assertNull(AuthValidator.newPassword("Fixora2026"))
    }

    @Test
    fun confirmPassword() {
        assertEquals(FieldError.ConfirmEmpty, AuthValidator.confirmPassword("Fixora2026", ""))
        assertEquals(FieldError.PasswordMismatch, AuthValidator.confirmPassword("Fixora2026", "fixora2026"))
        assertNull(AuthValidator.confirmPassword("Fixora2026", "Fixora2026"))
    }

    @Test
    fun phone() {
        assertNull(AuthValidator.phone("8711234567"))
        assertEquals(FieldError.PhoneInvalid, AuthValidator.phone("871123456"))
        assertEquals(FieldError.PhoneInvalid, AuthValidator.phone("87112345678"))
        assertEquals(FieldError.PhoneInvalid, AuthValidator.phone("87112-4567"))
    }

    @Test
    fun birthDate_requiresAdult() {
        val today = LocalDate.of(2026, 10, 22)
        assertEquals(FieldError.BirthDateEmpty, AuthValidator.birthDate(null, today))
        // Cumple 18 mañana: todavía no es mayor de edad.
        assertEquals(FieldError.Underage, AuthValidator.birthDate(LocalDate.of(2008, 10, 23), today))
        // Cumple 18 hoy.
        assertNull(AuthValidator.birthDate(LocalDate.of(2008, 10, 22), today))
    }

    @Test
    fun verificationCode() {
        assertTrue(AuthValidator.isCodeComplete("123456"))
        assertFalse(AuthValidator.isCodeComplete("12345"))
        assertFalse(AuthValidator.isCodeComplete("12a456"))
    }
}
