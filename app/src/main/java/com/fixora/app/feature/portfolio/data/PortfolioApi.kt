package com.fixora.app.feature.portfolio.data

import com.fixora.app.core.network.DataResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Evidencias de trabajo y certificados del proveedor (ANDROID-8 con API-20). */
interface PortfolioApi {
    @GET("branches/{id}/portfolio")
    suspend fun getWorks(@Path("id") branchId: String): DataResponse<List<WorkDto>>

    /** Las fotos se suben antes con [uploadFile]; aquí van sus llaves (de 0 a 10). */
    @POST("branches/{id}/portfolio")
    suspend fun addWork(@Path("id") branchId: String, @Body body: WorkRequest): DataResponse<WorkDto>

    @DELETE("portfolio/{id}")
    suspend fun deleteWork(@Path("id") workId: String)

    /** Agrega fotos a un trabajo que ya existe, sin pasar de 10 en total. */
    @POST("portfolio/{id}/media")
    suspend fun addMedia(@Path("id") workId: String, @Body body: MediaListRequest): DataResponse<WorkDto>

    @Multipart
    @POST("files/upload")
    suspend fun uploadFile(
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part,
    ): DataResponse<UploadedDto>

    /** Solicitud del proveedor con todos sus documentos; de ahí salen sus certificados. */
    @GET("supplier-applications/me")
    suspend fun getMyApplication(): DataResponse<ApplicationDto>

    /** Certificado nuevo de un proveedor aprobado: queda pendiente de revisión. */
    @Multipart
    @POST("supplier-documents")
    suspend fun uploadCertificate(@Part file: MultipartBody.Part): DataResponse<DocumentDto>

    /** Solo se puede publicar un certificado aprobado. */
    @PATCH("supplier-documents/{id}/visibility")
    suspend fun setVisibility(@Path("id") documentId: String, @Body body: VisibilityRequest): DataResponse<DocumentDto>
}

@Serializable
data class WorkDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val media: List<MediaDto> = emptyList(),
)

@Serializable
data class MediaDto(val id: String, val url: String = "")

@Serializable
data class WorkRequest(
    val title: String,
    val description: String?,
    val media: List<MediaRequest>,
)

@Serializable
data class MediaRequest(@SerialName("file_key") val fileKey: String)

@Serializable
data class MediaListRequest(val media: List<MediaRequest>)

@Serializable
data class UploadedDto(@SerialName("file_key") val fileKey: String)

@Serializable
data class ApplicationDto(val documents: List<DocumentDto> = emptyList())

@Serializable
data class DocumentDto(
    val id: String,
    @SerialName("type_name") val typeName: String,
    val mandatory: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("upload_date") val uploadDate: String? = null,
    val validation: ValidationDto? = null,
)

@Serializable
data class ValidationDto(val result: String, val comment: String? = null)

@Serializable
data class VisibilityRequest(@SerialName("is_public") val isPublic: Boolean)
