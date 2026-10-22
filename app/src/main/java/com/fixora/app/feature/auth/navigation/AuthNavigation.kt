package com.fixora.app.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.auth.ui.landing.LandingScreen
import kotlinx.serialization.Serializable

@Serializable
data object LandingRoute

fun NavGraphBuilder.landingScreen(
    onStartClick: () -> Unit,
    onBecomeProviderClick: () -> Unit,
) {
    composable<LandingRoute> {
        LandingScreen(
            onStartClick = onStartClick,
            onBecomeProviderClick = onBecomeProviderClick,
        )
    }
}
