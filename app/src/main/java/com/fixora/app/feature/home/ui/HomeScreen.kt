package com.fixora.app.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.session.Session
import com.fixora.app.core.ui.SystemBarsAppearance

/**
 * Inicio provisional (ANDROID-2): muestra quién tiene la sesión y permite cerrarla. Se reemplaza
 * cuando se construya el inicio real del cliente.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsState()
    HomeContent(session = session, onLogout = viewModel::logout)
}

@Composable
private fun HomeContent(session: Session?, onLogout: () -> Unit) {
    SystemBarsAppearance(lightIcons = false)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Mist50)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.displaySmall, color = Navy800)
        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge, color = Slate500)
        if (session != null) {
            Text(session.email, style = MaterialTheme.typography.titleLarge, color = Navy800)
            Text(stringResource(R.string.home_role, session.role), style = MaterialTheme.typography.bodyMedium, color = Slate500)
            Text(stringResource(R.string.home_status, session.status), style = MaterialTheme.typography.bodyMedium, color = Slate500)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.home_placeholder), style = MaterialTheme.typography.bodyMedium, color = Slate500)
        Spacer(Modifier.weight(1f))
        FixoraPrimaryButton(text = stringResource(R.string.home_logout), onClick = onLogout)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    FixoraTheme {
        HomeContent(
            session = Session("1", "ana@correo.com", "cliente", "activo", "", ""),
            onLogout = {},
        )
    }
}
