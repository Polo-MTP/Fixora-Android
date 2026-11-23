package com.fixora.app.feature.notifications.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.SystemBarsAppearance
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.notifications.domain.AppNotification
import com.fixora.app.feature.notifications.domain.NotificationKind
import com.fixora.app.feature.notifications.domain.WhenLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Centro de notificaciones (ANDROID-14): las más recientes primero; al tocar una se abre. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpen: (route: Any) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.open.collect(onOpen) }
    NotificationsContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onOpen = viewModel::onOpen,
        onMarkAllRead = viewModel::markAllRead,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsContent(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    SystemBarsAppearance(lightIcons = false)
    Column(
        Modifier
            .fillMaxSize()
            .background(White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            BackButton(onBack)
            Text(
                stringResource(R.string.notifications_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Navy900,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        // En su propio renglón para que el título no se parta con letra grande.
        if (state.unreadCount > 0) {
            FixoraTextLink(
                text = stringResource(R.string.notifications_mark_all),
                onClick = onMarkAllRead,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
        }

        val listState = rememberLazyListState()
        val nearEnd by remember {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= listState.layoutInfo.totalItemsCount - 3
            }
        }
        LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) onLoadMore() }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            val error = state.error
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                error != null && state.items.isEmpty() -> Column(Modifier.padding(20.dp)) {
                    LoadErrorNotice(error, onRetry = onRefresh)
                }
                state.isEmpty -> EmptyNotifications()
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { notification ->
                        NotificationRow(notification, onClick = { onOpen(notification) })
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    val unreadLabel = stringResource(R.string.notifications_unread)
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (notification.isRead) White else Orange500.copy(alpha = 0.05f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (!notification.isRead) contentDescription = unreadLabel }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .background(Mist50, CircleShape)
                .border(1.dp, Mist200, CircleShape),
        ) {
            Icon(painterResource(notification.kind.icon()), contentDescription = null, tint = Navy800, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    notification.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    ),
                    color = Navy900,
                    modifier = Modifier.weight(1f),
                )
                notification.createdAt?.let {
                    Text(
                        whenText(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
                }
            }
            if (notification.message.isNotEmpty()) {
                Text(notification.message, style = MaterialTheme.typography.bodySmall, color = Slate500)
            }
        }
        if (!notification.isRead) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(Orange500, CircleShape),
            )
        }
    }
}

@DrawableRes
private fun NotificationKind.icon(): Int = when (this) {
    NotificationKind.Service -> R.drawable.ic_nav_activity
    NotificationKind.Account -> R.drawable.ic_certificate
    NotificationKind.Report -> R.drawable.ic_info
    NotificationKind.Other -> R.drawable.ic_bell
}

private val DateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-MX"))

@Composable
private fun whenText(createdAt: Instant): String = when (val label = WhenLabel.of(createdAt, Instant.now(), ZoneId.systemDefault())) {
    WhenLabel.Now -> stringResource(R.string.notifications_when_now)
    is WhenLabel.Minutes -> stringResource(R.string.notifications_when_minutes, label.value)
    is WhenLabel.Hours -> stringResource(R.string.notifications_when_hours, label.value)
    WhenLabel.Yesterday -> stringResource(R.string.notifications_when_yesterday)
    is WhenLabel.Date -> DateFormat.format(label.date)
}

@Composable
private fun EmptyNotifications() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(Mist50, CircleShape),
        ) {
            Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = Slate500, modifier = Modifier.size(28.dp))
        }
        Text(stringResource(R.string.notifications_empty_title), style = MaterialTheme.typography.titleMedium, color = Navy900)
        Text(
            stringResource(R.string.notifications_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(60.dp))
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .border(1.dp, Mist200, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = stringResource(R.string.auth_back),
            tint = Navy800,
            modifier = Modifier
                .size(20.dp)
                .rotate(180f),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NotificationsPreview() {
    val now = Instant.now()
    FixoraTheme {
        NotificationsContent(
            state = NotificationsUiState(
                isLoading = false,
                unreadCount = 1,
                items = listOf(
                    AppNotification("3", "solicitud_aceptada", "Actualización de Solicitud #23", "¡Un profesional ha aceptado tu solicitud de servicio!", "service_request", "23", false, now.minusSeconds(300)),
                    AppNotification("2", "verificacion_proveedor", "Tu cuenta fue aprobada", "Ya puedes recibir solicitudes.", "supplier", "4", true, now.minusSeconds(90_000)),
                ),
            ),
            onBack = {}, onRefresh = {}, onLoadMore = {}, onOpen = {}, onMarkAllRead = {},
        )
    }
}
