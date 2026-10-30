package com.fixora.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ConnectivityObserver
import com.fixora.app.core.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SessionState { Loading, LoggedOut, LoggedIn }

/** Estado global de la app: si hay sesión iniciada y si hay internet. */
@HiltViewModel
class AppViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionRepository.session
        .map { if (it != null) SessionState.LoggedIn else SessionState.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    // Arranca en true para no mostrar el aviso un instante mientras se consulta la red.
    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
