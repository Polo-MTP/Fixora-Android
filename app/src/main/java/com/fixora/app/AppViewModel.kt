package com.fixora.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ConnectivityObserver
import com.fixora.app.core.session.HomeArea
import com.fixora.app.core.session.Roles
import com.fixora.app.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState

    /** Hay sesión; [area] dice a qué inicio va según el rol. */
    data class LoggedIn(val area: HomeArea) : SessionState
}

/** Estado global de la app: si hay sesión iniciada (y de qué rol) y si hay internet. */
@HiltViewModel
class AppViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    // Una sesión de un rol sin app (administrador, soporte) cuenta como sin sesión; el login ya
    // no las guarda, pero así una guardada antes no deja la app en un estado sin pantalla.
    val sessionState: StateFlow<SessionState> = sessionRepository.session
        .map { session ->
            session?.let { Roles.homeArea(it.role) }?.let(SessionState::LoggedIn) ?: SessionState.LoggedOut
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    // Arranca en true para no mostrar el aviso un instante mientras se consulta la red.
    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
