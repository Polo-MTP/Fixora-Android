package com.fixora.app.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

/** Pestañas del cliente, en el orden del Figma «Fixora - Inicio». */
enum class ClientTab(val item: BottomBarItem) {
    Home(BottomBarItem(R.string.tab_home, R.drawable.ic_nav_home)),
    Map(BottomBarItem(R.string.tab_map, R.drawable.ic_nav_map)),
    Activity(BottomBarItem(R.string.tab_activity, R.drawable.ic_nav_activity)),
    Account(BottomBarItem(R.string.tab_account, R.drawable.ic_nav_account)),
}

/**
 * Inicio del cliente (ANDROID-4). La pestaña Cuenta es Mi cuenta (ANDROID-5); Inicio, Mapa y
 * Actividad son provisionales hasta que se construyan esos módulos. «Atrás» desde otra pestaña
 * regresa a Inicio antes de salir.
 */
@Composable
fun ClientHomeScreen(
    onOpenPersonalData: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenChangePassword: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ClientTab.Home) }
    BackHandler(enabled = tab != ClientTab.Home) { tab = ClientTab.Home }

    ClientHomeContent(
        tab = tab,
        onSelectTab = { tab = it },
        accountTab = {
            AccountTab(
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
    accountTab: @Composable () -> Unit,
) {
    HomeTabsScaffold(
        items = ClientTab.entries.map { it.item },
        selectedIndex = tab.ordinal,
        onSelect = { onSelectTab(ClientTab.entries[it]) },
    ) {
        when (tab) {
            ClientTab.Home -> ComingSoonTab(
                title = stringResource(R.string.home_greeting),
                message = stringResource(R.string.client_home_soon),
                icon = R.drawable.ic_nav_home,
            )
            ClientTab.Map -> ComingSoonTab(
                title = stringResource(R.string.tab_map),
                message = stringResource(R.string.client_map_soon),
                icon = R.drawable.ic_nav_map,
            )
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
        ClientHomeContent(ClientTab.Home, {}, {})
    }
}
