package com.fixora.app.feature.home.ui

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.map.data.MapRepository
import com.fixora.app.feature.map.domain.MapFilters
import com.fixora.app.feature.map.domain.MapSearchResult
import com.fixora.app.feature.map.domain.ServiceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientHomeViewModelTest {

    private val plumbing = ServiceCategory("1", "Plomería", "plomeria")
    private val electric = ServiceCategory("2", "Electricidad", "electricidad")
    private var error: ApiError? = null
    private lateinit var viewModel: ClientHomeViewModel
    private val state get() = viewModel.uiState.value

    private val repository = object : MapRepository {
        override suspend fun search(center: Coordinates, filters: MapFilters, categoryId: String?) =
            ApiResult.Success(MapSearchResult(emptyList(), 0))

        override suspend fun categories(): ApiResult<List<ServiceCategory>> =
            error?.let { ApiResult.Failure(it) } ?: ApiResult.Success(listOf(plumbing, electric))
    }

    private val firstName = MutableStateFlow<String?>(null)
    private val names = ClientFirstNameSource { firstName }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsTheServices() {
        viewModel = ClientHomeViewModel(repository, names)

        assertEquals(listOf(plumbing, electric), state.categories)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun greetingUsesTheClientFirstName() {
        viewModel = ClientHomeViewModel(repository, names)
        assertNull(state.firstName)

        firstName.value = "Misael"

        assertEquals("Misael", state.firstName)
    }

    @Test
    fun aServiceOpensTheMapWithIt() {
        viewModel = ClientHomeViewModel(repository, names)

        assertEquals("2", viewModel.categoryLaunch(electric).categoryId)
    }

    @Test
    fun searchingAServiceNameOpensThatService() {
        viewModel = ClientHomeViewModel(repository, names)
        viewModel.onQueryChange("  plomeria ")

        val launch = checkNotNull(viewModel.searchLaunch())

        assertEquals("1", launch.categoryId)
        assertEquals("", launch.query)
        assertEquals("", state.query)
    }

    @Test
    fun otherTextOpensTheMapFilteredByName() {
        viewModel = ClientHomeViewModel(repository, names)
        viewModel.onQueryChange("Juan Pérez")

        val launch = checkNotNull(viewModel.searchLaunch())

        assertNull(launch.categoryId)
        assertEquals("Juan Pérez", launch.query)
    }

    @Test
    fun emptySearchDoesNothing() {
        viewModel = ClientHomeViewModel(repository, names)
        viewModel.onQueryChange("   ")

        assertNull(viewModel.searchLaunch())
    }

    @Test
    fun failedServicesCanBeRetried() {
        error = ApiError.NoConnection
        viewModel = ClientHomeViewModel(repository, names)
        assertEquals(ApiError.NoConnection, state.error)

        error = null
        viewModel.retry()

        assertNull(state.error)
        assertEquals(2, state.categories.size)
    }
}
