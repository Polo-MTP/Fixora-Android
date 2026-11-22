package com.fixora.app.feature.requestform.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.PersonalData
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.providerprofile.domain.ProviderProfile
import com.fixora.app.feature.providerprofile.domain.ProviderService
import com.fixora.app.feature.requestform.data.RequestFormRepository
import com.fixora.app.feature.requestform.domain.AddressChoice
import com.fixora.app.feature.requestform.domain.CurrentAddress
import com.fixora.app.feature.requestform.domain.RequestDraft
import com.fixora.app.feature.requestform.domain.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RequestFormViewModelTest {

    private class FakeAccount : AccountRepository {
        val list = MutableStateFlow<List<Address>>(emptyList())
        override val personalData: Flow<PersonalData> = emptyFlow()
        override val addresses: Flow<List<Address>> = list
        override val personalDataError: Flow<ApiError?> = emptyFlow()
        override val addressesError: Flow<ApiError?> = emptyFlow()
        override suspend fun refreshPersonalData() = ApiResult.Success(Unit)
        override suspend fun refreshAddresses() = ApiResult.Success(Unit)
        override suspend fun updatePersonalData(data: PersonalData) = ApiResult.Success(Unit)
        override suspend fun saveAddress(address: Address) = ApiResult.Success(Unit)
        override suspend fun deleteAddress(id: String) = ApiResult.Success(Unit)
        override suspend fun setMainAddress(id: String) = ApiResult.Success(Unit)
        override suspend fun changePassword(current: String, new: String) = ApiResult.Success(Unit)
        override suspend fun updatePhoto(photo: ByteArray?) = ApiResult.Success(Unit)
    }

    private class FakeForm : RequestFormRepository {
        val drafts = mutableListOf<RequestDraft>()
        val slotDays = mutableListOf<LocalDate>()
        var uploads = 0
        var slots: List<TimeSlot> = emptyList()
        override suspend fun uploadPhotos(photos: List<ByteArray>): ApiResult<List<String>> {
            uploads += photos.size
            return ApiResult.Success(photos.indices.map { "photos/30/$it.jpg" })
        }
        override suspend fun freeSlots(branchId: String, categoryId: String, date: LocalDate): ApiResult<List<TimeSlot>> {
            slotDays += date
            return ApiResult.Success(slots)
        }
        override suspend fun create(draft: RequestDraft): ApiResult<String> {
            drafts += draft
            return ApiResult.Success("31")
        }
    }

    private val zone = ZoneId.of("America/Mexico_City")
    private val today = LocalDate.of(2026, 11, 21)
    private val now = Instant.parse("2026-11-21T18:00:00Z") // 12:00 en CDMX
    private val plumbing = ProviderService("1", "Plomería", "350.00", 60)
    private val electric = ProviderService("2", "Electricidad", "500.00", 60)
    private val home = Address("14", "Casa", "Av. Juárez", "10", "", "Centro", "06000", "Cuauhtémoc", "CDMX", "", isMain = true)
    private val work = Address("15", "Trabajo", "Reforma", "222", "", "Juárez", "06600", "Cuauhtémoc", "CDMX", "", isMain = false)

    private lateinit var account: FakeAccount
    private lateinit var form: FakeForm
    private var services = listOf(plumbing, electric)
    private var currentAddress: CurrentAddress? = null
    private lateinit var viewModel: RequestFormViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        account = FakeAccount().apply { list.value = listOf(work, home) }
        form = FakeForm()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create(categoryId: String? = null) {
        val handle = SavedStateHandle(mapOf("branchId" to "2", "categoryId" to categoryId, "distanceKm" to "1.2"))
        viewModel = RequestFormViewModel(
            savedStateHandle = handle,
            profiles = { ApiResult.Success(provider()) },
            account = account,
            repository = form,
            photoReader = { byteArrayOf(9) },
            currentAddress = { currentAddress },
        )
        viewModel.today = { today }
        viewModel.zone = zone
        viewModel.now = { now }
    }

    private fun provider() = ProviderProfile(
        id = "2", name = "Matriz", businessName = "Plomería Prueba", independent = true, description = "", logoUrl = null,
        verified = true, plates = emptyList(), rating = 0.0, reviewsCount = 0, totalJobs = 0, acceptingRequests = true,
        colony = "", municipality = "", position = null, schedules = emptyList(), services = services,
        works = emptyList(), certificates = emptyList(),
    )

    @Test
    fun theServiceFromTheMapAndTheMainAddressComeChosen() {
        create(categoryId = "2")

        assertEquals("2", state.serviceId)
        assertEquals("$500", "$" + state.service!!.basePrice.removeSuffix(".00"))
        assertEquals(AddressChoice.Saved("14"), state.addressChoice)
        assertEquals(MissingField.Description, state.missing)
    }

    @Test
    fun withOneServiceItIsChosenAlone() {
        services = listOf(plumbing)
        create()

        assertEquals("1", state.serviceId)
        assertEquals("Fuga de agua", state.suggestions.first())
    }

    @Test
    fun withSeveralItAsksWhichOne() {
        create()

        assertNull(state.serviceId)
        assertEquals(MissingField.Service, state.missing)
    }

    @Test
    fun sendsTheRequestNowWithTheProblemAndPhotos() {
        create(categoryId = "1")
        viewModel.onProblemSelected("Fuga de agua")
        viewModel.onDetailsChange("Gotea el lavabo")
        viewModel.onPhotosPicked(listOf(Uri.EMPTY))

        viewModel.send()

        val draft = form.drafts.single()
        assertEquals("Fuga de agua. Gotea el lavabo", draft.description)
        assertEquals("14", draft.savedAddressId)
        assertEquals(listOf("photos/30/0.jpg"), draft.photoKeys)
        assertNull(draft.scheduledAt)
        assertEquals("31", state.createdRequestId)
    }

    @Test
    fun retryingKeepsTheSameClientUuid() {
        create(categoryId = "1")
        viewModel.onProblemSelected("Sin agua")
        viewModel.send()
        viewModel.send()

        assertEquals(1, form.drafts.map { it.clientUuid }.toSet().size)
    }

    @Test
    fun schedulingShowsOnlyFreeSlotsOfThatDayStillAhead() {
        create(categoryId = "1")
        viewModel.onProblemSelected("Sin agua")
        val past = TimeSlot(Instant.parse("2026-11-21T16:00:00Z"), Instant.parse("2026-11-21T17:00:00Z"))
        val later = TimeSlot(Instant.parse("2026-11-21T20:00:00Z"), Instant.parse("2026-11-21T21:00:00Z"))
        val nextDay = TimeSlot(Instant.parse("2026-11-22T07:00:00Z"), Instant.parse("2026-11-22T08:00:00Z"))
        form.slots = listOf(past, later, nextDay)

        viewModel.onWhenModeChange(WhenMode.Scheduled)

        assertEquals(7, state.days.size)
        assertEquals(listOf(today), form.slotDays)
        assertEquals(listOf(later), state.slots)
        assertEquals(MissingField.Slot, state.missing)

        viewModel.onSlotSelected(later)
        viewModel.send()
        assertEquals(later.startsAt, form.drafts.single().scheduledAt)
    }

    @Test
    fun changingTheDayAsksForThatDay() {
        create(categoryId = "1")
        viewModel.onWhenModeChange(WhenMode.Scheduled)

        viewModel.onDaySelected(today.plusDays(2))

        assertEquals(listOf(today, today.plusDays(2)), form.slotDays)
    }

    @Test
    fun currentLocationIsUsedWhenItResolves() {
        create(categoryId = "1")
        viewModel.onProblemSelected("Sin agua")

        viewModel.onUseCurrentLocation()
        assertTrue(state.currentAddressFailed)
        assertEquals(AddressChoice.Saved("14"), state.addressChoice)

        currentAddress = CurrentAddress("Av. Juárez", "10", "Centro", "Cuauhtémoc", "CDMX", "06000", Coordinates(19.43, -99.13))
        viewModel.onUseCurrentLocation()
        viewModel.send()

        assertEquals(AddressChoice.Current, state.addressChoice)
        assertNull(form.drafts.single().savedAddressId)
        assertEquals("06000", form.drafts.single().currentAddress?.postalCode)
    }

    @Test
    fun anAddressAddedFromHereGetsChosen() {
        create(categoryId = "1")
        val new = Address("16", "Mamá", "Insurgentes", "5", "", "Roma", "06700", "Cuauhtémoc", "CDMX", "", isMain = false)

        account.list.value = listOf(work, home, new)

        assertEquals(AddressChoice.Saved("16"), state.addressChoice)
    }
}
