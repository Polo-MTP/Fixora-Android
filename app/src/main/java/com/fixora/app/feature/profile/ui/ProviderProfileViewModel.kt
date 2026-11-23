package com.fixora.app.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.session.Roles
import com.fixora.app.core.session.SessionRepository
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.PersonalData
import com.fixora.app.feature.profile.data.ProfileState
import com.fixora.app.feature.profile.data.TechnicianProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderProfileUiState(
    val email: String = "",
    val role: String = "",
    val fullName: String? = null,
    val profile: ProfileState = ProfileState.Loading,
) {
    private val normalizedRole get() = role.trim().lowercase()

    /** La API deja registrar trabajos al dueño (proveedor) y al gerente de la sucursal. */
    val canManageWorks get() = normalizedRole == Roles.PROVIDER || normalizedRole == Roles.MANAGER

    /** Los certificados son del proveedor dueño: los demás roles no tienen solicitud de proveedor. */
    val canManageCertificates get() = normalizedRole == Roles.PROVIDER
}

/** Pestaña «Perfil» del proveedor, gerente y técnico (ANDROID-7). */
@HiltViewModel
class ProviderProfileViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    accountRepository: AccountRepository,
    private val profileRepository: TechnicianProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<ProviderProfileUiState> = combine(
        sessionRepository.session,
        // El nombre llega aparte; mientras tanto se muestra el correo.
        accountRepository.personalData.map<PersonalData, PersonalData?> { it }.onStart { emit(null) },
        profileRepository.state,
    ) { session, personal, profile ->
        ProviderProfileUiState(
            email = session?.email.orEmpty(),
            role = session?.role.orEmpty(),
            fullName = personal?.fullName?.takeIf { it.isNotBlank() },
            profile = profile,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderProfileUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { profileRepository.refresh() }
    }

    // Al borrarse la sesión, FixoraApp regresa a la landing; aquí no hace falta navegar.
    fun logout() {
        viewModelScope.launch { sessionRepository.logout() }
    }
}
