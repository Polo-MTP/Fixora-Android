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
import com.fixora.app.feature.availability.ui.ProviderHomeTab
import com.fixora.app.feature.home.ui.components.ComingSoonTab
import com.fixora.app.feature.home.ui.components.HomeTabsScaffold
import com.fixora.app.feature.profile.ui.ProviderProfileTab

/** Pestañas del proveedor, en el orden del Figma «Fixora Técnico - Inicio». */
enum class ProviderTab(val item: BottomBarItem) {
    Home(BottomBarItem(R.string.tab_home, R.drawable.ic_nav_home)),
    Services(BottomBarItem(R.string.tab_services, R.drawable.ic_nav_services)),
    Reviews(BottomBarItem(R.string.tab_reviews, R.drawable.ic_nav_reviews)),
    Profile(BottomBarItem(R.string.tab_profile, R.drawable.ic_nav_account)),
}

/**
 * Inicio del proveedor, gerente y empleado (ANDROID-4). Solo entran cuentas aprobadas: la API
 * rechaza el login de las que siguen «por aprobar». Inicio tiene el interruptor En línea (ANDROID-9);
 * Servicios y Reseñas son provisionales; Perfil tiene la foto, biografía y vehículo (ANDROID-7), y
 * los trabajos realizados y certificados (ANDROID-8).
 */
@Composable
fun ProviderHomeScreen(
    onOpenNotifications: () -> Unit,
    onOpenPhoto: () -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenPortfolio: () -> Unit,
    onOpenCertificates: () -> Unit,
    onOpenPersonalData: () -> Unit,
    onOpenChangePassword: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ProviderTab.Home) }
    BackHandler(enabled = tab != ProviderTab.Home) { tab = ProviderTab.Home }

    ProviderHomeContent(
        tab = tab,
        onSelectTab = { tab = it },
        homeTab = { ProviderHomeTab(onOpenNotifications = onOpenNotifications) },
        profileTab = {
            ProviderProfileTab(
                onOpenPhoto = onOpenPhoto,
                onOpenVehicle = onOpenVehicle,
                onOpenPortfolio = onOpenPortfolio,
                onOpenCertificates = onOpenCertificates,
                onOpenPersonalData = onOpenPersonalData,
                onOpenChangePassword = onOpenChangePassword,
            )
        },
    )
}

@Composable
private fun ProviderHomeContent(
    tab: ProviderTab,
    onSelectTab: (ProviderTab) -> Unit,
    homeTab: @Composable () -> Unit,
    profileTab: @Composable () -> Unit,
) {
    HomeTabsScaffold(
        items = ProviderTab.entries.map { it.item },
        selectedIndex = tab.ordinal,
        onSelect = { onSelectTab(ProviderTab.entries[it]) },
    ) {
        when (tab) {
            ProviderTab.Home -> homeTab()
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
            ProviderTab.Profile -> profileTab()
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProviderHomePreview() {
    FixoraTheme {
        ProviderHomeContent(tab = ProviderTab.Home, onSelectTab = {}, homeTab = {}, profileTab = {})
    }
}
