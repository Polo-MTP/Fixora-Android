package com.fixora.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.fixora.app.BuildConfig
import com.fixora.app.core.ui.slideBackwardEnter
import com.fixora.app.core.ui.slideBackwardExit
import com.fixora.app.core.ui.slideForwardEnter
import com.fixora.app.core.ui.slideForwardExit
import com.fixora.app.feature.auth.navigation.LandingRoute
import com.fixora.app.feature.auth.navigation.RegisterRoute
import com.fixora.app.feature.auth.navigation.RegisterSuccessRoute
import com.fixora.app.feature.auth.navigation.landingScreen
import com.fixora.app.feature.auth.navigation.registerScreen
import com.fixora.app.feature.auth.navigation.registerSuccessScreen

@Composable
fun FixoraNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val uriHandler = LocalUriHandler.current

    NavHost(
        navController = navController,
        startDestination = LandingRoute,
        modifier = modifier,
        enterTransition = { slideForwardEnter() },
        exitTransition = { slideForwardExit() },
        popEnterTransition = { slideBackwardEnter() },
        popExitTransition = { slideBackwardExit() },
    ) {
        landingScreen(
            // TODO(ANDROID-4): abrir el login cuando exista; desde ahí se llegará al registro.
            onStartClick = { navController.navigate(RegisterRoute) },
            onBecomeProviderClick = { uriHandler.openIfConfigured(BuildConfig.PROVIDER_SIGNUP_URL) },
        )
        registerScreen(
            onExit = navController::popBackStack,
            // TODO(ANDROID-3): crear la cuenta en la API antes de mostrar la confirmación.
            onRegistered = {
                // Al terminar, «atrás» ya no regresa al formulario sino a la landing.
                navController.navigate(RegisterSuccessRoute) {
                    popUpTo<LandingRoute>()
                }
            },
        )
        registerSuccessScreen(
            // Por ahora regresa a la landing; luego irá al inicio del cliente.
            onContinue = navController::popBackStack,
        )
    }
}

// Las URL vienen de app/env/<flavor>.properties; si están vacías (aún sin desplegar) no se abre nada.
private fun UriHandler.openIfConfigured(url: String) {
    if (url.isBlank()) return
    runCatching { openUri(url) }
}
