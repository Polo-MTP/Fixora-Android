package com.fixora.app.feature.chat.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.chat.ui.ChatScreen
import kotlinx.serialization.Serializable

/** Chat de una solicitud (ANDROID-23); lo abren el cliente y el técnico. */
@Serializable
data class ChatRoute(val requestId: String)

fun NavGraphBuilder.chatScreen(onBack: () -> Unit) {
    composable<ChatRoute> { ChatScreen(onBack = onBack) }
}
