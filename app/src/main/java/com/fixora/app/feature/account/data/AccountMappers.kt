package com.fixora.app.feature.account.data

import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.PersonalData
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Conversión entre lo que manda la API y los modelos de la app. */

internal fun PersonalDataDto?.toDomain() = PersonalData(
    name = this?.name.orEmpty(),
    lastNameP = this?.lastNameP.orEmpty(),
    lastNameM = this?.lastNameM.orEmpty(),
    birthDate = this?.birthDate?.let(::parseDate),
    phone = this?.phone.orEmpty(),
)

internal fun PersonalData.toRequest() = PersonalDataRequest(
    name = name.trim(),
    lastNameP = lastNameP.trim(),
    lastNameM = lastNameM.trim(),
    // LocalDate.toString() ya es yyyy-MM-dd.
    birthDate = birthDate?.toString().orEmpty(),
    phone = phone.trim(),
)

internal fun AddressDto.toDomain() = Address(
    id = id,
    alias = alias.orEmpty(),
    street = street,
    numExt = numExt,
    numInt = numInt.orEmpty(),
    colony = colony,
    postalCode = postalCode,
    municipality = municipality,
    state = state,
    reference = reference.orEmpty(),
    isMain = isMain,
)

internal fun Address.toCreateRequest() = CreateAddressRequest(
    alias = alias.trim(),
    street = street.trim(),
    numExt = numExt.trim(),
    numInt = numInt.trim(),
    colony = colony.trim(),
    municipality = municipality.trim(),
    state = state.trim(),
    postalCode = postalCode.trim(),
    reference = reference.trim(),
    isMain = isMain,
)

/**
 * Cuerpo para editar [this] partiendo de [current]. Si solo cambió el alias, los datos de la
 * dirección no se mandan, así la API no crea otra fila y la dirección conserva su id.
 */
internal fun Address.toUpdateRequest(current: Address?): UpdateAddressRequest {
    val alias = alias.trim()
    if (current != null && sameLocation(current)) return UpdateAddressRequest(alias = alias)
    return UpdateAddressRequest(
        alias = alias,
        street = street.trim(),
        numExt = numExt.trim(),
        numInt = numInt.trim(),
        colony = colony.trim(),
        municipality = municipality.trim(),
        state = state.trim(),
        postalCode = postalCode.trim(),
        reference = reference.trim(),
    )
}

private fun Address.sameLocation(other: Address) =
    street.trim() == other.street && numExt.trim() == other.numExt && numInt.trim() == other.numInt &&
        colony.trim() == other.colony && municipality.trim() == other.municipality &&
        state.trim() == other.state && postalCode.trim() == other.postalCode && reference.trim() == other.reference

// Una fecha con otro formato no debe tumbar la pantalla: se muestra vacía y el usuario la elige.
private fun parseDate(value: String): LocalDate? =
    try {
        LocalDate.parse(value.take(10))
    } catch (e: DateTimeParseException) {
        null
    }
