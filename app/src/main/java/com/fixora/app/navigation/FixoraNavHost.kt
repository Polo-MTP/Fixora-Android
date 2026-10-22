package com.fixora.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.fixora.app.BuildConfig
import com.fixora.app.feature.auth.navigation.LandingRoute
import com.fixora.app.feature.auth.navigation.landingScreen

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
    ) {
        landingScreen(
            // TODO(ANDROID-3): navegar al registro del cliente cuando exista la pantalla.
            onStartClick = {},
            onBecomeProviderClick = { uriHandler.openIfConfigured(BuildConfig.PROVIDER_SIGNUP_URL) },
        )
    }
}

// Las URL vienen de app/env/<flavor>.properties; si están vacías (aún sin desplegar) no se abre nada.
private fun UriHandler.openIfConfigured(url: String) {
    if (url.isBlank()) return
    runCatching { openUri(url) }
}
