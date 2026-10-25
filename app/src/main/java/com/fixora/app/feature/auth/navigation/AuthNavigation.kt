package com.fixora.app.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.auth.ui.landing.LandingScreen
import com.fixora.app.feature.auth.ui.register.RegisterScreen
import com.fixora.app.feature.auth.ui.register.RegisterSuccessScreen
import kotlinx.serialization.Serializable

@Serializable
data object LandingRoute

@Serializable
data object RegisterRoute

@Serializable
data object RegisterSuccessRoute

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

fun NavGraphBuilder.registerScreen(
    onExit: () -> Unit,
    onRegistered: () -> Unit,
) {
    composable<RegisterRoute> {
        RegisterScreen(onExit = onExit, onRegistered = onRegistered)
    }
}

fun NavGraphBuilder.registerSuccessScreen(onContinue: () -> Unit) {
    composable<RegisterSuccessRoute> {
        RegisterSuccessScreen(onContinue = onContinue)
    }
}
