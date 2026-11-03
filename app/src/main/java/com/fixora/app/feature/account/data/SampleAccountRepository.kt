package com.fixora.app.feature.account.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.AddressRules
import com.fixora.app.feature.account.domain.PersonalData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

// Pausa breve para que se vea el indicador de carga, como si fuera a la API.
private const val SAMPLE_LATENCY_MS = 600L

/**
 * Datos de ejemplo en memoria mientras la API no tenga los endpoints de Mi cuenta (API-10).
 * Los cambios duran mientras la app está abierta; al reiniciarla vuelven los de ejemplo.
 */
@Singleton
class SampleAccountRepository @Inject constructor() : AccountRepository {

    private val personal = MutableStateFlow(SamplePersonalData)
    private val addressList = MutableStateFlow(SampleAddresses)
    private val nextId = AtomicInteger(SampleAddresses.size + 1)

    override val personalData: Flow<PersonalData> = personal
    override val addresses: Flow<List<Address>> = addressList

    override suspend fun updatePersonalData(data: PersonalData) = respond { personal.value = data }

    override suspend fun saveAddress(address: Address) = respond {
        val withId = address.copy(id = address.id ?: nextId.getAndIncrement().toString())
        addressList.update { AddressRules.save(it, withId) }
    }

    override suspend fun deleteAddress(id: String) = respond { addressList.update { AddressRules.delete(it, id) } }

    override suspend fun setMainAddress(id: String) = respond { addressList.update { AddressRules.setMain(it, id) } }

    override suspend fun changePassword(current: String, new: String) = respond {}

    private suspend fun respond(change: () -> Unit): ApiResult<Unit> {
        delay(SAMPLE_LATENCY_MS)
        change()
        return ApiResult.Success(Unit)
    }
}

private val SamplePersonalData = PersonalData(
    name = "Ana María",
    lastNameP = "Pérez",
    lastNameM = "López",
    birthDate = LocalDate.of(1995, 4, 12),
    phone = "5512345678",
)

private val SampleAddresses = listOf(
    Address(
        id = "1",
        alias = "Casa",
        street = "Av. Insurgentes Sur",
        numExt = "1234",
        numInt = "5",
        colony = "Del Valle",
        postalCode = "03100",
        municipality = "Benito Juárez",
        state = "Ciudad de México",
        reference = "Edificio gris, junto a la farmacia",
        isMain = true,
    ),
    Address(
        id = "2",
        alias = "Trabajo",
        street = "Paseo de la Reforma",
        numExt = "250",
        numInt = "",
        colony = "Juárez",
        postalCode = "06600",
        municipality = "Cuauhtémoc",
        state = "Ciudad de México",
        reference = "",
        isMain = false,
    ),
)
