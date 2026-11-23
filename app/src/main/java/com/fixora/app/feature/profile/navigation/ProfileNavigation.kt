package com.fixora.app.feature.profile.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.profile.ui.photo.TechnicianPhotoScreen
import com.fixora.app.feature.profile.ui.vehicle.VehicleScreen
import kotlinx.serialization.Serializable

@Serializable
data object TechnicianPhotoRoute

@Serializable
data object VehicleRoute

/** Pantallas del perfil del técnico que se abren desde la pestaña «Perfil» del proveedor. */
fun NavGraphBuilder.profileScreens(onBack: () -> Unit) {
    composable<TechnicianPhotoRoute> { TechnicianPhotoScreen(onBack = onBack) }
    composable<VehicleRoute> { VehicleScreen(onBack = onBack) }
}
