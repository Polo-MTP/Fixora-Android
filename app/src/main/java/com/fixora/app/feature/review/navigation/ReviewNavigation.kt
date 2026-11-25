package com.fixora.app.feature.review.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.review.ui.RateServiceScreen
import kotlinx.serialization.Serializable

/** Calificar una solicitud terminada (ANDROID-26); la abren el cliente y el técnico. */
@Serializable
data class RateServiceRoute(val requestId: String)

fun NavGraphBuilder.rateServiceScreen(onBack: () -> Unit) {
    composable<RateServiceRoute> { RateServiceScreen(onBack = onBack) }
}
