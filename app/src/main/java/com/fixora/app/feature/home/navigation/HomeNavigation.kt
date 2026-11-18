package com.fixora.app.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.home.ui.ClientHomeScreen
import com.fixora.app.feature.home.ui.ProviderHomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object ClientHomeRoute

@Serializable
data object ProviderHomeRoute

fun NavGraphBuilder.clientHomeScreen(
    onOpenPersonalData: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenChangePassword: () -> Unit,
) {
    composable<ClientHomeRoute> {
        ClientHomeScreen(
            onOpenPersonalData = onOpenPersonalData,
            onOpenAddresses = onOpenAddresses,
            onOpenChangePassword = onOpenChangePassword,
        )
    }
}

fun NavGraphBuilder.providerHomeScreen(
    onOpenPhoto: () -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onOpenCertificates: () -> Unit,
    onOpenPersonalData: () -> Unit,
    onOpenChangePassword: () -> Unit,
) {
    composable<ProviderHomeRoute> {
        ProviderHomeScreen(
            onOpenPhoto = onOpenPhoto,
            onOpenVehicle = onOpenVehicle,
            onOpenPortfolio = onOpenPortfolio,
            onOpenCertificates = onOpenCertificates,
            onOpenPersonalData = onOpenPersonalData,
            onOpenChangePassword = onOpenChangePassword,
        )
    }
}
