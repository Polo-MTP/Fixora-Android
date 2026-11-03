package com.fixora.app.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraAvatar
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraCardDivider
import com.fixora.app.core.designsystem.component.FixoraListRow
import com.fixora.app.core.designsystem.component.initialsOf
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.feature.account.domain.PersonalData
import com.fixora.app.feature.account.ui.components.SampleDataNotice
import java.time.LocalDate

/** Pestaña «Cuenta» del cliente (ANDROID-5): perfil, accesos a cada sección y cerrar sesión. */
@Composable
fun AccountTab(
    onOpenPersonalData: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenChangePassword: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    AccountTabContent(
        state = state,
        onOpenPersonalData = onOpenPersonalData,
        onOpenAddresses = onOpenAddresses,
        onOpenChangePassword = onOpenChangePassword,
        onLogout = viewModel::logout,
    )
}

@Composable
private fun AccountTabContent(
    state: AccountUiState,
    onOpenPersonalData: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineLarge, color = Navy900)
        SampleDataNotice()
        FixoraCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                val fullName = state.personal?.fullName
                FixoraAvatar(initialsOf(fullName, state.email))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = fullName ?: state.email,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Navy900,
                    )
                    if (fullName != null) {
                        Text(state.email, style = MaterialTheme.typography.labelSmall, color = Slate500)
                    }
                }
            }
        }
        FixoraCard {
            FixoraListRow(
                icon = R.drawable.ic_nav_account,
                title = stringResource(R.string.personal_data_title),
                subtitle = stringResource(R.string.account_personal_subtitle),
                onClick = onOpenPersonalData,
            )
            FixoraCardDivider()
            FixoraListRow(
                icon = R.drawable.ic_place,
                title = stringResource(R.string.addresses_title),
                subtitle = state.mainAddress?.let { stringResource(R.string.account_addresses_main, it.alias) }
                    ?: stringResource(R.string.account_addresses_empty),
                onClick = onOpenAddresses,
            )
            FixoraCardDivider()
            FixoraListRow(
                icon = R.drawable.ic_lock,
                title = stringResource(R.string.change_password_title),
                onClick = onOpenChangePassword,
            )
        }
        FixoraCard {
            FixoraListRow(
                icon = R.drawable.ic_logout,
                title = stringResource(R.string.home_logout),
                onClick = onLogout,
                tint = ErrorRed,
                showChevron = false,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 760, showBackground = true, backgroundColor = 0xFFF4F6F9)
@Composable
private fun AccountTabPreview() {
    FixoraTheme {
        AccountTabContent(
            state = AccountUiState(
                email = "ana@correo.com",
                personal = PersonalData("Ana María", "Pérez", "López", LocalDate.of(1995, 4, 12), "5512345678"),
                mainAddress = null,
            ),
            onOpenPersonalData = {},
            onOpenAddresses = {},
            onOpenChangePassword = {},
            onLogout = {},
        )
    }
}
