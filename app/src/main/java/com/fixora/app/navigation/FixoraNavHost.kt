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
import androidx.navigation.toRoute
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
import com.fixora.app.feature.chat.navigation.ChatRoute
import com.fixora.app.feature.chat.navigation.chatScreen
import com.fixora.app.feature.auth.navigation.LandingRoute
import com.fixora.app.feature.auth.navigation.LoginRoute
import com.fixora.app.feature.auth.navigation.RegisterRoute
import com.fixora.app.feature.auth.navigation.forgotPasswordScreen
import com.fixora.app.feature.auth.navigation.landingScreen
import com.fixora.app.feature.auth.navigation.loginScreen
import com.fixora.app.feature.auth.navigation.registerScreen
import com.fixora.app.feature.emergency.navigation.EmergencyRoute
import com.fixora.app.feature.emergency.navigation.emergencyScreen
import com.fixora.app.feature.home.navigation.ClientHomeRoute
import com.fixora.app.feature.home.navigation.ProviderHomeRoute
import com.fixora.app.feature.home.navigation.clientHomeScreen
import com.fixora.app.feature.home.navigation.providerHomeScreen
import com.fixora.app.feature.notifications.navigation.NotificationsRoute
import com.fixora.app.feature.notifications.navigation.notificationsScreen
import com.fixora.app.feature.payment.navigation.DebtsRoute
import com.fixora.app.feature.payment.navigation.debtsScreen
import com.fixora.app.feature.portfolio.navigation.AddWorkRoute
import com.fixora.app.feature.portfolio.navigation.CertificatesRoute
import com.fixora.app.feature.portfolio.navigation.PortfolioRoute
import com.fixora.app.feature.portfolio.navigation.portfolioScreens
import com.fixora.app.feature.profile.navigation.TechnicianPhotoRoute
import com.fixora.app.feature.profile.navigation.VehicleRoute
import com.fixora.app.feature.profile.navigation.profileScreens
import com.fixora.app.feature.providerprofile.navigation.ProviderProfileRoute
import com.fixora.app.feature.providerprofile.navigation.providerProfileScreen
import com.fixora.app.feature.requestform.navigation.RequestServiceRoute
import com.fixora.app.feature.requestform.navigation.requestFormScreen
import com.fixora.app.feature.home.navigation.MAP_CATEGORY_KEY
import com.fixora.app.feature.servicerequest.navigation.ClientRequestRoute
import com.fixora.app.feature.servicerequest.navigation.IncomingRequestRoute
import com.fixora.app.feature.servicerequest.navigation.ServiceRequestRoute
import com.fixora.app.feature.servicerequest.navigation.serviceRequestScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
    incomingAlarms: Flow<PushMessage> = emptyFlow(),
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
        if (homeArea != null) notificationRoute(message)?.let { navController.navigate(it.forArea(homeArea)) }
        onNotificationHandled()
    }

    // Solicitud entrante con la app a la vista (ANDROID-13): se abre la alarma directamente.
    LaunchedEffect(incomingAlarms, homeArea) {
        if (homeArea != HomeArea.Provider) return@LaunchedEffect
        incomingAlarms.collect { message ->
            val route = notificationRoute(message) as? IncomingRequestRoute ?: return@collect
            val showing = navController.currentBackStackEntry?.takeIf { it.destination.hasRoute(IncomingRequestRoute::class) }
                ?.toRoute<IncomingRequestRoute>()
            if (showing != route) navController.navigate(route)
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
            onOpenProviderPortal = { uriHandler.openIfConfigured(BuildConfig.PROVIDER_PORTAL_URL) },
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
            onOpenNotifications = { navController.navigate(NotificationsRoute) },
            onEmergency = { navController.navigate(EmergencyRoute) },
            onOpenDebts = { navController.navigate(DebtsRoute(asProvider = false)) },
            onOpenProvider = { branchId, distanceKm, categoryId ->
                navController.navigate(ProviderProfileRoute(branchId, distanceKm, categoryId))
            },
            onOpenPhoto = { navController.navigate(AccountPhotoRoute) },
            onOpenPersonalData = { navController.navigate(PersonalDataRoute) },
            onOpenAddresses = { navController.navigate(AddressesRoute) },
            onOpenChangePassword = { navController.navigate(ChangePasswordRoute) },
        )
        providerHomeScreen(
            onOpenNotifications = { navController.navigate(NotificationsRoute) },
            onOpenDebts = { navController.navigate(DebtsRoute(asProvider = true)) },
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
        providerProfileScreen(
            onBack = navController::popBackStack,
            onRequestService = { branchId, categoryId, distanceKm ->
                navController.navigate(RequestServiceRoute(branchId, categoryId, distanceKm))
            },
        )
        requestFormScreen(
            onBack = navController::popBackStack,
            onSent = { requestId ->
                // Enviada: se espera la respuesta y «atrás» ya no regresa al formulario.
                navController.navigate(ClientRequestRoute(requestId)) {
                    popUpTo<RequestServiceRoute> { inclusive = true }
                }
            },
            onAddAddress = { navController.navigate(AddressFormRoute()) },
            onEditAddress = { navController.navigate(AddressFormRoute(it)) },
        )
        emergencyScreen(
            onCancel = navController::popBackStack,
            onSent = { requestId ->
                navController.navigate(ClientRequestRoute(requestId)) {
                    popUpTo<EmergencyRoute> { inclusive = true }
                }
            },
        )
        chatScreen(onBack = navController::popBackStack)
        debtsScreen(
            onBack = navController::popBackStack,
            onOpenRequest = { requestId -> navController.navigate(ServiceRequestRoute(requestId).forArea(homeArea)) },
        )
        notificationsScreen(
            onBack = navController::popBackStack,
            onOpen = { route -> navController.navigate(route.forArea(homeArea)) },
        )
        serviceRequestScreen(
            onBack = navController::popBackStack,
            onAccepted = { requestId ->
                // Aceptada: el detalle reemplaza a la alarma para que «atrás» no regrese a ella.
                navController.navigate(ServiceRequestRoute(requestId)) {
                    popUpTo<IncomingRequestRoute> { inclusive = true }
                }
            },
            onClientHome = { navController.popBackStack<ClientHomeRoute>(inclusive = false) },
            onClientResent = { requestId ->
                navController.navigate(ClientRequestRoute(requestId)) {
                    popUpTo<ClientRequestRoute> { inclusive = true }
                }
            },
            onChooseAnother = { categoryId ->
                // Regresa al inicio con el mapa filtrado por ese servicio (lo lee ClientHomeScreen).
                runCatching { navController.getBackStackEntry<ClientHomeRoute>() }.getOrNull()
                    ?.savedStateHandle?.set(MAP_CATEGORY_KEY, categoryId ?: "")
                navController.popBackStack<ClientHomeRoute>(inclusive = false)
            },
            onOpenChat = { requestId -> navController.navigate(ChatRoute(requestId)) },
        )
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

/** El cliente ve sus solicitudes en la pantalla de espera; el proveedor, en el detalle. */
private fun Any.forArea(area: HomeArea?): Any =
    if (area == HomeArea.Client && this is ServiceRequestRoute) ClientRequestRoute(requestId) else this
