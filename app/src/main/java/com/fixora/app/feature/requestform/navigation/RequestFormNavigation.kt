package com.fixora.app.feature.requestform.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.requestform.ui.RequestFormScreen
import kotlinx.serialization.Serializable

/**
 * Formulario de solicitud a una sucursal (ANDROID-15). Desde la ficha llegan el servicio elegido
 * en el mapa y la distancia ya escrita («1.2»).
 */
@Serializable
data class RequestServiceRoute(
    val branchId: String,
    val categoryId: String? = null,
    val distanceKm: String? = null,
)

fun NavGraphBuilder.requestFormScreen(
    onBack: () -> Unit,
    onSent: (requestId: String) -> Unit,
    onAddAddress: () -> Unit,
    onEditAddress: (addressId: String) -> Unit,
) {
    composable<RequestServiceRoute> { RequestFormScreen(onBack = onBack, onSent = onSent, onAddAddress = onAddAddress, onEditAddress = onEditAddress) }
}
