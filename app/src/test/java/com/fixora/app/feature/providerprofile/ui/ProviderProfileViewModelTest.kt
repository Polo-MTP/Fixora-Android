package com.fixora.app.feature.providerprofile.ui

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.providerprofile.domain.BasePrice
import com.fixora.app.feature.providerprofile.domain.ProviderProfile
import com.fixora.app.feature.providerprofile.domain.ProviderService
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
    private lateinit var viewModel: ProviderProfileViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create(categoryId: String? = null) {
        val handle = SavedStateHandle(mapOf("branchId" to "2", "distanceKm" to "1.2", "categoryId" to categoryId))
        viewModel = ProviderProfileViewModel(handle) { branchId ->
            requested += branchId
            error?.let { ApiResult.Failure(it) } ?: ApiResult.Success(PROFILE)
        }
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
