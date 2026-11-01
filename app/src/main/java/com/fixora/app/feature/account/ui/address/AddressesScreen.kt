package com.fixora.app.feature.account.ui.address

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.SampleDataNotice

/** Direcciones guardadas del cliente (ANDROID-5): tocar una la edita; una es la principal. */
@Composable
fun AddressesScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: AddressesViewModel = hiltViewModel(),
) {
    val addresses by viewModel.addresses.collectAsState()
    AddressesContent(
        addresses = addresses,
        onBack = onBack,
        onAdd = onAdd,
        onEdit = onEdit,
        onSetMain = viewModel::setMain,
    )
}

@Composable
private fun AddressesContent(
    addresses: List<Address>?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSetMain: (String) -> Unit,
) {
    AccountScaffold(
        title = stringResource(R.string.addresses_title),
        onBack = onBack,
        bottomBar = { FixoraPrimaryButton(text = stringResource(R.string.addresses_add), onClick = onAdd) },
    ) {
        SampleDataNotice()
        if (addresses?.isEmpty() == true) {
            Text(
                text = stringResource(R.string.addresses_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        addresses.orEmpty().forEach { address ->
            val id = address.id ?: return@forEach
            AddressCard(address = address, onClick = { onEdit(id) }, onSetMain = { onSetMain(id) })
        }
    }
}

@Composable
private fun AddressCard(address: Address, onClick: () -> Unit, onSetMain: () -> Unit) {
    FixoraCard(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_place), contentDescription = null, tint = Navy800, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = address.alias,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                    modifier = Modifier.weight(1f),
                )
                if (address.isMain) MainBadge()
            }
            Text(address.streetLine, style = MaterialTheme.typography.bodyMedium, color = Navy900)
            Text(address.areaLine, style = MaterialTheme.typography.labelSmall, color = Slate500)
            if (!address.isMain) {
                FixoraTextLink(text = stringResource(R.string.addresses_set_main), onClick = onSetMain)
            }
        }
    }
}

@Composable
private fun MainBadge() {
    Text(
        text = stringResource(R.string.addresses_main),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = Orange500,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Orange500.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AddressesPreview() {
    FixoraTheme {
        AddressesContent(
            addresses = listOf(
                Address("1", "Casa", "Av. Insurgentes Sur", "1234", "5", "Del Valle", "03100", "Benito Juárez", "Ciudad de México", "", true),
                Address("2", "Trabajo", "Paseo de la Reforma", "250", "", "Juárez", "06600", "Cuauhtémoc", "Ciudad de México", "", false),
            ),
            onBack = {}, onAdd = {}, onEdit = {}, onSetMain = {},
        )
    }
}
