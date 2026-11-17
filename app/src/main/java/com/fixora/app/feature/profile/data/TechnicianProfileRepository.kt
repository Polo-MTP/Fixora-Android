package com.fixora.app.feature.profile.data

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.retryApiCall
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.profile.domain.TechnicianProfile
import com.fixora.app.feature.profile.domain.TechnicianProfileRules
import com.fixora.app.feature.profile.domain.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Estado del perfil de técnico del usuario con sesión. */
sealed interface ProfileState {
    data object Loading : ProfileState
    data class Loaded(val profile: TechnicianProfile) : ProfileState

    /** La cuenta no atiende servicios (por ejemplo, el encargado de una empresa): no hay perfil. */
    data object NotTechnician : ProfileState
    data class Failed(val error: ApiError) : ProfileState
}

private data class Cached(val userId: String, val state: ProfileState)

/**
 * Foto, biografía y vehículo del técnico (ANDROID-7). Guarda en memoria lo último que respondió la
 * API; si cambia la sesión, el perfil de otro usuario no se muestra.
 *
 * [currentUserId] es el id del usuario con sesión (null si no hay sesión).
 */
class TechnicianProfileRepository(
    private val api: TechnicianProfileApi,
    private val json: Json,
    private val currentUserId: Flow<String?>,
) {
    private val cache = MutableStateFlow<Cached?>(null)

    val state: Flow<ProfileState> =
        combine(currentUserId, cache) { userId, cached ->
            cached?.takeIf { it.userId == userId }?.state ?: ProfileState.Loading
        }.distinctUntilChanged()

    /** Pide el perfil a la API. Si ya había uno cargado y la consulta falla, se conserva. */
    suspend fun refresh() {
        val userId = currentUserId.first() ?: return
        when (val result = retryApiCall(json) { api.getMyProfile() }) {
            is ApiResult.Success -> set(userId, ProfileState.Loaded(result.data.data.toDomain()))
            is ApiResult.Failure -> {
                val error = result.error
                if (error is ApiError.Http && error.status == 404) {
                    set(userId, ProfileState.NotTechnician)
                } else if (loaded(userId) == null) {
                    set(userId, ProfileState.Failed(error))
                }
            }
        }
    }

    /**
     * Guarda la biografía y, si hay [photo] (JPEG), primero la sube y luego la pone en el perfil.
     * Si falla la subida, no se cambia nada.
     */
    suspend fun updateProfile(bio: String, photo: ByteArray?): ApiResult<Unit> {
        val (userId, profile) = currentProfile() ?: return NotTechnicianError
        val photoKey = photo?.let { bytes ->
            val upload = safeApiCall(json) { api.uploadFile(PhotosCategory, bytes.toPhotoPart()) }
            if (upload is ApiResult.Failure) return upload
            (upload as ApiResult.Success).data.data.fileKey
        }
        val result = safeApiCall(json) {
            api.updateProfile(profile.employeeId, ProfileRequest(bio = bio.trim(), photoKey = photoKey))
        }
        if (result is ApiResult.Failure) return result
        val saved = (result as ApiResult.Success).data.data
        set(userId, ProfileState.Loaded(profile.copy(photoUrl = saved.photoUrl, bio = saved.bio.orEmpty())))
        return ApiResult.Success(Unit)
    }

    suspend fun saveVehicle(vehicle: Vehicle): ApiResult<Unit> {
        val (userId, profile) = currentProfile() ?: return NotTechnicianError
        val request = VehicleRequest(
            plate = TechnicianProfileRules.normalizePlate(vehicle.plate),
            brand = vehicle.brand.trim(),
            model = vehicle.model.trim(),
            color = vehicle.color.trim(),
        )
        val result = safeApiCall(json) { api.saveVehicle(profile.employeeId, request) }
        if (result is ApiResult.Failure) return result
        val saved = (result as ApiResult.Success).data.data.toDomain()
        set(userId, ProfileState.Loaded(profile.copy(vehicle = saved)))
        return ApiResult.Success(Unit)
    }

    suspend fun deleteVehicle(): ApiResult<Unit> {
        val (userId, profile) = currentProfile() ?: return NotTechnicianError
        val result = safeApiCall(json) { api.deleteVehicle(profile.employeeId) }
        if (result is ApiResult.Success) set(userId, ProfileState.Loaded(profile.copy(vehicle = null)))
        return result
    }

    /** El perfil cargado del usuario con sesión; si aún no se cargó, lo pide primero. */
    private suspend fun currentProfile(): Pair<String, TechnicianProfile>? {
        val userId = currentUserId.first() ?: return null
        loaded(userId)?.let { return userId to it }
        refresh()
        return loaded(userId)?.let { userId to it }
    }

    private fun loaded(userId: String): TechnicianProfile? =
        (cache.value?.takeIf { it.userId == userId }?.state as? ProfileState.Loaded)?.profile

    private fun set(userId: String, state: ProfileState) {
        cache.value = Cached(userId, state)
    }

    private companion object {
        val PhotosCategory = "photos".toRequestBody("text/plain".toMediaType())

        // Lo mismo que respondería la API: esta cuenta no tiene registro de técnico.
        val NotTechnicianError = ApiResult.Failure(ApiError.Http(status = 404, code = "NOT_FOUND", message = null))
    }
}

private fun ByteArray.toPhotoPart(): MultipartBody.Part =
    MultipartBody.Part.createFormData("file", "perfil.jpg", toRequestBody("image/jpeg".toMediaType()))

private fun EmployeeDetailDto.toDomain() = TechnicianProfile(
    employeeId = id,
    photoUrl = profile?.photoUrl,
    bio = profile?.bio.orEmpty(),
    vehicle = vehicle?.toDomain(),
)

private fun VehicleDto.toDomain() = Vehicle(plate = plate, brand = brand, model = model, color = color)
