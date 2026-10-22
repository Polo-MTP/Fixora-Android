package com.fixora.app.feature.intro.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.intro.ui.IntroScreen
import kotlinx.serialization.Serializable

@Serializable
data object IntroRoute

fun NavGraphBuilder.introScreen(onFinished: () -> Unit) {
    composable<IntroRoute> {
        IntroScreen(onFinished = onFinished)
    }
}
