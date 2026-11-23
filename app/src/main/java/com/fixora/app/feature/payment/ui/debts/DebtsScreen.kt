package com.fixora.app.feature.payment.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.payment.domain.Debt
import com.fixora.app.feature.payment.domain.Debts
import com.fixora.app.feature.payment.domain.MoneyRules
import com.fixora.app.feature.payment.domain.PaymentSummary
import com.fixora.app.feature.payment.ui.OutlineActionButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lista de adeudos. El proveedor ve lo que le deben y puede recordarle al cliente (ANDROID-25);
 * el cliente ve lo que debe. Tocar uno abre su solicitud para confirmar o registrar el pago.
 */
@Composable
fun DebtsScreen(
    asProvider: Boolean,
    onBack: () -> Unit,
    onOpenRequest: (requestId: String) -> Unit,
    viewModel: DebtsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    DebtsContent(
        state = state,
        asProvider = asProvider,
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenRequest = onOpenRequest,
        onRemind = viewModel::remind,
    )
}

@Composable
private fun DebtsContent(
    state: DebtsUiState,
    asProvider: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenRequest: (String) -> Unit,
    onRemind: (String) -> Unit,
) {
    AccountScaffold(
        title = stringResource(if (asProvider) R.string.debts_title_provider else R.string.debts_title_client),
        onBack = onBack,
    ) {
        val debts = state.debts
        val error = state.loadError
        when {
            debts != null -> {
                TotalCard(debts, asProvider)
                if (debts.debts.isEmpty()) {
                    Text(
                        stringResource(if (asProvider) R.string.debts_empty_provider else R.string.debts_empty_client),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                    )
                }
                debts.debts.forEach { debt ->
                    DebtCard(
                        debt = debt,
                        asProvider = asProvider,
                        reminding = state.remindingId == debt.requestId,
                        reminded = state.remindedId == debt.requestId,
                        reminderError = state.reminderError?.takeIf { it.first == debt.requestId }?.second?.message(),
                        onOpen = { onOpenRequest(debt.requestId) },
                        onRemind = { onRemind(debt.requestId) },
                    )
                }
            }
            error != null -> LoadErrorNotice(error, onRetry = onRetry)
            else -> CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TotalCard(debts: Debts, asProvider: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Navy900)
            .padding(20.dp),
    ) {
        Icon(painterResource(R.drawable.ic_receipt), contentDescription = null, tint = White, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (asProvider) R.string.debts_total_provider else R.string.debts_total_client),
                style = MaterialTheme.typography.labelLarge,
                color = White.copy(alpha = 0.7f),
            )
            Text(
                MoneyRules.format(debts.totalDue),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = White,
            )
        }
        Text(
            pluralCount(debts.debts.size),
            style = MaterialTheme.typography.labelLarge,
            color = White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun pluralCount(count: Int): String =
    if (count == 1) stringResource(R.string.debts_count_one) else stringResource(R.string.debts_count, count)

@Composable
private fun DebtCard(
    debt: Debt,
    asProvider: Boolean,
    reminding: Boolean,
    reminded: Boolean,
    reminderError: String?,
    onOpen: () -> Unit,
    onRemind: () -> Unit,
) {
    FixoraCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        debt.category.ifBlank { stringResource(R.string.request_service) } + " · #" + debt.requestId,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Navy900,
                    )
                    debt.completedAt?.let {
                        Text(stringResource(R.string.debts_finished_on, formatDate(it)), style = MaterialTheme.typography.bodySmall, color = Slate500)
                    }
                }
                Text(
                    MoneyRules.format(debt.summary.balanceDue),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Navy900,
                )
            }
            val paid = MoneyRules.toCents(debt.summary.paid)
            if (paid > 0) {
                Text(
                    stringResource(R.string.debts_paid_of, MoneyRules.format(paid), MoneyRules.format(debt.summary.totalAmount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                )
            }
            if (debt.summary.awaitsConfirmation) {
                StatusLine(
                    stringResource(
                        if (asProvider) R.string.debts_awaiting_provider else R.string.debts_awaiting_client,
                        MoneyRules.format(debt.summary.pendingConfirmation),
                    ),
                    Orange500,
                )
            }
            if (asProvider) {
                ReminderRow(debt, reminding, reminded, reminderError, onRemind)
            }
        }
    }
}

/** Recordar al cliente: cuántos van y, si ya se mandó uno hoy, desde cuándo se puede otro. */
@Composable
private fun ReminderRow(debt: Debt, reminding: Boolean, reminded: Boolean, error: String?, onRemind: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reminded) StatusLine(stringResource(R.string.debts_reminder_sent), SuccessGreen)
        val next = debt.nextReminderAt
        when {
            next != null -> Text(
                stringResource(R.string.debts_reminder_next, formatDateTime(next)),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
            debt.remindersSent > 0 -> Text(
                if (debt.remindersSent == 1) stringResource(R.string.debts_reminders_one) else stringResource(R.string.debts_reminders, debt.remindersSent),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
        if (!debt.summary.awaitsConfirmation) {
            OutlineActionButton(
                text = stringResource(R.string.debts_remind),
                onClick = onRemind,
                loading = reminding,
                enabled = debt.canRemind,
            )
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp)),
        )
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Navy900)
    }
}

/**
 * Tarjeta del inicio con lo que falta por cobrar (o pagar), como «$580 por cobrar · 2 adeudos» del
 * diseño. Sin adeudos no se muestra.
 */
@Composable
fun DebtsHomeCard(
    asProvider: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebtsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val debts = state.debts ?: return
    if (debts.debts.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Orange500.copy(alpha = 0.07f))
            .border(1.dp, Orange500.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(painterResource(R.drawable.ic_receipt), contentDescription = null, tint = Orange500, modifier = Modifier.size(22.dp))
        Text(
            stringResource(
                if (asProvider) R.string.debts_home_provider else R.string.debts_home_client,
                MoneyRules.format(debts.totalDue),
            ) + " · " + pluralCount(debts.debts.size),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Navy900,
            modifier = Modifier.weight(1f),
        )
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Orange500, modifier = Modifier.size(20.dp))
    }
}

private val DateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-MX"))
private val DateTimeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es-MX"))

private fun formatDate(instant: Instant): String = DateFormat.format(instant.atZone(ZoneId.systemDefault()))

private fun formatDateTime(instant: Instant): String = DateTimeFormat.format(instant.atZone(ZoneId.systemDefault()))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DebtsPreview() {
    FixoraTheme {
        DebtsContent(
            state = DebtsUiState(
                isLoading = false,
                debts = Debts(
                    totalDue = "580.00",
                    debts = listOf(
                        Debt("62", "Plomería", Instant.parse("2026-11-22T18:00:00Z"), PaymentSummary("470.00", "400.00", "0.00", "70.00", true), 1, Instant.parse("2026-11-23T10:00:00Z"), Instant.parse("2026-11-24T10:00:00Z")),
                        Debt("63", "Electricidad", Instant.parse("2026-11-21T18:00:00Z"), PaymentSummary("510.00", "0.00", "0.00", "510.00", true), 0, null, null),
                    ),
                ),
            ),
            asProvider = true,
            onBack = {}, onRetry = {}, onOpenRequest = {}, onRemind = {},
        )
    }
}
