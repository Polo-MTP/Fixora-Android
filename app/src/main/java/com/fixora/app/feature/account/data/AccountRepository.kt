package com.fixora.app.feature.account.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.PersonalData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Datos de Mi cuenta (ANDROID-5). [personalData] y [addresses] emiten en cuanto hay datos del
 * usuario con sesión: la primera vez los pide a la API y después se actualizan con cada cambio.
 */
interface AccountRepository {
    val personalData: Flow<PersonalData>
    val addresses: Flow<List<Address>>

    /**
     * Si hay un servicio en curso (API-55); las pantallas que editan el nombre, la foto o el
     * vehículo lo usan para bloquearlos. Se actualiza con [refreshPersonalData].
     */
    val activeService: Flow<Boolean> get() = personalData.map { it.activeService }.distinctUntilChanged()

    /** Error de la última carga de datos personales; null si cargó bien o está cargando. */
    val personalDataError: Flow<ApiError?>

    /** Error de la última carga de direcciones; null si cargó bien o está cargando. */
    val addressesError: Flow<ApiError?>

    /** Vuelve a pedir los datos personales a la API (al abrir Mi cuenta o al reintentar). */
    suspend fun refreshPersonalData(): ApiResult<Unit>

    /** Vuelve a pedir las direcciones a la API (al abrir Mi cuenta o al reintentar). */
    suspend fun refreshAddresses(): ApiResult<Unit>

    suspend fun updatePersonalData(data: PersonalData): ApiResult<Unit>

    /** Crea la dirección si [Address.id] es null; si no, la reemplaza. */
    suspend fun saveAddress(address: Address): ApiResult<Unit>
    suspend fun deleteAddress(id: String): ApiResult<Unit>
    suspend fun setMainAddress(id: String): ApiResult<Unit>
    suspend fun changePassword(current: String, new: String): ApiResult<Unit>

    /** Pone la foto de perfil ([photo] en JPEG) o la quita si es null. */
    suspend fun updatePhoto(photo: ByteArray?): ApiResult<Unit>
}
