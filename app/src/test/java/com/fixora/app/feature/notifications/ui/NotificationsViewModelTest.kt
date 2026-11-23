package com.fixora.app.feature.notifications.ui

import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.notifications.data.NotificationsRepository
import com.fixora.app.feature.notifications.domain.AppNotification
import com.fixora.app.feature.notifications.domain.NotificationsPage
import com.fixora.app.feature.servicerequest.navigation.ServiceRequestRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private class FakeRepository : NotificationsRepository {
        val pages = mutableMapOf<Int, NotificationsPage>()
        val read = mutableListOf<String>()
        var readAll = 0
        var failReadAll = false
        override val unreadCount: StateFlow<Int> = MutableStateFlow(0)

        override suspend fun page(page: Int): ApiResult<NotificationsPage> =
            pages[page]?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiError.NoConnection)

        override suspend fun refreshUnread(): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun markRead(id: String): ApiResult<Unit> {
            read += id
            return ApiResult.Success(Unit)
        }

        override suspend fun markAllRead(): ApiResult<Unit> {
            readAll++
            return if (failReadAll) ApiResult.Failure(ApiError.NoConnection) else ApiResult.Success(Unit)
        }
    }

    private fun notification(id: String, read: Boolean = false, entityType: String? = "service_request") = AppNotification(
        id = id, type = "solicitud_aceptada", title = "Solicitud $id", message = "", entityType = entityType,
        entityId = if (entityType != null) "2$id" else null, isRead = read, createdAt = null,
    )

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: NotificationsViewModel
    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeRepository()
        repository.pages[1] = NotificationsPage(listOf(notification("3"), notification("2", read = true)), unreadCount = 1, page = 1, hasMore = true)
        repository.pages[2] = NotificationsPage(listOf(notification("2", read = true), notification("1", entityType = null)), unreadCount = 2, page = 2, hasMore = false)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsTheFirstPage() {
        viewModel = NotificationsViewModel(repository)

        assertEquals(listOf("3", "2"), state.items.map { it.id })
        assertEquals(1, state.unreadCount)
        assertTrue(state.hasMore)
        assertFalse(state.isLoading)
    }

    @Test
    fun moreAreAddedWithoutRepeating() {
        viewModel = NotificationsViewModel(repository)

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(listOf("3", "2", "1"), state.items.map { it.id })
        assertFalse(state.hasMore)
    }

    @Test
    fun openingAServiceNotificationReadsItAndOpensTheRequest() = runBlocking {
        viewModel = NotificationsViewModel(repository)

        viewModel.onOpen(state.items.first())

        assertEquals(ServiceRequestRoute("23"), withTimeout(1_000) { viewModel.open.first() })
        assertEquals(listOf("3"), repository.read)
        assertTrue(state.items.first().isRead)
        assertEquals(0, state.unreadCount)
    }

    @Test
    fun openingAnAlreadyReadOneDoesNotCallTheApi() {
        viewModel = NotificationsViewModel(repository)

        viewModel.onOpen(state.items[1])

        assertTrue(repository.read.isEmpty())
    }

    @Test
    fun markAllReadAndUndoIfItFails() {
        viewModel = NotificationsViewModel(repository)
        viewModel.markAllRead()
        assertEquals(0, state.unreadCount)
        assertTrue(state.items.all { it.isRead })

        repository.pages[1] = NotificationsPage(listOf(notification("4")), unreadCount = 1, page = 1, hasMore = false)
        repository.failReadAll = true
        viewModel.refresh()
        viewModel.markAllRead()

        assertEquals(1, state.unreadCount)
        assertFalse(state.items.first().isRead)
        assertEquals(2, repository.readAll)
    }

    @Test
    fun withoutUnreadMarkAllDoesNothing() {
        repository.pages[1] = NotificationsPage(listOf(notification("2", read = true)), unreadCount = 0, page = 1, hasMore = false)
        viewModel = NotificationsViewModel(repository)

        viewModel.markAllRead()

        assertEquals(0, repository.readAll)
    }

    @Test
    fun errorShowsAndRefreshRecovers() {
        val saved = repository.pages.remove(1)!!
        viewModel = NotificationsViewModel(repository)
        assertEquals(ApiError.NoConnection, state.error)

        repository.pages[1] = saved
        viewModel.refresh()

        assertEquals(2, state.items.size)
        assertFalse(state.isRefreshing)
    }
}
