package com.fixora.app.feature.providerprofile.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.providerprofile.data.ProviderProfileRepository
import com.fixora.app.feature.providerprofile.domain.Review
import com.fixora.app.feature.review.data.ReviewRepository
import com.fixora.app.feature.providerprofile.domain.BasePrice
import com.fixora.app.feature.providerprofile.domain.ProviderProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfileTab { Info, Reviews, Works, Certificates }

data class ProviderProfileUiState(
    val isLoading: Boolean = true,
    val profile: ProviderProfile? = null,
    val error: ApiError? = null,
    /** Distancia que mostró el mapa, ya escrita: «1.2». */
    val distanceKm: String? = null,
    val basePrice: BasePrice? = null,
    val tab: ProfileTab = ProfileTab.Info,
    /** Servicio elegido en el mapa; el formulario de solicitud lo trae elegido. */
    val categoryId: String? = null,
    /**
     * Reseñas ya cargadas con «Ver más» (ANDROID-26); null mientras solo se muestran las últimas
     * que trae la ficha.
     */
    val reviews: List<Review>? = null,
    val reviewsPage: Int = 0,
    val moreReviewsAvailable: Boolean = true,
    val isLoadingReviews: Boolean = false,
    val reviewsError: ApiError? = null,
) {
    val shownReviews: List<Review> get() = reviews ?: profile?.latestReviews.orEmpty()

    /** Faltan reseñas por ver: la ficha trae solo las últimas y la lista va por páginas. */
    val hasMoreReviews: Boolean
        get() = profile != null && moreReviewsAvailable && profile.reviewsCount > shownReviews.size
}

/** Ficha pública del proveedor (ANDROID-11). */
@HiltViewModel
class ProviderProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProviderProfileRepository,
    private val reviewRepository: ReviewRepository,
) : ViewModel() {

    // Los argumentos de ProviderProfileRoute se guardan con el nombre de cada propiedad.
    private val branchId: String = checkNotNull(savedStateHandle["branchId"])
    private val categoryId: String? = savedStateHandle["categoryId"]

    private val _uiState = MutableStateFlow(
        ProviderProfileUiState(distanceKm = savedStateHandle["distanceKm"], categoryId = savedStateHandle["categoryId"]),
    )
    val uiState: StateFlow<ProviderProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun onTabSelected(tab: ProfileTab) = _uiState.update { it.copy(tab = tab) }

    /** La siguiente página de reseñas; la primera reemplaza a las últimas que trae la ficha. */
    fun loadMoreReviews() {
        val state = _uiState.value
        if (state.isLoadingReviews || !state.hasMoreReviews) return
        val page = state.reviewsPage + 1
        _uiState.update { it.copy(isLoadingReviews = true, reviewsError = null) }
        viewModelScope.launch {
            when (val result = reviewRepository.branchReviews(branchId, page, REVIEWS_PAGE_SIZE)) {
                is ApiResult.Success -> _uiState.update {
                    val items = result.data.items.map { review ->
                        Review(
                            rating = review.rating,
                            comment = review.comment,
                            author = review.authorName,
                            createdAt = review.createdAt,
                            photoUrls = review.evidence.filterNot { item -> item.isVideo }.map { item -> item.url },
                        )
                    }
                    it.copy(
                        isLoadingReviews = false,
                        reviews = if (page == 1) items else it.reviews.orEmpty() + items,
                        reviewsPage = page,
                        moreReviewsAvailable = result.data.hasMore,
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoadingReviews = false, reviewsError = result.error) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.profile(branchId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, profile = result.data, basePrice = result.data.basePriceFor(categoryId))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    companion object {
        const val REVIEWS_PAGE_SIZE = 10
    }
}
