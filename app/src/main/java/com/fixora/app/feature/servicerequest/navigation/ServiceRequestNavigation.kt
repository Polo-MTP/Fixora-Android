package com.fixora.app.feature.servicerequest.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.servicerequest.ui.ServiceRequestScreen
import kotlinx.serialization.Serializable

/** Una solicitud de servicio; la abre el aviso de solicitud entrante o de cambio de estado. */
@Serializable
data class ServiceRequestRoute(val requestId: String)

fun NavGraphBuilder.serviceRequestScreen(onBack: () -> Unit) {
    composable<ServiceRequestRoute> { ServiceRequestScreen(onBack = onBack) }
}
