package com.fixora.app.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.BottomBarItem
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.feature.account.ui.AccountTab
import com.fixora.app.feature.home.ui.components.ComingSoonTab
import com.fixora.app.feature.home.ui.components.HomeTabsScaffold
import com.fixora.app.feature.map.ui.MapLaunch
import com.fixora.app.feature.map.ui.MapTab

/** Pestañas del cliente, en el orden del Figma «Fixora - Inicio». */
enum class ClientTab(val item: BottomBarItem) {
    Home(BottomBarItem(R.string.tab_home, R.drawable.ic_nav_home)),
    Map(BottomBarItem(R.string.tab_map, R.drawable.ic_nav_map)),
    Activity(BottomBarItem(R.string.tab_activity, R.drawable.ic_nav_activity)),
    Account(BottomBarItem(R.string.tab_account, R.drawable.ic_nav_account)),
}

/**
 * Inicio del cliente (ANDROID-4). Inicio tiene el buscador y los accesos por servicio (ANDROID-12),
 * que abren Mapa ya filtrado (ANDROID-10); Cuenta es Mi cuenta (ANDROID-5) y Actividad es
 * provisional hasta que se construya ese módulo.
 * «Atrás» desde otra pestaña regresa a Inicio antes de salir.
 */
@Composable
fun ClientHomeScreen(
    openMapCategory: String?,
    onMapCategoryHandled: () -> Unit,
    onOpenNotifications: () -> Unit,
    onEmergency: () -> Unit,
    onOpenProvider: (branchId: String, distanceKm: String, categoryId: String?) -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenPersonalData: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenChangePassword: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ClientTab.Home) }
    // Filtro que el Inicio le pasa al mapa; el mapa lo aplica una vez y avisa para limpiarlo.
    var mapLaunch by remember { mutableStateOf<MapLaunch?>(null) }
    BackHandler(enabled = tab != ClientTab.Home) { tab = ClientTab.Home }
    // Al regresar de una solicitud rechazada o vencida: el mapa con ese servicio para elegir otro.
    LaunchedEffect(openMapCategory) {
        val category = openMapCategory ?: return@LaunchedEffect
        mapLaunch = MapLaunch(categoryId = category.ifBlank { null })
        tab = ClientTab.Map
        onMapCategoryHandled()
    }

    ClientHomeContent(
        tab = tab,
        onSelectTab = { tab = it },
        homeTab = {
            ClientHomeTab(
                onOpenMap = { launch ->
                    mapLaunch = launch
                    tab = ClientTab.Map
                },
                onOpenNotifications = onOpenNotifications,
                onEmergency = onEmergency,
            )
        },
        mapTab = {
            MapTab(launch = mapLaunch, onLaunchHandled = { mapLaunch = null }, onOpenProvider = onOpenProvider)
        },
        accountTab = {
            AccountTab(
                onOpenPhoto = onOpenPhoto,
                onOpenPersonalData = onOpenPersonalData,
                onOpenAddresses = onOpenAddresses,
                onOpenChangePassword = onOpenChangePassword,
            )
        },
    )
}

@Composable
private fun ClientHomeContent(
    tab: ClientTab,
    onSelectTab: (ClientTab) -> Unit,
    homeTab: @Composable () -> Unit,
    mapTab: @Composable () -> Unit,
    accountTab: @Composable () -> Unit,
) {
    HomeTabsScaffold(
        items = ClientTab.entries.map { it.item },
        selectedIndex = tab.ordinal,
        onSelect = { onSelectTab(ClientTab.entries[it]) },
    ) {
        when (tab) {
            ClientTab.Home -> homeTab()
            ClientTab.Map -> mapTab()
            ClientTab.Activity -> ComingSoonTab(
                title = stringResource(R.string.tab_activity),
                message = stringResource(R.string.client_activity_soon),
                icon = R.drawable.ic_nav_activity,
            )
            ClientTab.Account -> accountTab()
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ClientHomePreview() {
    FixoraTheme {
        ClientHomeContent(ClientTab.Home, {}, {}, {}, {})
    }
}
