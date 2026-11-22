package com.fixora.app.feature.providerprofile.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.providerprofile.ui.ProviderProfileScreen
import kotlinx.serialization.Serializable

/**
 * Ficha pública de una sucursal. Desde el mapa llegan también la distancia ya escrita («1.2») y el
 * servicio elegido, para mostrar ese precio.
 */
@Serializable
data class ProviderProfileRoute(
    val branchId: String,
    val distanceKm: String? = null,
    val categoryId: String? = null,
)

fun NavGraphBuilder.providerProfileScreen(
    onBack: () -> Unit,
    onRequestService: (branchId: String, categoryId: String?, distanceKm: String?) -> Unit,
) {
    composable<ProviderProfileRoute> { ProviderProfileScreen(onBack = onBack, onRequestService = onRequestService) }
}
