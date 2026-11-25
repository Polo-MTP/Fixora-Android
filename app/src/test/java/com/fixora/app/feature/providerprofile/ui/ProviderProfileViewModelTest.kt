package com.fixora.app.feature.providerprofile.ui

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.providerprofile.domain.BasePrice
import com.fixora.app.feature.providerprofile.domain.ProviderProfile
import com.fixora.app.feature.providerprofile.domain.ProviderService
import com.fixora.app.feature.providerprofile.domain.Review
import com.fixora.app.feature.review.data.ReviewRepository
import com.fixora.app.feature.review.domain.RequestReviews
import com.fixora.app.feature.review.domain.ReviewDirection
import com.fixora.app.feature.review.domain.ReviewEvidence
import com.fixora.app.feature.review.domain.ReviewsPage
import com.fixora.app.feature.review.domain.ServiceReview
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderProfileViewModelTest {

    private val requested = mutableListOf<String>()
    private var error: ApiError? = null
    private var profile = PROFILE

    /** Páginas de reseñas pedidas y lo que regresa cada una (ANDROID-26). */
    private val reviewPages = mutableListOf<Int>()
    private var reviewsError: ApiError? = null
    private val reviews = object : ReviewRepository {
        override suspend fun branchReviews(branchId: String, page: Int, limit: Int): ApiResult<ReviewsPage> {
            reviewPages += page
            reviewsError?.let { return ApiResult.Failure(it) }
            val items = (1..limit).map { n ->
                ServiceReview("$page-$n", ReviewDirection.ClientToProvider, 4, "Bien", "Ana P.", null, listOf(ReviewEvidence("https://f/$n.jpg", isVideo = n == 2)))
            }.take(if (page == 2) 2 else limit)
            return ApiResult.Success(ReviewsPage(items, page, totalPages = 2))
        }
        override suspend fun requestReviews(requestId: String): ApiResult<RequestReviews> = error("no se usa")
        override suspend fun uploadEvidence(mimeType: String, bytes: ByteArray): ApiResult<String> = error("no se usa")
        override suspend fun create(requestId: String, rating: Int, comment: String?, evidenceKeys: List<String>, clientUuid: String): ApiResult<ServiceReview> =
            error("no se usa")
    }
    private lateinit var viewModel: ProviderProfileViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create(categoryId: String? = null) {
        val handle = SavedStateHandle(mapOf("branchId" to "2", "distanceKm" to "1.2", "categoryId" to categoryId))
        viewModel = ProviderProfileViewModel(
            handle,
            { branchId ->
                requested += branchId
                error?.let { ApiResult.Failure(it) } ?: ApiResult.Success(profile)
            },
            reviews,
        )
    }

    @Test
    fun loadsTheBranchFromTheRoute() {
        create()

        assertEquals(listOf("2"), requested)
        assertEquals(PROFILE, state.profile)
        assertEquals("1.2", state.distanceKm)
        assertFalse(state.isLoading)
        assertEquals(ProfileTab.Info, state.tab)
    }

    @Test
    fun priceIsTheOneOfTheServiceChosenInTheMap() {
        create(categoryId = "2")
        assertEquals(BasePrice("500.00", isMinimum = false), state.basePrice)

        create(categoryId = null)
        assertEquals(BasePrice("300.00", isMinimum = true), state.basePrice)
    }

    @Test
    fun errorCanBeRetried() {
        error = ApiError.NoConnection
        create()
        assertEquals(ApiError.NoConnection, state.error)
        assertNull(state.profile)

        error = null
        viewModel.retry()

        assertNull(state.error)
        assertEquals(PROFILE, state.profile)
        assertEquals(2, requested.size)
    }

    @Test
    fun requestButtonCarriesTheServiceFromTheMap() {
        create(categoryId = "2")

        assertEquals("2", state.categoryId)
        assertEquals("1.2", state.distanceKm)

        viewModel.onTabSelected(ProfileTab.Certificates)
        assertEquals(ProfileTab.Certificates, state.tab)
    }

    @Test
    fun withMoreReviewsThanTheLatestOnesTheWholeListLoadsByPages() {
        profile = PROFILE.copy(reviewsCount = 12, latestReviews = List(5) { Review(5, "", "Luis") })
        create()
        assertTrue(state.hasMoreReviews)
        assertEquals(5, state.shownReviews.size)

        viewModel.loadMoreReviews()
        // La primera página reemplaza a las últimas de la ficha.
        assertEquals(listOf(1), reviewPages)
        assertEquals(10, state.shownReviews.size)
        assertEquals(listOf("https://f/1.jpg"), state.shownReviews.first().photoUrls)
        // Los videos no se muestran como foto.
        assertTrue(state.shownReviews[1].photoUrls.isEmpty())

        viewModel.loadMoreReviews()
        assertEquals(listOf(1, 2), reviewPages)
        assertEquals(12, state.shownReviews.size)
        assertFalse(state.hasMoreReviews)

        viewModel.loadMoreReviews()
        assertEquals(listOf(1, 2), reviewPages)
    }

    @Test
    fun withOnlyTheLatestReviewsThereIsNothingMoreToLoad() {
        profile = PROFILE.copy(reviewsCount = 2, latestReviews = List(2) { Review(5, "", "Luis") })
        create()

        assertFalse(state.hasMoreReviews)
        viewModel.loadMoreReviews()
        assertTrue(reviewPages.isEmpty())
    }

    @Test
    fun ifMoreReviewsFailTheLatestStayAndItCanBeRetried() {
        profile = PROFILE.copy(reviewsCount = 12, latestReviews = List(5) { Review(5, "", "Luis") })
        create()
        reviewsError = ApiError.NoConnection

        viewModel.loadMoreReviews()

        assertEquals(ApiError.NoConnection, state.reviewsError)
        assertEquals(5, state.shownReviews.size)
        reviewsError = null
        viewModel.loadMoreReviews()
        assertEquals(10, state.shownReviews.size)
    }

    private companion object {
        val PROFILE = ProviderProfile(
            id = "2", name = "Matriz", businessName = "Hogar y Servicios", independent = false, description = "",
            logoUrl = null, verified = true, plates = emptyList(), rating = 0.0, reviewsCount = 0, totalJobs = 0,
            acceptingRequests = true, colony = "", municipality = "", position = null, schedules = emptyList(),
            services = listOf(ProviderService("1", "Plomería", "300.00", 60), ProviderService("2", "Electricidad", "500.00", 60)),
            works = emptyList(), certificates = emptyList(),
        )
    }
}
