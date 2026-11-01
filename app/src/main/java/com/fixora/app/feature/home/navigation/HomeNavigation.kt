package com.fixora.app.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.home.ui.ClientHomeScreen
import com.fixora.app.feature.home.ui.ProviderHomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object ClientHomeRoute

@Serializable
data object ProviderHomeRoute

fun NavGraphBuilder.clientHomeScreen() {
    composable<ClientHomeRoute> { ClientHomeScreen() }
}

fun NavGraphBuilder.providerHomeScreen() {
    composable<ProviderHomeRoute> { ProviderHomeScreen() }
}
