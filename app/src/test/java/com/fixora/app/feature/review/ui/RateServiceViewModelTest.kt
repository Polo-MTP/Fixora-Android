package com.fixora.app.feature.review.ui

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.review.data.ReviewRepository
import com.fixora.app.feature.review.domain.RequestReviews
import com.fixora.app.feature.review.domain.ReviewDirection
import com.fixora.app.feature.review.domain.ReviewRules
import com.fixora.app.feature.review.domain.ReviewsPage
import com.fixora.app.feature.review.domain.ServiceReview
import com.fixora.app.feature.servicerequest.data.EvidenceProblem
import com.fixora.app.feature.servicerequest.data.EvidenceReadResult
import com.fixora.app.feature.servicerequest.domain.RequestStatus
import com.fixora.app.feature.servicerequest.domain.ServiceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Calificar al terminar el servicio (ANDROID-26). */
@OptIn(ExperimentalCoroutinesApi::class)
class RateServiceViewModelTest {

    private class FakeReviews : ReviewRepository {
        var status = RequestReviews(ReviewDirection.ClientToProvider, canReview = true, reviews = emptyList())
        val uploads = mutableListOf<String>()
        var failUploads = false
        val created = mutableListOf<List<Any?>>()
        var createError: ApiError? = null
        override suspend fun requestReviews(requestId: String) = ApiResult.Success(status)
        override suspend fun uploadEvidence(mimeType: String, bytes: ByteArray): ApiResult<String> {
            uploads += mimeType
            if (failUploads) return ApiResult.Failure(ApiError.NoConnection)
            return ApiResult.Success("evidences/4/${uploads.size}.jpg")
        }
        override suspend fun create(
            requestId: String, rating: Int, comment: String?, evidenceKeys: List<String>, clientUuid: String,
        ): ApiResult<ServiceReview> {
            created += listOf(requestId, rating, comment, evidenceKeys, clientUuid)
            createError?.let { return ApiResult.Failure(it) }
            return ApiResult.Success(ServiceReview("9", ReviewDirection.ClientToProvider, rating, comment.orEmpty(), "Ana P.", null))
        }
        override suspend fun branchReviews(branchId: String, page: Int, limit: Int): ApiResult<ReviewsPage> = error("no se usa")
    }

    private val request = ServiceRequest(
        id = "31", status = RequestStatus.Completed, isEmergency = false, description = "Fuga", categoryName = "Plomería",
        clientName = "Carla Prueba", clientRating = 0.0, zone = "Centro", position = Coordinates(25.54, -103.41),
        basePrice = "300.00", photoUrls = emptyList(), createdAt = null, expiresAt = null, scheduledAt = null,
        technicianName = "Juan Pérez", technicianPhotoUrl = "https://f/juan.jpg",
    )

    private lateinit var reviews: FakeReviews
    private lateinit var viewModel: RateServiceViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        reviews = FakeReviews()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create() {
        viewModel = RateServiceViewModel(SavedStateHandle(mapOf("requestId" to "31")), reviews, { ApiResult.Success(request) }) { _, _ ->
            error("En las pruebas la evidencia se agrega con addEvidence")
        }
    }

    private fun photo() = EvidenceReadResult.Ready("foto.jpg", "image/jpeg", byteArrayOf(1))

    @Test
    fun theClientRatesTheTechnicianThatCame() {
        create()

        assertEquals("Juan Pérez", state.counterpartName)
        assertEquals("https://f/juan.jpg", state.counterpartPhotoUrl)
        assertTrue(state.canReview)
    }

    @Test
    fun theTechnicianRatesTheClient() {
        reviews.status = RequestReviews(ReviewDirection.ProviderToClient, canReview = true, reviews = emptyList())
        create()

        assertEquals("Carla Prueba", state.counterpartName)
        assertNull(state.counterpartPhotoUrl)
    }

    @Test
    fun withoutStarsItIsNotSent() {
        create()

        viewModel.submit()

        assertTrue(state.ratingMissing)
        assertTrue(reviews.created.isEmpty())
    }

    @Test
    fun itUploadsTheEvidenceAndSavesTheReview() {
        create()
        viewModel.onRatingChange(5)
        viewModel.onCommentChange("  Muy puntual  ")
        viewModel.addEvidence { photo() }

        viewModel.submit()

        assertEquals(listOf("image/jpeg"), reviews.uploads)
        val sent = reviews.created.single()
        assertEquals(listOf("31", 5, "Muy puntual", listOf("evidences/4/1.jpg")), sent.take(4))
        assertTrue(state.isDone)
    }

    @Test
    fun aRetryUsesTheSameUuidSoItIsNotSavedTwice() {
        create()
        viewModel.onRatingChange(4)
        reviews.createError = ApiError.NoConnection
        viewModel.submit()
        assertEquals(ApiError.NoConnection, state.error)

        reviews.createError = null
        viewModel.submit()

        assertEquals(2, reviews.created.size)
        assertEquals(reviews.created[0][4], reviews.created[1][4])
        assertTrue(state.isDone)
    }

    @Test
    fun ifAnUploadFailsNothingIsSaved() {
        create()
        viewModel.onRatingChange(3)
        viewModel.addEvidence { photo() }
        reviews.failUploads = true

        viewModel.submit()

        assertEquals(ApiError.NoConnection, state.error)
        assertTrue(reviews.created.isEmpty())
        assertFalse(state.isSending)
    }

    @Test
    fun noMoreThanThreeFilesAndAPdfIsExplained() {
        create()
        repeat(4) { viewModel.addEvidence { photo() } }
        assertEquals(ReviewRules.MAX_EVIDENCE, state.evidence.size)

        viewModel.removeEvidence(state.evidence.first().id)
        viewModel.addEvidence { EvidenceReadResult.Rejected(EvidenceProblem.WrongType) }
        assertEquals(EvidenceProblem.WrongType, state.problem)
        assertEquals(2, state.evidence.size)
    }

    @Test
    fun ifItWasAlreadyRatedItShowsTheReviewInsteadOfTheForm() {
        val mine = ServiceReview("3", ReviewDirection.ClientToProvider, 4, "Bien", "Ana P.", null)
        reviews.status = RequestReviews(ReviewDirection.ClientToProvider, canReview = false, reviews = listOf(mine))
        create()

        assertEquals(mine, state.existing)
        assertFalse(state.notYet)
        viewModel.onRatingChange(5)
        viewModel.submit()
        assertTrue(reviews.created.isEmpty())
    }

    @Test
    fun beforeTheServiceEndsItCannotBeRated() {
        reviews.status = RequestReviews(ReviewDirection.ClientToProvider, canReview = false, reviews = emptyList())
        create()

        assertTrue(state.notYet)
    }

    @Test
    fun ifItWasRatedFromAnotherPhoneItShowsTheOneThatStayed() {
        create()
        viewModel.onRatingChange(2)
        val mine = ServiceReview("3", ReviewDirection.ClientToProvider, 5, "", "Ana P.", null)
        reviews.createError = ApiError.Http(409, "REVIEW_ALREADY_EXISTS", "Esta solicitud ya fue calificada en este sentido.")
        reviews.status = RequestReviews(ReviewDirection.ClientToProvider, canReview = false, reviews = listOf(mine))

        viewModel.submit()

        assertEquals(mine, state.existing)
        assertFalse(state.isDone)
    }
}
