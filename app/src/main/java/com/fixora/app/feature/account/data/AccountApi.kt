package com.fixora.app.feature.account.data

import com.fixora.app.core.network.DataResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Endpoints de Mi cuenta en la API (API-10). Todos requieren sesión. */
interface AccountApi {
    @GET("me")
    suspend fun getMe(): DataResponse<MeDto>

    @PATCH("me")
    suspend fun updatePersonalData(@Body body: PersonalDataRequest): DataResponse<PersonalDataDto>

    @PATCH("me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @GET("me/addresses")
    suspend fun getAddresses(): DataResponse<List<AddressDto>>

    @POST("me/addresses")
    suspend fun createAddress(@Body body: CreateAddressRequest): DataResponse<AddressDto>

    /** Si cambian los datos de la dirección, la API crea otra fila: el id de la respuesta es el nuevo. */
    @PATCH("me/addresses/{id}")
    suspend fun updateAddress(@Path("id") id: String, @Body body: UpdateAddressRequest): DataResponse<AddressDto>

    @PATCH("me/addresses/{id}/main")
    suspend fun setMainAddress(@Path("id") id: String)

    /** La API la desactiva; si era la principal, la siguiente pasa a serlo. */
    @DELETE("me/addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: String)
}

@Serializable
data class MeDto(@SerialName("personal_data") val personalData: PersonalDataDto? = null)

@Serializable
data class PersonalDataDto(
    val name: String? = null,
    @SerialName("lastname_p") val lastNameP: String? = null,
    @SerialName("lastname_m") val lastNameM: String? = null,
    /** Formato yyyy-MM-dd. */
    @SerialName("birth_date") val birthDate: String? = null,
    val phone: String? = null,
)

/**
 * Los campos opcionales vacíos se mandan como "" y no como null: el Json de la app omite los
 * null, y para la API un campo que no llega significa «no lo cambies». Con "" lo borra.
 */
@Serializable
data class PersonalDataRequest(
    val name: String,
    @SerialName("lastname_p") val lastNameP: String,
    @SerialName("lastname_m") val lastNameM: String,
    @SerialName("birth_date") val birthDate: String,
    val phone: String,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class AddressDto(
    val id: String,
    val alias: String? = null,
    @SerialName("is_main") val isMain: Boolean = false,
    val reference: String? = null,
    val street: String = "",
    @SerialName("num_ext") val numExt: String = "",
    @SerialName("num_int") val numInt: String? = null,
    val colony: String = "",
    val municipality: String = "",
    val state: String = "",
    @SerialName("postal_code") val postalCode: String = "",
)

@Serializable
data class CreateAddressRequest(
    val alias: String,
    val street: String,
    @SerialName("num_ext") val numExt: String,
    @SerialName("num_int") val numInt: String,
    val colony: String,
    val municipality: String,
    val state: String,
    @SerialName("postal_code") val postalCode: String,
    val reference: String,
    @SerialName("is_main") val isMain: Boolean,
)

/**
 * La API no acepta `is_main` al editar (la principal se cambia con su propio endpoint). Los campos
 * null no se mandan: si solo cambió el alias, la dirección conserva su id.
 */
@Serializable
data class UpdateAddressRequest(
    val alias: String,
    val street: String? = null,
    @SerialName("num_ext") val numExt: String? = null,
    @SerialName("num_int") val numInt: String? = null,
    val colony: String? = null,
    val municipality: String? = null,
    val state: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val reference: String? = null,
)
