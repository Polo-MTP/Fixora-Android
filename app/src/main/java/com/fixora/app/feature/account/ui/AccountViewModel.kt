package com.fixora.app.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.session.SessionRepository
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.domain.PersonalData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val email: String = "",
    val personal: PersonalData? = null,
    val mainAddress: Address? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    val uiState: StateFlow<AccountUiState> = combine(
        sessionRepository.session,
        accountRepository.personalData,
        accountRepository.addresses,
    ) { session, personal, addresses ->
        AccountUiState(
            email = session?.email.orEmpty(),
            personal = personal,
            mainAddress = addresses.firstOrNull { it.isMain },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    // Al borrarse la sesión, FixoraApp regresa a la landing; aquí no hace falta navegar.
    fun logout() {
        viewModelScope.launch { sessionRepository.logout() }
    }
}
