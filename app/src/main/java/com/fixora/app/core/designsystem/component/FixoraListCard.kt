package com.fixora.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy300
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White

/** Tarjeta blanca con borde suave, como las de estadísticas del inicio del técnico. */
@Composable
fun FixoraCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(White)
            .border(1.dp, Mist200, RoundedCornerShape(20.dp)),
        content = content,
    )
}

/** Separador entre filas de una [FixoraCard]. */
@Composable
fun FixoraCardDivider() {
    HorizontalDivider(thickness = 1.dp, color = Mist200, modifier = Modifier.padding(start = 68.dp))
}

/**
 * Fila de menú: ícono en un cuadro claro, título, subtítulo opcional y flecha. Con
 * [showChevron] = false queda como acción directa (por ejemplo, «Cerrar sesión»).
 */
@Composable
fun FixoraListRow(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = Navy800,
    showChevron: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Mist50),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (tint == Navy800) Navy900 else tint,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Slate500)
            }
        }
        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Navy300,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Círculo azul con las iniciales del usuario, mientras no haya foto de perfil. */
@Composable
fun FixoraAvatar(initials: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Navy800),
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.36f).sp),
            color = White,
        )
    }
}

/** Iniciales para [FixoraAvatar]: del nombre si lo hay; si no, la primera letra del correo. */
fun initialsOf(name: String?, email: String): String {
    val fromName = name.orEmpty().split(' ').filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }
    return fromName.joinToString("").ifEmpty { email.firstOrNull()?.uppercase().orEmpty() }
}
