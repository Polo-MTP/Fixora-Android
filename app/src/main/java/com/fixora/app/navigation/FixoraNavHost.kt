package com.fixora.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.fixora.app.BuildConfig
import com.fixora.app.core.ui.slideBackwardEnter
import com.fixora.app.core.ui.slideBackwardExit
import com.fixora.app.core.ui.slideForwardEnter
import com.fixora.app.core.ui.slideForwardExit
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
import com.fixora.app.feature.home.navigation.HomeRoute
import com.fixora.app.feature.home.navigation.homeScreen

/**
 * @param isLoggedIn decide la pantalla inicial y, cuando cambia, a dónde ir: al iniciar sesión se
 * entra al inicio; al cerrarla (o si la API rechaza la sesión) se regresa a la landing. En ambos
 * casos se limpia el historial para que «atrás» no regrese a la sesión anterior.
 */
@Composable
fun FixoraNavHost(
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val uriHandler = LocalUriHandler.current
    val startDestination = remember { if (isLoggedIn) HomeRoute else LandingRoute }

    LaunchedEffect(isLoggedIn) {
        val target: Any = if (isLoggedIn) HomeRoute else LandingRoute
        if (navController.currentDestination?.hasRoute(target::class) == true) return@LaunchedEffect
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { slideForwardEnter() },
        exitTransition = { slideForwardExit() },
        popEnterTransition = { slideBackwardEnter() },
        popExitTransition = { slideBackwardExit() },
    ) {
        landingScreen(
            onStartClick = { navController.navigate(LoginRoute) },
            onBecomeProviderClick = { uriHandler.openIfConfigured(BuildConfig.PROVIDER_SIGNUP_URL) },
        )
        loginScreen(
            onBack = navController::popBackStack,
            onForgotPassword = { navController.navigate(ForgotPasswordRoute) },
            onRegister = { navController.navigate(RegisterRoute) },
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
        homeScreen()
    }
}

// Las URL vienen de app/env/<flavor>.properties; si están vacías (aún sin desplegar) no se abre nada.
private fun UriHandler.openIfConfigured(url: String) {
    if (url.isBlank()) return
    runCatching { openUri(url) }
}
