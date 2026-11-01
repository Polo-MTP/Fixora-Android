package com.fixora.app.feature.account.domain

import com.fixora.app.feature.auth.domain.FieldError
import java.time.LocalDate

/** Datos personales del usuario (tabla personal_data). */
data class PersonalData(
    val name: String,
    val lastNameP: String,
    val lastNameM: String,
    val birthDate: LocalDate?,
    val phone: String,
) {
    val fullName get() = listOf(name, lastNameP, lastNameM).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Dirección guardada del usuario (tablas addresses y address_user). [id] es null mientras no se
 * ha guardado. El país es siempre México, por eso no se pide.
 */
data class Address(
    val id: String?,
    val alias: String,
    val street: String,
    val numExt: String,
    val numInt: String,
    val colony: String,
    val postalCode: String,
    val municipality: String,
    val state: String,
    val reference: String,
    val isMain: Boolean,
) {
    /** «Av. Insurgentes Sur 1234, int. 5» */
    val streetLine get() = buildString {
        append("$street $numExt")
        if (numInt.isNotBlank()) append(", int. $numInt")
    }

    /** «Del Valle, Benito Juárez, C.P. 03100» */
    val areaLine get() = "$colony, $municipality, C.P. $postalCode"
}

/** Reglas de la libreta de direcciones: siempre hay exactamente una principal si hay alguna. */
object AddressRules {

    /** Agrega o reemplaza [address] (que ya trae id). Si queda como principal, las demás dejan de serlo. */
    fun save(addresses: List<Address>, address: Address): List<Address> {
        val others = addresses.filterNot { it.id == address.id }
        // La primera dirección siempre es la principal, y nunca se queda la lista sin una.
        val saved = address.copy(isMain = address.isMain || others.none { it.isMain })
        val updated = if (addresses.any { it.id == address.id }) {
            addresses.map { if (it.id == address.id) saved else it }
        } else {
            addresses + saved
        }
        return if (saved.isMain) updated.map { it.copy(isMain = it.id == saved.id) } else updated
    }

    /** Quita la dirección; si era la principal, la primera que queda pasa a serlo. */
    fun delete(addresses: List<Address>, id: String): List<Address> {
        val remaining = addresses.filterNot { it.id == id }
        if (remaining.isEmpty() || remaining.any { it.isMain }) return remaining
        return listOf(remaining.first().copy(isMain = true)) + remaining.drop(1)
    }

    fun setMain(addresses: List<Address>, id: String): List<Address> =
        addresses.map { it.copy(isMain = it.id == id) }
}

/** Validaciones propias de Mi cuenta; las de nombre, teléfono y contraseña están en AuthValidator. */
object AccountValidator {
    const val POSTAL_CODE_LENGTH = 5

    fun postalCode(value: String): FieldError? =
        if (value.length == POSTAL_CODE_LENGTH && value.all { it in '0'..'9' }) null else FieldError.PostalCodeInvalid

    fun passwordChanged(current: String, new: String): FieldError? =
        if (new.isNotEmpty() && new == current) FieldError.PasswordUnchanged else null
}
