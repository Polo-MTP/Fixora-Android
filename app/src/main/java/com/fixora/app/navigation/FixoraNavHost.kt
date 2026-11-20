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
import com.fixora.app.core.notifications.PushMessage
import com.fixora.app.core.session.HomeArea
import com.fixora.app.core.ui.slideBackwardEnter
import com.fixora.app.core.ui.slideBackwardExit
import com.fixora.app.core.ui.slideForwardEnter
import com.fixora.app.core.ui.slideForwardExit
import com.fixora.app.feature.account.navigation.AccountPhotoRoute
import com.fixora.app.feature.account.navigation.AddressFormRoute
import com.fixora.app.feature.account.navigation.AddressesRoute
import com.fixora.app.feature.account.navigation.ChangePasswordRoute
import com.fixora.app.feature.account.navigation.PersonalDataRoute
import com.fixora.app.feature.account.navigation.accountScreens
import com.fixora.app.feature.auth.navigation.ForgotPasswordRoute
import com.fixora.app.feature.auth.navigation.LandingRoute
import com.fixora.app.feature.auth.navigation.LoginRoute
import com.fixora.app.feature.auth.navigation.RegisterRoute
import com.fixora.app.feature.auth.navigation.forgotPasswordScreen
import com.fixora.app.feature.auth.navigation.landingScreen
import com.fixora.app.feature.auth.navigation.loginScreen
import com.fixora.app.feature.auth.navigation.registerScreen
import com.fixora.app.feature.home.navigation.ClientHomeRoute
import com.fixora.app.feature.home.navigation.ProviderHomeRoute
import com.fixora.app.feature.home.navigation.clientHomeScreen
import com.fixora.app.feature.home.navigation.providerHomeScreen
import com.fixora.app.feature.portfolio.navigation.AddWorkRoute
import com.fixora.app.feature.portfolio.navigation.CertificatesRoute
import com.fixora.app.feature.portfolio.navigation.PortfolioRoute
import com.fixora.app.feature.portfolio.navigation.portfolioScreens
import com.fixora.app.feature.profile.navigation.TechnicianPhotoRoute
import com.fixora.app.feature.providerprofile.navigation.ProviderProfileRoute
import com.fixora.app.feature.providerprofile.navigation.providerProfileScreen
import com.fixora.app.feature.profile.navigation.VehicleRoute
import com.fixora.app.feature.profile.navigation.profileScreens

/**
 * @param homeArea inicio que le toca a la sesión según el rol (null = sin sesión). Decide la
 * pantalla inicial y, cuando cambia, a dónde ir: al iniciar sesión se entra al inicio del cliente
 * o del proveedor; al cerrarla (o si la API rechaza la sesión) se regresa a la landing. En ambos
 * casos se limpia el historial para que «atrás» no regrese a la sesión anterior.
 * @param openedNotification notificación con la que se abrió la app; se resuelve con
 * [notificationRoute] y luego se avisa con [onNotificationHandled] para no repetirla.
 */
@Composable
fun FixoraNavHost(
    homeArea: HomeArea?,
    modifier: Modifier = Modifier,
    openedNotification: PushMessage? = null,
    onNotificationHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val uriHandler = LocalUriHandler.current
    val startDestination = remember { homeRoute(homeArea) }

    LaunchedEffect(homeArea) {
        val target = homeRoute(homeArea)
        if (navController.currentDestination?.hasRoute(target::class) == true) return@LaunchedEffect
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    LaunchedEffect(openedNotification, homeArea) {
        val message = openedNotification ?: return@LaunchedEffect
        // Sin sesión no hay a dónde llevar: se queda en la landing.
        if (homeArea != null) notificationRoute(message)?.let { navController.navigate(it) }
        onNotificationHandled()
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
        )
        registerScreen(
            onExit = navController::popBackStack,
            onLogin = { navController.popBackStack<LoginRoute>(inclusive = false) },
        )
        clientHomeScreen(
            onOpenProvider = { branchId, distanceKm, categoryId ->
                navController.navigate(ProviderProfileRoute(branchId, distanceKm, categoryId))
            },
            onOpenPhoto = { navController.navigate(AccountPhotoRoute) },
            onOpenPersonalData = { navController.navigate(PersonalDataRoute) },
            onOpenAddresses = { navController.navigate(AddressesRoute) },
            onOpenChangePassword = { navController.navigate(ChangePasswordRoute) },
        )
        providerHomeScreen(
            onOpenPhoto = { navController.navigate(TechnicianPhotoRoute) },
            onOpenVehicle = { navController.navigate(VehicleRoute) },
            onOpenPortfolio = { navController.navigate(PortfolioRoute) },
            onOpenCertificates = { navController.navigate(CertificatesRoute) },
            onOpenPersonalData = { navController.navigate(PersonalDataRoute) },
            onOpenChangePassword = { navController.navigate(ChangePasswordRoute) },
        )
        accountScreens(
            onBack = navController::popBackStack,
            onAddAddress = { navController.navigate(AddressFormRoute()) },
            onEditAddress = { navController.navigate(AddressFormRoute(it)) },
        )
        profileScreens(onBack = navController::popBackStack)
        portfolioScreens(
            onBack = navController::popBackStack,
            onAddWork = { navController.navigate(AddWorkRoute) },
        )
        providerProfileScreen(onBack = navController::popBackStack)
    }
}

// Las URL vienen de app/env/<flavor>.properties; si están vacías (aún sin desplegar) no se abre nada.
private fun UriHandler.openIfConfigured(url: String) {
    if (url.isBlank()) return
    runCatching { openUri(url) }
}

private fun homeRoute(area: HomeArea?): Any = when (area) {
    HomeArea.Client -> ClientHomeRoute
    HomeArea.Provider -> ProviderHomeRoute
    null -> LandingRoute
}
