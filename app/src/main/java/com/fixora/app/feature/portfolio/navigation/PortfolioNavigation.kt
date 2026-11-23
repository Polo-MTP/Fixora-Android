package com.fixora.app.feature.portfolio.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fixora.app.feature.portfolio.ui.certificates.CertificatesScreen
import com.fixora.app.feature.portfolio.ui.works.AddWorkScreen
import com.fixora.app.feature.portfolio.ui.works.PortfolioScreen
import kotlinx.serialization.Serializable

@Serializable
data object PortfolioRoute

@Serializable
data object AddWorkRoute

@Serializable
data object CertificatesRoute

/** Evidencias de trabajo y certificados (ANDROID-8), que se abren desde la pestaña «Perfil». */
fun NavGraphBuilder.portfolioScreens(onBack: () -> Unit, onAddWork: () -> Unit) {
    composable<PortfolioRoute> { PortfolioScreen(onBack = onBack, onAddWork = onAddWork) }
    composable<AddWorkRoute> { AddWorkScreen(onBack = onBack) }
    composable<CertificatesRoute> { CertificatesScreen(onBack = onBack) }
}
