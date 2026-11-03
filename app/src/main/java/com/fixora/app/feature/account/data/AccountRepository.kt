package com.fixora.app.feature.account.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.PersonalData
import kotlinx.coroutines.flow.Flow

/**
 * Datos de Mi cuenta (ANDROID-5). Las pantallas solo conocen esta interfaz: cuando la API tenga
 * los endpoints (API-10) se agrega la implementación real y se cambia el binding en AccountModule.
 */
interface AccountRepository {
    val personalData: Flow<PersonalData>
    val addresses: Flow<List<Address>>

    suspend fun updatePersonalData(data: PersonalData): ApiResult<Unit>

    /** Crea la dirección si [Address.id] es null; si no, la reemplaza. */
    suspend fun saveAddress(address: Address): ApiResult<Unit>
    suspend fun deleteAddress(id: String): ApiResult<Unit>
    suspend fun setMainAddress(id: String): ApiResult<Unit>
    suspend fun changePassword(current: String, new: String): ApiResult<Unit>
}
