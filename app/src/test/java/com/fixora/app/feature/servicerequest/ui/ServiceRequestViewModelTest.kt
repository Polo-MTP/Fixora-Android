package com.fixora.app.feature.servicerequest.ui

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceRequestViewModelTest {

    private val requested = mutableListOf<String>()
    private var error: ApiError? = null
    private lateinit var viewModel: ServiceRequestViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create() {
        viewModel = ServiceRequestViewModel(SavedStateHandle(mapOf("requestId" to "15"))) { id ->
            requested += id
            error?.let { ApiResult.Failure(it) } ?: ApiResult.Success(REQUEST)
        }
    }

    @Test
    fun loadsTheRequestFromTheRoute() {
        create()

        assertEquals(listOf("15"), requested)
        assertEquals(REQUEST, state.request)
        assertFalse(state.isLoading)
    }

    @Test
    fun errorCanBeRetried() {
        error = ApiError.NoConnection
        create()
        assertEquals(ApiError.NoConnection, state.error)

        error = null
        viewModel.retry()

        assertNull(state.error)
        assertEquals(REQUEST, state.request)
    }

    private companion object {
        val REQUEST = ServiceRequest(
            id = "15", status = RequestStatus.Pending, isEmergency = false, description = "Fuga",
            categoryName = "Plomería", clientName = "Roberto", clientRating = 0.0, zone = "Juárez",
            basePrice = "350.00", photoUrls = emptyList(), createdAt = null, expiresAt = null,
            scheduledAt = null, technicianName = null,
        )
    }
}
