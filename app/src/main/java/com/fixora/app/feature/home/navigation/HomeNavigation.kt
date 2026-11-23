package com.fixora.app.feature.home.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.home.ui.ClientHomeScreen
import com.fixora.app.feature.home.ui.ProviderHomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object ClientHomeRoute

/** Servicio con el que abrir el mapa al regresar al inicio («Elegir otro proveedor», ANDROID-17). */
const val MAP_CATEGORY_KEY = "openMapCategory"

@Serializable
data object ProviderHomeRoute

fun NavGraphBuilder.clientHomeScreen(
    onOpenNotifications: () -> Unit,
    onEmergency: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenProvider: (branchId: String, distanceKm: String, categoryId: String?) -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenPersonalData: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenChangePassword: () -> Unit,
) {
    composable<ClientHomeRoute> { entry ->
        val mapCategory by entry.savedStateHandle.getStateFlow<String?>(MAP_CATEGORY_KEY, null).collectAsState()
        ClientHomeScreen(
            openMapCategory = mapCategory,
            onMapCategoryHandled = { entry.savedStateHandle[MAP_CATEGORY_KEY] = null },
            onOpenNotifications = onOpenNotifications,
            onEmergency = onEmergency,
            onOpenDebts = onOpenDebts,
            onOpenProvider = onOpenProvider,
            onOpenPhoto = onOpenPhoto,
            onOpenPersonalData = onOpenPersonalData,
            onOpenAddresses = onOpenAddresses,
            onOpenChangePassword = onOpenChangePassword,
        )
    }
}

fun NavGraphBuilder.providerHomeScreen(
    onOpenNotifications: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onOpenCertificates: () -> Unit,
    onOpenPersonalData: () -> Unit,
    onOpenChangePassword: () -> Unit,
) {
    composable<ProviderHomeRoute> {
        ProviderHomeScreen(
            onOpenNotifications = onOpenNotifications,
            onOpenDebts = onOpenDebts,
            onOpenPhoto = onOpenPhoto,
            onOpenVehicle = onOpenVehicle,
            onOpenPortfolio = onOpenPortfolio,
            onOpenCertificates = onOpenCertificates,
            onOpenPersonalData = onOpenPersonalData,
            onOpenChangePassword = onOpenChangePassword,
        )
    }
}
