package com.fixora.app.feature.map.ui

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.map.data.MapRepository
import com.fixora.app.feature.map.domain.MapFilters
import com.fixora.app.feature.map.domain.MapProvider
import com.fixora.app.feature.map.domain.MapRules
import com.fixora.app.feature.map.domain.MapSearchResult
import com.fixora.app.feature.map.domain.ServiceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private data class Search(val center: Coordinates, val filters: MapFilters, val categoryId: String?)

    private class FakeRepository : MapRepository {
        val searches = mutableListOf<Search>()
        var providers = listOf(provider("1", "Plomería Centro"), provider("2", "Electro Juan"))
        var total = 2
        var error: ApiError? = null

        override suspend fun search(center: Coordinates, filters: MapFilters, categoryId: String?): ApiResult<MapSearchResult> {
            searches += Search(center, filters, categoryId)
            error?.let { return ApiResult.Failure(it) }
            return ApiResult.Success(MapSearchResult(providers, total))
        }

        override suspend fun categories(): ApiResult<List<ServiceCategory>> = ApiResult.Success(
            listOf(ServiceCategory("1", "Plomería", "plomeria"), ServiceCategory("2", "Electricidad", "electricidad")),
        )
    }

    private class FakeLocation(var granted: Boolean = true, var position: Coordinates? = Coordinates(20.67, -103.35)) :
        LocationSource {
        override fun hasPermission() = granted
        override suspend fun current() = position
        override fun updates(intervalMs: Long): Flow<Coordinates> = emptyFlow()
    }

    private lateinit var repository: FakeRepository
    private lateinit var location: FakeLocation
    private lateinit var viewModel: MapViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeRepository()
        location = FakeLocation()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create() {
        viewModel = MapViewModel(repository, location)
    }

    @Test
    fun withPermissionItSearchesAroundTheUser() {
        create()

        val user = Coordinates(20.67, -103.35)
        assertEquals(listOf(Search(user, MapFilters(), null)), repository.searches)
        assertEquals(user, state.userLocation)
        assertFalse(state.usingDefaultCenter)
        assertEquals(2, state.visibleProviders.size)
        assertEquals(2, state.categories.size)
        assertEquals(user, state.camera?.center)
        assertEquals(MapRules.zoomFor(MapRules.DEFAULT_RADIUS_KM), state.camera?.zoom)
    }

    @Test
    fun deniedPermissionSearchesAroundTheDefaultCenter() = runBlocking {
        location.granted = false
        create()

        withTimeout(1_000) { viewModel.requestPermission.first() }
        assertTrue(repository.searches.isEmpty())

        viewModel.onPermissionResult(granted = false)

        assertEquals(MapRules.DEFAULT_CENTER, repository.searches.single().center)
        assertTrue(state.usingDefaultCenter)
        assertNull(state.userLocation)
    }

    @Test
    fun withoutGpsFixItAlsoUsesTheDefaultCenter() {
        location.position = null
        create()

        assertEquals(MapRules.DEFAULT_CENTER, repository.searches.single().center)
        assertTrue(state.usingDefaultCenter)
    }

    @Test
    fun choosingAServiceSearchesAgainAndAllClearsIt() {
        create()

        viewModel.onCategorySelected("1")
        viewModel.onCategorySelected("1")
        viewModel.onCategorySelected(null)

        assertEquals(listOf(null, "1", null), repository.searches.map { it.categoryId })
    }

    @Test
    fun typingFiltersByBusinessNameIgnoringAccents() {
        create()

        viewModel.onQueryChange("plomeria")

        assertEquals(listOf("1"), state.visibleProviders.map { it.id })
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun submittingAServiceNameSelectsThatService() {
        create()

        viewModel.onQueryChange("electri")
        viewModel.onQuerySubmit()

        assertEquals("2", state.selectedCategoryId)
        assertEquals("", state.query)
        assertEquals("2", repository.searches.last().categoryId)
    }

    @Test
    fun widerRadiusMovesTheCameraAndSearchesAgain() {
        create()
        val firstCamera = checkNotNull(state.camera)

        viewModel.onFiltersApplied(MapFilters(radiusKm = 50, availableNow = true))

        assertEquals(MapFilters(50, true), repository.searches.last().filters)
        assertEquals(MapRules.zoomFor(50), state.camera?.zoom)
        assertTrue(checkNotNull(state.camera).id > firstCamera.id)
    }

    @Test
    fun movingTheMapFarOffersToSearchThere() {
        create()

        viewModel.onCameraIdle(Coordinates(20.671, -103.351))
        assertFalse(state.canSearchHere)

        val elsewhere = Coordinates(20.75, -103.40)
        viewModel.onCameraIdle(elsewhere)
        assertTrue(state.canSearchHere)

        viewModel.onSearchHere()
        assertEquals(elsewhere, repository.searches.last().center)
        assertFalse(state.canSearchHere)
    }

    @Test
    fun selectionIsDroppedWhenTheProviderIsNoLongerShown() {
        create()
        viewModel.onProviderSelected("2")
        assertEquals("Electro Juan", state.selectedProvider?.businessName)

        viewModel.onQueryChange("centro")
        assertNull(state.selectedProviderId)

        viewModel.onQueryChange("")
        viewModel.onProviderSelected("1")
        repository.providers = listOf(provider("2", "Electro Juan"))
        viewModel.onCategorySelected("2")
        assertNull(state.selectedProviderId)
    }

    @Test
    fun failedSearchShowsTheErrorAndRetryRepeatsIt() {
        repository.error = ApiError.NoConnection
        create()
        assertEquals(ApiError.NoConnection, state.error)
        assertFalse(state.isLoading)

        repository.error = null
        viewModel.retry()

        assertNull(state.error)
        assertEquals(2, state.providers.size)
    }

    private companion object {
        fun provider(id: String, business: String) = MapProvider(
            id = id,
            name = "Matriz",
            businessName = business,
            independent = false,
            logoUrl = null,
            position = Coordinates(20.67, -103.35),
            distanceKm = 1.0,
            rating = 4.5,
            reviewsCount = 2,
            basePrice = null,
            openNow = true,
        )
    }
}
