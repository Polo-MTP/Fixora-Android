package com.fixora.app.feature.payment.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.fixora.app.feature.payment.ui.debts.DebtsScreen
import kotlinx.serialization.Serializable

/** Adeudos: lo que le deben al proveedor o lo que debe el cliente. */
@Serializable
data class DebtsRoute(val asProvider: Boolean)

fun NavGraphBuilder.debtsScreen(onBack: () -> Unit, onOpenRequest: (requestId: String) -> Unit) {
    composable<DebtsRoute> { entry ->
        DebtsScreen(asProvider = entry.toRoute<DebtsRoute>().asProvider, onBack = onBack, onOpenRequest = onOpenRequest)
    }
}
