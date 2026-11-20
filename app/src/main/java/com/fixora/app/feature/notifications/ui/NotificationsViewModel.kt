package com.fixora.app.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.notifications.data.NotificationsRepository
import com.fixora.app.feature.notifications.domain.AppNotification
import com.fixora.app.navigation.notificationRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val items: List<AppNotification> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 0,
    val error: ApiError? = null,
    val unreadCount: Int = 0,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && items.isEmpty()
}

/** Centro de notificaciones (ANDROID-14), igual para cliente y proveedor. */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val _open = Channel<Any>(Channel.BUFFERED)

    /** Pantalla a abrir al tocar una notificación (la de su solicitud o servicio). */
    val open: Flow<Any> = _open.receiveAsFlow()

    init {
        load(refreshing = false)
    }

    fun refresh() = load(refreshing = true)

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore || state.isLoading) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = repository.page(state.page + 1)) {
                is ApiResult.Success -> _uiState.update { current ->
                    val known = current.items.map { it.id }.toSet()
                    current.copy(
                        isLoadingMore = false,
                        items = current.items + result.data.items.filterNot { it.id in known },
                        page = result.data.page,
                        hasMore = result.data.hasMore,
                        unreadCount = result.data.unreadCount,
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /** La marca como leída y, si tiene a dónde llevar, lo abre. */
    fun onOpen(notification: AppNotification) {
        notificationRoute(notification.asPushMessage())?.let { _open.trySend(it) }
        if (notification.isRead) return
        setRead(notification.id)
        viewModelScope.launch { repository.markRead(notification.id) }
    }

    fun markAllRead() {
        if (_uiState.value.unreadCount == 0) return
        val previous = _uiState.value.items
        _uiState.update { state -> state.copy(items = state.items.map { it.copy(isRead = true) }, unreadCount = 0) }
        viewModelScope.launch {
            // Si falla, se regresa como estaba para no mostrar algo que la API no guardó.
            if (repository.markAllRead() is ApiResult.Failure) {
                _uiState.update { it.copy(items = previous, unreadCount = previous.count { n -> !n.isRead }) }
            }
        }
    }

    private fun setRead(id: String) = _uiState.update { state ->
        state.copy(
            items = state.items.map { if (it.id == id) it.copy(isRead = true) else it },
            unreadCount = (state.unreadCount - 1).coerceAtLeast(0),
        )
    }

    private fun load(refreshing: Boolean) {
        _uiState.update { it.copy(isLoading = !refreshing && it.items.isEmpty(), isRefreshing = refreshing, error = null) }
        viewModelScope.launch {
            when (val result = repository.page(1)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        items = result.data.items,
                        page = 1,
                        hasMore = result.data.hasMore,
                        unreadCount = result.data.unreadCount,
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = result.error) }
            }
        }
    }
}

/** Campana de los inicios: cuántas no leídas hay. */
@HiltViewModel
class NotificationBellViewModel @Inject constructor(
    private val repository: NotificationsRepository,
) : ViewModel() {
    val unreadCount: StateFlow<Int> = repository.unreadCount

    /** Al volver al inicio se cuenta otra vez (pudieron llegar avisos con la app cerrada). */
    fun refresh() {
        viewModelScope.launch { repository.refreshUnread() }
    }
}
