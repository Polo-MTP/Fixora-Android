package com.fixora.app.feature.payment.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.feature.payment.domain.AdjustmentReason
import com.fixora.app.feature.payment.domain.AdjustmentStatus
import com.fixora.app.feature.payment.domain.MoneyRules
import com.fixora.app.feature.payment.domain.PaymentMethod
import com.fixora.app.feature.payment.domain.PriceAdjustment
import com.fixora.app.feature.servicerequest.domain.ServiceRequest

@Composable
fun adjustmentReasonLabel(reason: AdjustmentReason): String = stringResource(
    when (reason) {
        AdjustmentReason.ExtraMaterials -> R.string.payment_reason_materials
        AdjustmentReason.ExtraTime -> R.string.payment_reason_time
        AdjustmentReason.Other -> R.string.payment_reason_other
    },
)

@Composable
fun paymentMethodLabel(method: PaymentMethod): String = stringResource(
    when (method) {
        PaymentMethod.Cash -> R.string.payment_method_cash
        PaymentMethod.Transfer -> R.string.payment_method_transfer
    },
)

/**
 * Desglose del cobro, como «Terminar servicio» del diseño: precio base, cada ajuste con su estado y
 * el total. Los rechazados se ven tachados porque no suman.
 */
@Composable
fun PriceBreakdownCard(request: ServiceRequest, totalLabel: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Mist50)
            .border(1.dp, Mist200, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        BreakdownRow(stringResource(R.string.payment_base_price), MoneyRules.format(request.baseCents))
        request.adjustments.forEach { AdjustmentRow(it) }
        HorizontalDivider(color = Mist200)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                totalLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Navy900,
                modifier = Modifier.weight(1f),
            )
            Text(
                MoneyRules.format(request.totalCents),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Navy900,
            )
        }
    }
}

@Composable
private fun AdjustmentRow(adjustment: PriceAdjustment) {
    val rejected = adjustment.status == AdjustmentStatus.Rejected
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(adjustmentReasonLabel(adjustment.reason), style = MaterialTheme.typography.bodyMedium, color = Slate500)
            adjustment.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Slate500.copy(alpha = 0.8f))
            }
            adjustmentStatusLabel(adjustment.status)?.let { (text, color) ->
                Text(text, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
        Text(
            "+" + MoneyRules.format(adjustment.amount),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (rejected) TextDecoration.LineThrough else null,
            ),
            color = if (rejected) Slate500 else Navy900,
        )
    }
}

/** Aprobado no lleva etiqueta: es lo normal y ya suma al total. */
@Composable
private fun adjustmentStatusLabel(status: AdjustmentStatus): Pair<String, Color>? = when (status) {
    AdjustmentStatus.Pending -> stringResource(R.string.payment_adjustment_pending) to Orange500
    AdjustmentStatus.Rejected -> stringResource(R.string.payment_adjustment_rejected) to ErrorRed
    AdjustmentStatus.Approved, AdjustmentStatus.Paid -> null
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Slate500, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Navy900)
    }
}

enum class NoticeTone { Info, Warning, Success, Error }

/** Aviso con ícono, título y texto; el tono da el color del borde y del ícono. */
@Composable
fun PaymentNotice(
    @DrawableRes icon: Int,
    title: String,
    text: String? = null,
    tone: NoticeTone = NoticeTone.Info,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val color = when (tone) {
        NoticeTone.Info -> Navy800
        NoticeTone.Warning -> Orange500
        NoticeTone.Success -> SuccessGreen
        NoticeTone.Error -> ErrorRed
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.06f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Navy900)
                text?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Slate500) }
            }
        }
        content()
    }
}

/** Botón secundario con borde, para la acción que acompaña a la principal. */
@Composable
fun OutlineActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    color: Color = Navy900,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Mist200, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
    ) {
        if (loading) {
            CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) color else Slate500)
        }
    }
}
