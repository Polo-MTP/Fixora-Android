package com.fixora.app.feature.account.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.retryApiCall
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.AddressRules
import com.fixora.app.feature.account.domain.PersonalData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Datos ya descargados de un usuario. Si cambia la sesión, los de otro usuario no se muestran. */
private data class Cached<T>(val userId: String, val value: T)

/**
 * Mi cuenta contra la API (`/api/me`). Guarda en memoria lo último que respondió la API para que
 * las pantallas no esperen cada vez; después de cada cambio vuelve a pedir las direcciones, porque
 * la API decide cuál queda como principal y puede cambiar el id de una dirección editada.
 *
 * [currentUserId] es el id del usuario con sesión (null si no hay sesión).
 */
class RemoteAccountRepository(
    private val api: AccountApi,
    private val json: Json,
    private val currentUserId: Flow<String?>,
) : AccountRepository {

    private val personalCache = MutableStateFlow<Cached<PersonalData>?>(null)
    private val addressCache = MutableStateFlow<Cached<List<Address>>?>(null)
    private val personalError = MutableStateFlow<ApiError?>(null)
    private val addressError = MutableStateFlow<ApiError?>(null)

    // Si dos pantallas piden los datos a la vez, la segunda espera y usa la misma respuesta.
    private val personalLock = Mutex()
    private val addressLock = Mutex()

    override val personalData: Flow<PersonalData> = cachedFor(personalCache) { loadPersonalData(force = false) }
    override val addresses: Flow<List<Address>> = cachedFor(addressCache) { loadAddresses(force = false) }
    override val personalDataError: StateFlow<ApiError?> = personalError.asStateFlow()
    override val addressesError: StateFlow<ApiError?> = addressError.asStateFlow()

    override suspend fun refreshPersonalData() = loadPersonalData(force = true)

    override suspend fun refreshAddresses() = loadAddresses(force = true)

    override suspend fun updatePersonalData(data: PersonalData): ApiResult<Unit> {
        val userId = currentUserId.first() ?: return NoSession
        val result = safeApiCall(json) { api.updatePersonalData(data.toRequest()) }
        if (result is ApiResult.Success) {
            // La respuesta no trae la foto: se conserva la que ya estaba.
            val photoUrl = personalCache.value?.takeIf { it.userId == userId }?.value?.photoUrl
            personalCache.value = Cached(userId, result.data.data.toDomain().copy(photoUrl = photoUrl))
        }
        return result.toUnit()
    }

    override suspend fun updatePhoto(photo: ByteArray?): ApiResult<Unit> {
        val userId = currentUserId.first() ?: return NoSession
        val photoKey = photo?.let { bytes ->
            val part = MultipartBody.Part.createFormData("file", "perfil.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            when (val upload = safeApiCall(json) { api.uploadFile(PhotosCategory, part) }) {
                is ApiResult.Success -> upload.data.data.fileKey
                is ApiResult.Failure -> return upload
            }
        }
        val body = JsonObject(mapOf("photo_key" to (photoKey?.let(::JsonPrimitive) ?: JsonNull)))
        val result = safeApiCall(json) { api.updatePhoto(body) }
        if (result is ApiResult.Success) {
            personalCache.value?.takeIf { it.userId == userId }?.let { cached ->
                personalCache.value = cached.copy(value = cached.value.copy(photoUrl = result.data.data.photoUrl))
            }
        }
        return result.toUnit()
    }

    override suspend fun saveAddress(address: Address): ApiResult<Unit> {
        val userId = currentUserId.first() ?: return NoSession
        val id = address.id ?: return createAddress(userId, address)

        val current = cachedAddresses(userId)?.firstOrNull { it.id == id }
        val result = safeApiCall(json) { api.updateAddress(id, address.toUpdateRequest(current)) }
        if (result is ApiResult.Failure) return result
        val saved = (result as ApiResult.Success).data.data.toDomain()

        // La API no cambia la principal al editar: se marca aparte, ya con el id que devolvió.
        val mainResult = if (address.isMain && !saved.isMain) {
            safeApiCall(json) { api.setMainAddress(saved.id!!) }
        } else {
            ApiResult.Success(Unit)
        }
        val isMain = saved.isMain || mainResult is ApiResult.Success && address.isMain
        syncAddresses(userId) { list ->
            val replaced = list.map { if (it.id == id) saved.copy(isMain = isMain) else it }
            if (isMain) AddressRules.setMain(replaced, saved.id!!) else replaced
        }
        return mainResult
    }

    override suspend fun deleteAddress(id: String): ApiResult<Unit> {
        val userId = currentUserId.first() ?: return NoSession
        val result = safeApiCall(json) { api.deleteAddress(id) }
        if (result is ApiResult.Success) syncAddresses(userId) { AddressRules.delete(it, id) }
        return result
    }

    override suspend fun setMainAddress(id: String): ApiResult<Unit> {
        val userId = currentUserId.first() ?: return NoSession
        val result = safeApiCall(json) { api.setMainAddress(id) }
        if (result is ApiResult.Success) syncAddresses(userId) { AddressRules.setMain(it, id) }
        return result
    }

    override suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        safeApiCall(json) { api.changePassword(ChangePasswordRequest(currentPassword = current, newPassword = new)) }

    private suspend fun createAddress(userId: String, address: Address): ApiResult<Unit> {
        val result = safeApiCall(json) { api.createAddress(address.toCreateRequest()) }
        if (result is ApiResult.Success) {
            val created = result.data.data.toDomain()
            syncAddresses(userId) { AddressRules.save(it, created) }
        }
        return result.toUnit()
    }

    private suspend fun loadPersonalData(force: Boolean): ApiResult<Unit> = personalLock.withLock {
        val userId = currentUserId.first() ?: return@withLock NoSession
        if (!force && personalCache.value?.userId == userId) return@withLock ApiResult.Success(Unit)
        personalError.value = null
        when (val result = retryApiCall(json) { api.getMe() }) {
            is ApiResult.Success -> {
                val me = result.data.data
                personalCache.value = Cached(userId, me.personalData.toDomain().copy(photoUrl = me.photoUrl))
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result.also { personalError.value = it.error }
        }
    }

    private suspend fun loadAddresses(force: Boolean): ApiResult<Unit> = addressLock.withLock {
        val userId = currentUserId.first() ?: return@withLock NoSession
        if (!force && addressCache.value?.userId == userId) return@withLock ApiResult.Success(Unit)
        addressError.value = null
        when (val result = retryApiCall(json) { api.getAddresses() }) {
            is ApiResult.Success -> {
                addressCache.value = Cached(userId, result.data.data.map { it.toDomain() })
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result.also { addressError.value = it.error }
        }
    }

    /**
     * Después de un cambio que la API ya aceptó, pide la lista otra vez. Si esa consulta falla,
     * el cambio no se pierde: se aplica a la lista en memoria con las mismas reglas que la API.
     */
    private suspend fun syncAddresses(userId: String, fallback: (List<Address>) -> List<Address>) {
        val fresh = safeApiCall(json) { api.getAddresses() }
        addressCache.value = when (fresh) {
            is ApiResult.Success -> Cached(userId, fresh.data.data.map { it.toDomain() })
            is ApiResult.Failure -> Cached(userId, fallback(cachedAddresses(userId).orEmpty()))
        }
        addressError.value = null
    }

    private fun cachedAddresses(userId: String): List<Address>? =
        addressCache.value?.takeIf { it.userId == userId }?.value

    /** Emite lo guardado del usuario con sesión; si no hay nada, primero lo pide a la API. */
    private fun <T> cachedFor(cache: StateFlow<Cached<T>?>, loadIfMissing: suspend () -> Unit): Flow<T> =
        combine(currentUserId, cache) { userId, cached -> cached?.takeIf { it.userId == userId }?.value }
            .onStart { loadIfMissing() }
            .filterNotNull()
            .distinctUntilChanged()

    private companion object {
        // Sin sesión no hay a quién pedirle datos; la app ya está regresando a la landing.
        val NoSession = ApiResult.Failure(ApiError.Http(status = 401, code = null, message = null))
        val PhotosCategory = "photos".toRequestBody("text/plain".toMediaType())
    }
}

private fun <T> ApiResult<T>.toUnit(): ApiResult<Unit> = when (this) {
    is ApiResult.Success -> ApiResult.Success(Unit)
    is ApiResult.Failure -> this
}
