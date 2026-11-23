package com.fixora.app.feature.emergency.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.emergency.ui.EmergencyScreen
import kotlinx.serialization.Serializable

/** Confirmar una emergencia (ANDROID-16); se abre con el botón del inicio del cliente. */
@Serializable
data object EmergencyRoute

fun NavGraphBuilder.emergencyScreen(onCancel: () -> Unit, onSent: (requestId: String) -> Unit) {
    composable<EmergencyRoute> { EmergencyScreen(onCancel = onCancel, onSent = onSent) }
}
