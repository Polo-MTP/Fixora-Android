package com.fixora.app.feature.review.data

import com.fixora.app.core.network.ApiResult
import com.fixora.app.core.network.safeApiCall
import com.fixora.app.feature.review.domain.RequestReviews
import com.fixora.app.feature.review.domain.ReviewDirection
import com.fixora.app.feature.review.domain.ReviewEvidence
import com.fixora.app.feature.review.domain.ReviewsPage
import com.fixora.app.feature.review.domain.ServiceReview
import com.fixora.app.feature.servicerequest.data.EvidencesCategory
import com.fixora.app.feature.servicerequest.data.evidencePart
import com.fixora.app.feature.servicerequest.domain.parseApiInstant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/** Calificar al terminar el servicio y ver reseñas (ANDROID-26). Interfaz para probar sin red. */
interface ReviewRepository {
    suspend fun requestReviews(requestId: String): ApiResult<RequestReviews>

    /** Sube una foto o un video de la reseña y devuelve su llave. */
    suspend fun uploadEvidence(mimeType: String, bytes: ByteArray): ApiResult<String>

    /** [clientUuid] es el mismo en cada reintento, para que la API no la guarde dos veces. */
    suspend fun create(
        requestId: String,
        rating: Int,
        comment: String?,
        evidenceKeys: List<String>,
        clientUuid: String,
    ): ApiResult<ServiceReview>

    suspend fun branchReviews(branchId: String, page: Int, limit: Int): ApiResult<ReviewsPage>
}

class RemoteReviewRepository(
    private val api: ReviewApi,
    private val json: Json,
) : ReviewRepository {

    override suspend fun requestReviews(requestId: String) = safeApiCall(json) {
        val data = api.requestReviews(requestId).data
        RequestReviews(
            // La API siempre lo manda; si no, se asume que escribe el cliente.
            myDirection = ReviewDirection.from(data.myDirection) ?: ReviewDirection.ClientToProvider,
            canReview = data.canReview,
            reviews = data.reviews.map { it.toDomain() },
        )
    }

    override suspend fun uploadEvidence(mimeType: String, bytes: ByteArray) = safeApiCall(json) {
        api.uploadEvidence(EvidencesCategory, evidencePart(mimeType, bytes)).data.fileKey
    }

    override suspend fun create(
        requestId: String,
        rating: Int,
        comment: String?,
        evidenceKeys: List<String>,
        clientUuid: String,
    ) = safeApiCall(json) {
        val body = CreateReviewBody(
            rating = rating,
            comment = comment,
            attachmentKeys = evidenceKeys.takeIf { it.isNotEmpty() },
            clientUuid = clientUuid,
        )
        api.create(requestId, body).data.toDomain()
    }

    override suspend fun branchReviews(branchId: String, page: Int, limit: Int) = safeApiCall(json) {
        val data = api.branchReviews(branchId, page, limit).data
        ReviewsPage(
            items = data.items.map { it.toDomain() },
            page = data.pagination.page,
            totalPages = data.pagination.totalPages,
        )
    }
}

internal fun ReviewDto.toDomain() = ServiceReview(
    id = id,
    direction = ReviewDirection.from(direction),
    rating = rating.coerceIn(1, 5),
    comment = comment.orEmpty().trim(),
    authorName = authorName,
    createdAt = parseApiInstant(createdAt),
    evidence = evidence.mapNotNull { item ->
        item.url?.takeIf { it.isNotBlank() }?.let { ReviewEvidence(it, isVideo = item.mimeType.startsWith("video/")) }
    },
)

@Module
@InstallIn(SingletonComponent::class)
object ReviewModule {
    @Provides
    @Singleton
    fun provideReviewApi(retrofit: Retrofit): ReviewApi = retrofit.create()

    @Provides
    @Singleton
    fun provideReviewRepository(api: ReviewApi, json: Json): ReviewRepository = RemoteReviewRepository(api, json)
}
