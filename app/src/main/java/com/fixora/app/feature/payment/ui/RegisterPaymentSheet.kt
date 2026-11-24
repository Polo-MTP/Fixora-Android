package com.fixora.app.feature.payment.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.ui.message
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.payment.domain.MoneyRules
import com.fixora.app.feature.payment.domain.PaymentMethod

/** Lo que registra el cliente al pagar. */
data class PaymentDraft(val method: PaymentMethod, val amountCents: Long, val reference: String?)

/**
 * El cliente registra su pago (ANDROID-24): efectivo o transferencia y cuánto. Por defecto todo lo
 * que falta; puede pagar una parte y lo demás queda como adeudo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterPaymentSheet(
    balanceCents: Long,
    isSaving: Boolean,
    error: ApiError?,
    onDismiss: () -> Unit,
    onSubmit: (PaymentDraft) -> Unit,
) {
    var method by rememberSaveable { mutableStateOf(PaymentMethod.Cash) }
    var amount by rememberSaveable { mutableStateOf(MoneyRules.toInput(balanceCents)) }
    var reference by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val cents = MoneyRules.parseCents(amount)
    val amountError = when {
        cents == null || cents <= 0 -> stringResource(R.string.payment_amount_invalid)
        cents > balanceCents -> stringResource(R.string.payment_amount_over_balance, MoneyRules.format(balanceCents))
        else -> null
    }

    PaymentSheet(onDismiss = { if (!isSaving) onDismiss() }) {
        Text(stringResource(R.string.payment_register_title), style = MaterialTheme.typography.titleLarge, color = Navy900)
        Text(stringResource(R.string.payment_register_hint), style = MaterialTheme.typography.bodyMedium, color = Slate500)
        Text(stringResource(R.string.payment_register_method), style = MaterialTheme.typography.labelLarge, color = Navy900)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentMethod.entries.forEach { option ->
                ChoiceChip(
                    text = paymentMethodLabel(option),
                    selected = option == method,
                    onClick = { method = option },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AuthTextField(
            value = amount,
            onValueChange = { amount = cleanAmount(it) },
            label = stringResource(R.string.payment_register_amount),
            placeholder = MoneyRules.toInput(balanceCents),
            prefix = "$",
            keyboardType = KeyboardType.Decimal,
            error = amountError.takeIf { showErrors },
        )
        val partialDebt = cents?.let { balanceCents - it }?.takeIf { amountError == null && it > 0 }
        partialDebt?.let {
            Text(
                stringResource(R.string.payment_register_partial, MoneyRules.format(it)),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
        }
        if (method == PaymentMethod.Transfer) {
            AuthTextField(
                value = reference,
                onValueChange = { reference = it.take(255) },
                label = stringResource(R.string.payment_register_reference),
                placeholder = stringResource(R.string.payment_register_reference_placeholder),
            )
        }
        error?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
        FixoraPrimaryButton(
            text = stringResource(R.string.payment_register_send),
            loading = isSaving,
            onClick = {
                showErrors = true
                if (cents != null && amountError == null) {
                    onSubmit(PaymentDraft(method, cents, reference.trim().takeIf { it.isNotEmpty() }))
                }
            },
        )
    }
}
