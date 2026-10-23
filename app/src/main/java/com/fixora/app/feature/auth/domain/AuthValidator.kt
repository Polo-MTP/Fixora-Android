package com.fixora.app.feature.auth.domain

import java.time.LocalDate
import java.time.Period

/** Errores de validación de los formularios de cuenta. La UI los traduce a texto. */
enum class FieldError {
    Required,
    EmailEmpty,
    EmailInvalid,
    PasswordWeak,
    ConfirmEmpty,
    PasswordMismatch,
    PhoneInvalid,
    BirthDateEmpty,
    Underage,
}

/** Requisitos de una contraseña nueva, para mostrarlos en vivo mientras se escribe. */
data class PasswordRules(
    val minLength: Boolean,
    val uppercase: Boolean,
    val lowercase: Boolean,
    val digit: Boolean,
) {
    val allMet get() = minLength && uppercase && lowercase && digit
}

/** Reglas de los formularios de login y registro (campos de users y personal_data). */
object AuthValidator {
    const val PASSWORD_MIN_LENGTH = 8
    const val PHONE_LENGTH = 10
    const val CODE_LENGTH = 6
    const val ADULT_AGE = 18

    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun email(value: String): FieldError? = when {
        value.isBlank() -> FieldError.EmailEmpty
        !emailPattern.matches(value.trim()) -> FieldError.EmailInvalid
        else -> null
    }

    fun required(value: String): FieldError? = if (value.isBlank()) FieldError.Required else null

    fun passwordRules(value: String) = PasswordRules(
        minLength = value.length >= PASSWORD_MIN_LENGTH,
        uppercase = value.any(Char::isUpperCase),
        lowercase = value.any(Char::isLowerCase),
        digit = value.any(Char::isDigit),
    )

    fun newPassword(value: String): FieldError? = if (passwordRules(value).allMet) null else FieldError.PasswordWeak

    fun confirmPassword(password: String, confirmation: String): FieldError? = when {
        confirmation.isEmpty() -> FieldError.ConfirmEmpty
        confirmation != password -> FieldError.PasswordMismatch
        else -> null
    }

    fun phone(value: String): FieldError? =
        if (value.length == PHONE_LENGTH && value.all { it in '0'..'9' }) null else FieldError.PhoneInvalid

    fun birthDate(date: LocalDate?, today: LocalDate): FieldError? = when {
        date == null -> FieldError.BirthDateEmpty
        Period.between(date, today).years < ADULT_AGE -> FieldError.Underage
        else -> null
    }

    fun isCodeComplete(code: String): Boolean = code.length == CODE_LENGTH && code.all { it in '0'..'9' }
}
