package com.fixora.app.feature.servicerequest.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.servicerequest.ui.ServiceRequestScreen
import com.fixora.app.feature.servicerequest.ui.client.ClientRequestScreen
import com.fixora.app.feature.servicerequest.ui.incoming.IncomingRequestScreen
import kotlinx.serialization.Serializable

/** Una solicitud de servicio; la abre el aviso de solicitud entrante o de cambio de estado. */
@Serializable
data class ServiceRequestRoute(val requestId: String)

/** La solicitud vista por el cliente: espera, rechazo, vencida o aceptada (ANDROID-17). */
@Serializable
data class ClientRequestRoute(val requestId: String)

/** Alarma de solicitud entrante a pantalla completa (ANDROID-13). */
@Serializable
data class IncomingRequestRoute(val requestId: String)

fun NavGraphBuilder.serviceRequestScreen(
    onBack: () -> Unit,
    onAccepted: (requestId: String) -> Unit,
    onClientHome: () -> Unit,
    onClientResent: (requestId: String) -> Unit,
    onChooseAnother: (categoryId: String?) -> Unit,
    onOpenChat: (requestId: String) -> Unit,
) {
    composable<ClientRequestRoute> {
        ClientRequestScreen(
            onHome = onClientHome,
            onResent = onClientResent,
            onChooseAnother = onChooseAnother,
            onOpenChat = onOpenChat,
        )
    }
    composable<ServiceRequestRoute> { ServiceRequestScreen(onBack = onBack, onOpenChat = onOpenChat) }
    composable<IncomingRequestRoute> { IncomingRequestScreen(onAccepted = onAccepted, onClose = onBack) }
}
