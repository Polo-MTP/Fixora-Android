package com.fixora.app.feature.home.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.BottomBarItem
import com.fixora.app.core.designsystem.component.FixoraAvatar
import com.fixora.app.core.designsystem.component.FixoraBottomBar
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraListRow
import com.fixora.app.core.designsystem.component.initialsOf
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.session.Roles
import com.fixora.app.core.session.Session
import com.fixora.app.core.ui.SystemBarsAppearance

/** Estructura de los inicios: el contenido de la pestaña elegida arriba y la barra de pestañas abajo. */
@Composable
fun HomeTabsScaffold(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    SystemBarsAppearance(lightIcons = false)
    Column(
        Modifier
            .fillMaxSize()
            .background(Mist50),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) { content() }
        FixoraBottomBar(items = items, selectedIndex = selectedIndex, onSelect = onSelect)
    }
}

/** Título grande de cada pestaña, alineado con el margen del contenido. */
@Composable
fun TabTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineLarge, color = Navy900, modifier = modifier)
}

/** Pestaña que todavía no tiene contenido: explica lo que habrá ahí. */
@Composable
fun ComingSoonTab(title: String, message: String, @DrawableRes icon: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        TabTitle(title)
        FixoraCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(18.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Mist50),
                ) {
                    Icon(painterResource(icon), contentDescription = null, tint = Navy800, modifier = Modifier.size(24.dp))
                }
                Text(message, style = MaterialTheme.typography.bodyMedium, color = Slate500, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Tarjeta con el avatar, el correo y el rol de quien tiene la sesión. */
@Composable
fun SessionCard(email: String, role: String, name: String? = null) {
    FixoraCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            FixoraAvatar(initialsOf(name, email))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name ?: email,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                )
                Text(
                    text = if (name != null) email else roleLabel(role),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate500,
                )
            }
        }
    }
}

/** Pestaña de perfil mínima: quién tiene la sesión y el botón para cerrarla. */
@Composable
fun ProfileTab(title: String, session: Session?, onLogout: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        TabTitle(title)
        if (session != null) SessionCard(email = session.email, role = session.role)
        FixoraCard { LogoutRow(onLogout) }
    }
}

@Composable
fun LogoutRow(onLogout: () -> Unit) {
    FixoraListRow(
        icon = R.drawable.ic_logout,
        title = stringResource(R.string.home_logout),
        onClick = onLogout,
        tint = ErrorRed,
        showChevron = false,
    )
}

@Composable
private fun roleLabel(role: String): String = when (role.trim().lowercase()) {
    Roles.CLIENT -> stringResource(R.string.role_client)
    Roles.PROVIDER -> stringResource(R.string.role_provider)
    Roles.MANAGER -> stringResource(R.string.role_manager)
    Roles.EMPLOYEE -> stringResource(R.string.role_employee)
    else -> role
}
