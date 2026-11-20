package com.fixora.app.feature.notifications.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.notifications.ui.NotificationsScreen
import kotlinx.serialization.Serializable

/** Centro de notificaciones (ANDROID-14); se abre con la campana de los dos inicios. */
@Serializable
data object NotificationsRoute

fun NavGraphBuilder.notificationsScreen(onBack: () -> Unit, onOpen: (route: Any) -> Unit) {
    composable<NotificationsRoute> { NotificationsScreen(onBack = onBack, onOpen = onOpen) }
}
