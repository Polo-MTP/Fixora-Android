package com.fixora.app.feature.profile.data

import com.fixora.app.core.network.DataResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Perfil del técnico (API-20): foto, biografía y vehículo. Todo cuelga del registro de empleado
 * (employees), que tienen los técnicos, gerentes y el proveedor independiente.
 */
interface TechnicianProfileApi {
    /** 404 si la cuenta no atiende servicios (por ejemplo, el encargado de una empresa). */
    @GET("me/employee-profile")
    suspend fun getMyProfile(): DataResponse<EmployeeDetailDto>

    @PUT("employees/{id}/profile")
    suspend fun updateProfile(@Path("id") employeeId: String, @Body body: ProfileRequest): DataResponse<ProfileDto>

    @PUT("employees/{id}/vehicle")
    suspend fun saveVehicle(@Path("id") employeeId: String, @Body body: VehicleRequest): DataResponse<VehicleDto>

    @DELETE("employees/{id}/vehicle")
    suspend fun deleteVehicle(@Path("id") employeeId: String)

    /** La foto se sube primero; la API devuelve su llave y esa se guarda en el perfil. */
    @Multipart
    @POST("files/upload")
    suspend fun uploadFile(
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part,
    ): DataResponse<UploadedFileDto>
}

@Serializable
data class EmployeeDetailDto(
    val id: String,
    /** Sucursal del técnico: el portafolio de trabajos (ANDROID-8) va por sucursal. */
    @SerialName("branch_id") val branchId: String? = null,
    val profile: ProfileDto? = null,
    val vehicle: VehicleDto? = null,
)

@Serializable
data class ProfileDto(
    @SerialName("photo_url") val photoUrl: String? = null,
    val bio: String? = null,
)

@Serializable
data class VehicleDto(
    val plate: String = "",
    val brand: String = "",
    val model: String = "",
    val color: String = "",
)

/** [photoKey] null no se manda: la foto se queda como está. Una [bio] vacía la borra. */
@Serializable
data class ProfileRequest(
    val bio: String,
    @SerialName("photo_key") val photoKey: String? = null,
)

@Serializable
data class VehicleRequest(val plate: String, val brand: String, val model: String, val color: String)

@Serializable
data class UploadedFileDto(@SerialName("file_key") val fileKey: String)
