package com.fixora.app.feature.account.ui.photo

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.PersonalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Con un servicio en curso la foto no se cambia (API-55). */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountPhotoViewModelTest {

    private class FakeAccount : AccountRepository {
        val personal = MutableStateFlow<PersonalData?>(null)
        var refreshes = 0
        val photos = mutableListOf<ByteArray?>()
        override val personalData: Flow<PersonalData> = personal.filterNotNull()
        override val addresses: Flow<List<Address>> = emptyFlow()
        override val personalDataError: Flow<ApiError?> = emptyFlow()
        override val addressesError: Flow<ApiError?> = emptyFlow()
        override suspend fun refreshPersonalData(): ApiResult<Unit> {
            refreshes++
            return ApiResult.Success(Unit)
        }
        override suspend fun refreshAddresses() = ApiResult.Success(Unit)
        override suspend fun updatePersonalData(data: PersonalData) = ApiResult.Success(Unit)
        override suspend fun saveAddress(address: Address) = ApiResult.Success(Unit)
        override suspend fun deleteAddress(id: String) = ApiResult.Success(Unit)
        override suspend fun setMainAddress(id: String) = ApiResult.Success(Unit)
        override suspend fun changePassword(current: String, new: String) = ApiResult.Success(Unit)
        override suspend fun updatePhoto(photo: ByteArray?): ApiResult<Unit> {
            photos += photo
            return ApiResult.Success(Unit)
        }
    }

    private val ana = PersonalData("Ana", "Pérez", "", null, "", photoUrl = "https://f/ana.jpg")
    private lateinit var account: FakeAccount
    private lateinit var viewModel: AccountPhotoViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        account = FakeAccount()
        viewModel = AccountPhotoViewModel(account) { null }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opening_asksAgainIfThereIsAServiceInProgress() {
        assertEquals(1, account.refreshes)
    }

    @Test
    fun withAServiceInProgress_thePhotoIsNotRemoved() {
        account.personal.value = ana.copy(activeService = true)

        viewModel.remove()
        viewModel.save()

        assertTrue(state.locked)
        assertTrue(account.photos.isEmpty())
        assertFalse(state.isSaved)
    }

    @Test
    fun whenTheServiceEnds_thePhotoCanBeChangedAgain() {
        account.personal.value = ana.copy(activeService = true)
        account.personal.value = ana

        viewModel.remove()
        viewModel.save()

        assertFalse(state.locked)
        assertEquals(listOf<ByteArray?>(null), account.photos)
    }
}
