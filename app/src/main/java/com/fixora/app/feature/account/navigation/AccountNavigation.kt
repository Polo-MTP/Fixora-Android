package com.fixora.app.feature.account.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.account.ui.address.AddressFormScreen
import com.fixora.app.feature.account.ui.address.AddressesScreen
import com.fixora.app.feature.account.ui.password.ChangePasswordScreen
import com.fixora.app.feature.account.ui.personal.PersonalDataScreen
import kotlinx.serialization.Serializable

@Serializable
data object PersonalDataRoute

@Serializable
data object AddressesRoute

/** [addressId] null = dirección nueva. */
@Serializable
data class AddressFormRoute(val addressId: String? = null)

@Serializable
data object ChangePasswordRoute

/** Pantallas de Mi cuenta que se abren desde la pestaña «Cuenta» del cliente. */
fun NavGraphBuilder.accountScreens(
    onBack: () -> Unit,
    onAddAddress: () -> Unit,
    onEditAddress: (String) -> Unit,
) {
    composable<PersonalDataRoute> { PersonalDataScreen(onBack = onBack) }
    composable<AddressesRoute> {
        AddressesScreen(onBack = onBack, onAdd = onAddAddress, onEdit = onEditAddress)
    }
    composable<AddressFormRoute> { AddressFormScreen(onBack = onBack) }
    composable<ChangePasswordRoute> { ChangePasswordScreen(onBack = onBack) }
}
