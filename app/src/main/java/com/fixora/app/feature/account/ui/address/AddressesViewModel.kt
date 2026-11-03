package com.fixora.app.feature.account.ui.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.Address
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddressesViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    // null mientras se cargan; después, la lista (puede estar vacía). La principal va primero,
    // como la verá el cliente al pedir un servicio.
    val addresses: StateFlow<List<Address>?> = accountRepository.addresses
        .map { list -> list.sortedByDescending { it.isMain } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMain(id: String) {
        viewModelScope.launch { accountRepository.setMainAddress(id) }
    }
}
