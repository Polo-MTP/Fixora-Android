package com.fixora.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.fixora.app.BuildConfig
import com.fixora.app.feature.auth.navigation.ForgotPasswordRoute
import com.fixora.app.feature.auth.navigation.LandingRoute
import com.fixora.app.feature.auth.navigation.LoginRoute
import com.fixora.app.feature.auth.navigation.RegisterRoute
import com.fixora.app.feature.auth.navigation.RegisterSuccessRoute
import com.fixora.app.feature.auth.navigation.forgotPasswordScreen
import com.fixora.app.feature.auth.navigation.landingScreen
import com.fixora.app.feature.auth.navigation.loginScreen
import com.fixora.app.feature.auth.navigation.registerScreen
import com.fixora.app.feature.auth.navigation.registerSuccessScreen

private const val TRANSITION_MS = 300

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
        enterTransition = { fadeIn(tween(TRANSITION_MS)) },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) },
    ) {
        landingScreen(
            onStartClick = { navController.navigate(LoginRoute) },
            onBecomeProviderClick = { uriHandler.openIfConfigured(BuildConfig.PROVIDER_SIGNUP_URL) },
        )
        loginScreen(
            onBack = navController::popBackStack,
            onForgotPassword = { navController.navigate(ForgotPasswordRoute) },
            onRegister = { navController.navigate(RegisterRoute) },
            // TODO(ANDROID-4): iniciar sesión con la API y entrar al inicio según el rol.
            onSubmit = {},
        )
        forgotPasswordScreen(
            onBack = navController::popBackStack,
            onLogin = navController::popBackStack,
            // TODO(ANDROID-4): pedir a la API el código de recuperación.
            onSubmit = {},
        )
        registerScreen(
            onExit = navController::popBackStack,
            onLogin = { navController.popBackStack<LoginRoute>(inclusive = false) },
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
