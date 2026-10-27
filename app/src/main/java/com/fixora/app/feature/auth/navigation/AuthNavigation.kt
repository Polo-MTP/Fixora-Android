package com.fixora.app.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.auth.ui.forgot.ForgotPasswordScreen
import com.fixora.app.feature.auth.ui.landing.LandingScreen
import com.fixora.app.feature.auth.ui.login.LoginScreen
import com.fixora.app.feature.auth.ui.register.RegisterScreen
import com.fixora.app.feature.auth.ui.register.RegisterSuccessScreen
import kotlinx.serialization.Serializable

@Serializable
data object LandingRoute

@Serializable
data object LoginRoute

@Serializable
data object ForgotPasswordRoute

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

fun NavGraphBuilder.loginScreen(
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onSubmit: () -> Unit,
) {
    composable<LoginRoute> {
        LoginScreen(
            onBack = onBack,
            onForgotPassword = onForgotPassword,
            onRegister = onRegister,
            onSubmit = onSubmit,
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

fun NavGraphBuilder.registerScreen(
    onExit: () -> Unit,
    onLogin: () -> Unit,
    onRegistered: () -> Unit,
) {
    composable<RegisterRoute> {
        RegisterScreen(onExit = onExit, onLogin = onLogin, onRegistered = onRegistered)
    }
}

fun NavGraphBuilder.registerSuccessScreen(onContinue: () -> Unit) {
    composable<RegisterSuccessRoute> {
        RegisterSuccessScreen(onContinue = onContinue)
    }
}
