package com.fixora.app.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.BottomBarItem
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.session.Session
import com.fixora.app.feature.home.ui.components.ComingSoonTab
import com.fixora.app.feature.home.ui.components.HomeTabsScaffold
import com.fixora.app.feature.home.ui.components.ProfileTab

/** Pestañas del proveedor, en el orden del Figma «Fixora Técnico - Inicio». */
enum class ProviderTab(val item: BottomBarItem) {
    Home(BottomBarItem(R.string.tab_home, R.drawable.ic_nav_home)),
    Services(BottomBarItem(R.string.tab_services, R.drawable.ic_nav_services)),
    Reviews(BottomBarItem(R.string.tab_reviews, R.drawable.ic_nav_reviews)),
    Profile(BottomBarItem(R.string.tab_profile, R.drawable.ic_nav_account)),
}

/**
 * Inicio del proveedor, gerente y empleado (ANDROID-4). Solo entran cuentas aprobadas: la API
 * rechaza el login de las que siguen «por aprobar». Las pestañas son provisionales.
 */
@Composable
fun ProviderHomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(ProviderTab.Home) }
    BackHandler(enabled = tab != ProviderTab.Home) { tab = ProviderTab.Home }
    val session by viewModel.session.collectAsState()

    ProviderHomeContent(
        tab = tab,
        onSelectTab = { tab = it },
        session = session,
        onLogout = viewModel::logout,
    )
}

@Composable
private fun ProviderHomeContent(
    tab: ProviderTab,
    onSelectTab: (ProviderTab) -> Unit,
    session: Session?,
    onLogout: () -> Unit,
) {
    HomeTabsScaffold(
        items = ProviderTab.entries.map { it.item },
        selectedIndex = tab.ordinal,
        onSelect = { onSelectTab(ProviderTab.entries[it]) },
    ) {
        when (tab) {
            ProviderTab.Home -> ComingSoonTab(
                title = stringResource(R.string.home_greeting),
                message = stringResource(R.string.provider_home_soon),
                icon = R.drawable.ic_nav_home,
            )
            ProviderTab.Services -> ComingSoonTab(
                title = stringResource(R.string.tab_services),
                message = stringResource(R.string.provider_services_soon),
                icon = R.drawable.ic_nav_services,
            )
            ProviderTab.Reviews -> ComingSoonTab(
                title = stringResource(R.string.tab_reviews),
                message = stringResource(R.string.provider_reviews_soon),
                icon = R.drawable.ic_nav_reviews,
            )
            ProviderTab.Profile -> ProfileTab(
                title = stringResource(R.string.tab_profile),
                session = session,
                onLogout = onLogout,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProviderProfilePreview() {
    FixoraTheme {
        ProviderHomeContent(
            tab = ProviderTab.Profile,
            onSelectTab = {},
            session = Session("2", "juan@plomeria.mx", "proveedor", "activo", "", ""),
            onLogout = {},
        )
    }
}
