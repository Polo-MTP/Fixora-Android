package com.fixora.app.feature.account.ui.address

import androidx.lifecycle.SavedStateHandle
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.PersonalData
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.GeocodedAddress
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.map.domain.MapRules
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** El punto en el mapa de la dirección (ANDROID-41). */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressFormViewModelTest {

    private class FakeAccount : AccountRepository {
        val list = MutableStateFlow<List<Address>>(emptyList())
        val saved = mutableListOf<Address>()
        override val personalData: Flow<PersonalData> = emptyFlow()
        override val addresses: Flow<List<Address>> = list
        override val personalDataError: Flow<ApiError?> = emptyFlow()
        override val addressesError: Flow<ApiError?> = emptyFlow()
        override suspend fun refreshPersonalData() = ApiResult.Success(Unit)
        override suspend fun refreshAddresses() = ApiResult.Success(Unit)
        override suspend fun updatePersonalData(data: PersonalData) = ApiResult.Success(Unit)
        override suspend fun saveAddress(address: Address): ApiResult<Unit> {
            saved += address
            return ApiResult.Success(Unit)
        }
        override suspend fun deleteAddress(id: String) = ApiResult.Success(Unit)
        override suspend fun setMainAddress(id: String) = ApiResult.Success(Unit)
        override suspend fun changePassword(current: String, new: String) = ApiResult.Success(Unit)
        override suspend fun updatePhoto(photo: ByteArray?) = ApiResult.Success(Unit)
    }

    private class FakeLocation(var here: Coordinates? = null) : LocationSource {
        override fun hasPermission() = here != null
        override suspend fun current() = here
        override fun updates(intervalMs: Long): Flow<Coordinates> = emptyFlow()
    }

    private val torreon = Coordinates(25.5428, -103.4068)
    private val home = Address("7", "Casa", "Morelos", "10", "", "Centro", "27000", "Torreón", "Coahuila", "", isMain = true, location = torreon)

    private lateinit var account: FakeAccount
    private lateinit var location: FakeLocation
    private var found: GeocodedAddress? = null
    private val lookedUp = mutableListOf<Coordinates>()
    private lateinit var viewModel: AddressFormViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        account = FakeAccount()
        location = FakeLocation()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun create(addressId: String? = null) {
        viewModel = AddressFormViewModel(SavedStateHandle(mapOf("addressId" to addressId)), account, location) { at ->
            lookedUp += at
            found
        }
    }

    private fun fillTheAddress() {
        viewModel.onChange(AddressField.Alias, "Casa")
        viewModel.onChange(AddressField.Street, "Morelos")
        viewModel.onChange(AddressField.NumExt, "10")
        viewModel.onChange(AddressField.Colony, "Centro")
        viewModel.onChange(AddressField.PostalCode, "27000")
        viewModel.onChange(AddressField.Municipality, "Torreón")
        viewModel.onChange(AddressField.State, "Coahuila")
    }

    @Test
    fun withoutThePointOnTheMapItIsNotSaved() {
        create()
        fillTheAddress()

        viewModel.save()

        assertTrue(state.locationMissing)
        assertTrue(account.saved.isEmpty())
    }

    @Test
    fun theMapOpensWhereThePhoneIsAndTheConfirmedPointIsSaved() {
        location.here = torreon
        create()
        fillTheAddress()

        viewModel.openPicker()
        val picker = state.picker!!
        assertEquals(MapRules.DEFAULT_CENTER, picker.start)
        assertEquals(torreon, picker.recenter)

        val door = Coordinates(25.5431, -103.4072)
        viewModel.onLocationConfirmed(door)
        assertNull(state.picker)
        viewModel.save()

        assertEquals(door, account.saved.single().location)
    }

    @Test
    fun thePointFillsTheAddressAndAsksToReviewIt() {
        found = GeocodedAddress(
            street = "Av. Morelos", number = "450", colony = "Centro", city = "Torreón",
            municipality = "Torreón", state = "Coahuila", postalCode = "27000",
        )
        create()

        viewModel.onLocationConfirmed(torreon)

        assertEquals(listOf(torreon), lookedUp)
        assertEquals("Av. Morelos", state[AddressField.Street].value)
        assertEquals("450", state[AddressField.NumExt].value)
        assertEquals("Centro", state[AddressField.Colony].value)
        assertEquals("27000", state[AddressField.PostalCode].value)
        assertEquals("Coahuila", state[AddressField.State].value)
        assertEquals("", state[AddressField.Alias].value)
        assertTrue(state.showReviewNotice)
    }

    @Test
    fun movingThePointKeepsWhatTheUserCorrected() {
        found = GeocodedAddress(street = "Av. Morelos", number = "450", colony = "", municipality = "Torreón", state = "Coahuila", postalCode = "27000")
        create()
        viewModel.onLocationConfirmed(torreon)
        viewModel.onChange(AddressField.NumExt, "452")
        viewModel.onChange(AddressField.Colony, "Primero de Cobián")

        found = GeocodedAddress(street = "Calle Juárez", number = "300", colony = "Centro", municipality = "Torreón", state = "Coahuila", postalCode = "27010")
        viewModel.onLocationConfirmed(Coordinates(25.5401, -103.4101))

        // La calle y el C.P. los puso el mapa: se actualizan. El número y la colonia los corrigió el usuario.
        assertEquals("Calle Juárez", state[AddressField.Street].value)
        assertEquals("27010", state[AddressField.PostalCode].value)
        assertEquals("452", state[AddressField.NumExt].value)
        assertEquals("Primero de Cobián", state[AddressField.Colony].value)
    }

    @Test
    fun ifTheMapDoesNotKnowTheAddressTheFormStaysAsItWas() {
        create()
        viewModel.onChange(AddressField.Street, "Morelos")

        viewModel.onLocationConfirmed(torreon)

        assertEquals(torreon, state.location)
        assertEquals("Morelos", state[AddressField.Street].value)
        assertFalse(state.isLookingUp)
        assertFalse(state.showReviewNotice)
    }

    @Test
    fun withoutPermissionTheMapStaysOnTheCityToSearchByHand() {
        create()

        viewModel.openPicker()

        assertEquals(LocationPicker.CITY_ZOOM, state.picker!!.zoom, 0.0)
        assertNull(state.picker!!.recenter)
    }

    @Test
    fun editingOpensTheMapOnTheMarkedPoint() {
        account.list.value = listOf(home)
        create(addressId = "7")

        assertEquals(torreon, state.location)
        viewModel.openPicker()

        assertEquals(torreon, state.picker!!.start)
        assertEquals(LocationPicker.PRECISE_ZOOM, state.picker!!.zoom, 0.0)
        viewModel.closePicker()
        viewModel.save()
        assertFalse(state.locationMissing)
        assertEquals(torreon, account.saved.single().location)
    }
}
