package com.fixora.app.feature.payment.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.ui.message
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.payment.domain.AdjustmentReason
import com.fixora.app.feature.payment.domain.MoneyRules

/** Lo que escribe el técnico al proponer un ajuste. */
data class AdjustmentDraft(val amountCents: Long, val reason: AdjustmentReason, val description: String?)

/** Cuánto aceptan los campos de monto: los centavos que caben en el tope, con 2 decimales. */
internal fun cleanAmount(text: String): String = text.replace(',', '.').filter { it.isDigit() || it == '.' }.take(12)

/**
 * El técnico propone cobrar de más (ANDROID-25): monto, motivo y, si el motivo es «Otro», qué es.
 * La API lo deja pendiente hasta que el cliente lo apruebe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposeAdjustmentSheet(
    isSaving: Boolean,
    error: ApiError?,
    onDismiss: () -> Unit,
    onSubmit: (AdjustmentDraft) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf(AdjustmentReason.ExtraMaterials) }
    var description by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val cents = MoneyRules.parseCents(amount)
    val amountError = when {
        cents == null || cents <= 0 -> stringResource(R.string.payment_amount_invalid)
        cents > MoneyRules.MAX_ADJUSTMENT_CENTS -> stringResource(R.string.payment_amount_too_high, MoneyRules.format(MoneyRules.MAX_ADJUSTMENT_CENTS))
        else -> null
    }
    val descriptionError = if (reason == AdjustmentReason.Other && description.isBlank()) {
        stringResource(R.string.payment_adjustment_description_required)
    } else {
        null
    }

    PaymentSheet(onDismiss = { if (!isSaving) onDismiss() }) {
        Text(stringResource(R.string.payment_adjustment_title), style = MaterialTheme.typography.titleLarge, color = Navy900)
        Text(stringResource(R.string.payment_adjustment_hint), style = MaterialTheme.typography.bodyMedium, color = Slate500)
        AuthTextField(
            value = amount,
            onValueChange = { amount = cleanAmount(it) },
            label = stringResource(R.string.payment_adjustment_amount),
            placeholder = "120",
            prefix = "$",
            keyboardType = KeyboardType.Decimal,
            error = amountError.takeIf { showErrors },
        )
        Text(stringResource(R.string.payment_adjustment_reason), style = MaterialTheme.typography.labelLarge, color = Navy900)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdjustmentReason.entries.forEach { option ->
                ChoiceChip(
                    text = adjustmentReasonLabel(option),
                    selected = option == reason,
                    onClick = { reason = option },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AuthTextField(
            value = description,
            onValueChange = { description = it.take(500) },
            label = stringResource(
                if (reason == AdjustmentReason.Other) R.string.payment_adjustment_description else R.string.payment_adjustment_description_optional,
            ),
            placeholder = stringResource(R.string.payment_adjustment_description_placeholder),
            minLines = 2,
            error = descriptionError.takeIf { showErrors },
        )
        error?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
        FixoraPrimaryButton(
            text = stringResource(R.string.payment_adjustment_send),
            loading = isSaving,
            onClick = {
                showErrors = true
                if (cents != null && amountError == null && descriptionError == null) {
                    onSubmit(AdjustmentDraft(cents, reason, description.trim().takeIf { it.isNotEmpty() }))
                }
            },
        )
    }
}

/** El proveedor dice que no le llegó el pago; el motivo es opcional y se le muestra al cliente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RejectPaymentSheet(
    isSaving: Boolean,
    error: ApiError?,
    onDismiss: () -> Unit,
    onSubmit: (reason: String?) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    PaymentSheet(onDismiss = { if (!isSaving) onDismiss() }) {
        Text(stringResource(R.string.payment_reject_title), style = MaterialTheme.typography.titleLarge, color = Navy900)
        Text(stringResource(R.string.payment_reject_hint), style = MaterialTheme.typography.bodyMedium, color = Slate500)
        AuthTextField(
            value = reason,
            onValueChange = { reason = it.take(500) },
            label = stringResource(R.string.payment_reject_reason),
            placeholder = stringResource(R.string.payment_reject_reason_placeholder),
            minLines = 2,
        )
        error?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
        FixoraPrimaryButton(
            text = stringResource(R.string.payment_reject_send),
            loading = isSaving,
            onClick = { onSubmit(reason.trim().takeIf { it.isNotEmpty() }) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = White,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            content()
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Navy900,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Navy900 else Mist200, RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp)
            .fillMaxWidth(),
    )
}
