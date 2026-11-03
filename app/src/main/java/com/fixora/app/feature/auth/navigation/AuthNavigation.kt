package com.fixora.app.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.auth.ui.forgot.ForgotPasswordScreen
import com.fixora.app.feature.auth.ui.landing.LandingScreen
import com.fixora.app.feature.auth.ui.login.LoginScreen
import com.fixora.app.feature.auth.ui.register.RegisterScreen
import kotlinx.serialization.Serializable

@Serializable
data object LandingRoute

@Serializable
data object LoginRoute

@Serializable
data object ForgotPasswordRoute

@Serializable
data object RegisterRoute

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

fun NavGraphBuilder.loginScreen(
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
) {
    composable<LoginRoute> {
        LoginScreen(
            onBack = onBack,
            onForgotPassword = onForgotPassword,
            onRegister = onRegister,
        )
    }
}

fun NavGraphBuilder.forgotPasswordScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onSubmit: () -> Unit,
) {
    composable<ForgotPasswordRoute> {
        ForgotPasswordScreen(onBack = onBack, onLogin = onLogin, onSubmit = onSubmit)
    }
}

/** Al terminar, el registro guarda la sesión y FixoraNavHost lleva al inicio del cliente. */
fun NavGraphBuilder.registerScreen(
    onExit: () -> Unit,
    onLogin: () -> Unit,
) {
    composable<RegisterRoute> {
        RegisterScreen(onExit = onExit, onLogin = onLogin)
    }
}
