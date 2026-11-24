package com.fixora.app.feature.servicerequest.ui.trip

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.ui.message
import com.fixora.app.feature.auth.ui.components.AuthTextField
import com.fixora.app.feature.payment.ui.NoticeTone
import com.fixora.app.feature.payment.ui.OutlineActionButton
import com.fixora.app.feature.payment.ui.PaymentNotice
import com.fixora.app.feature.payment.ui.PaymentSheet
import com.fixora.app.feature.servicerequest.data.EvidenceProblem
import com.fixora.app.feature.servicerequest.domain.CancelRules
import com.fixora.app.feature.servicerequest.domain.EvidenceKind
import com.fixora.app.feature.servicerequest.domain.PickedEvidence

/**
 * El técnico cancela un servicio ya aceptado (ANDROID-21): motivo obligatorio y evidencia con la
 * cámara o un archivo. Sin evidencia se le advierte que la cancelación puede penalizarse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancelServiceSheet(
    form: CancelForm,
    onReasonChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveEvidence: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PaymentSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.trip_cancel_title), style = MaterialTheme.typography.titleLarge, color = Navy900)
        Text(stringResource(R.string.trip_cancel_hint), style = MaterialTheme.typography.bodyMedium, color = Slate500)
        AuthTextField(
            value = form.reason,
            onValueChange = onReasonChange,
            label = stringResource(R.string.trip_cancel_reason),
            placeholder = stringResource(R.string.trip_cancel_reason_placeholder),
            minLines = 3,
            error = stringResource(R.string.trip_cancel_reason_required).takeIf { form.reasonMissing },
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.trip_cancel_evidence), style = MaterialTheme.typography.labelLarge, color = Navy900)
            Text(
                stringResource(R.string.trip_cancel_evidence_hint, CancelRules.MAX_EVIDENCE),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
        }
        form.evidence.forEach { item ->
            EvidenceRow(item, enabled = !form.isSending, onRemove = { onRemoveEvidence(item.id) })
        }
        if (form.evidence.size < CancelRules.MAX_EVIDENCE) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineActionButton(
                    text = stringResource(R.string.trip_cancel_take_photo),
                    onClick = onTakePhoto,
                    enabled = form.canAddEvidence,
                    modifier = Modifier.weight(1f),
                )
                OutlineActionButton(
                    text = stringResource(R.string.trip_cancel_pick_file),
                    onClick = onPickFile,
                    enabled = form.canAddEvidence,
                    loading = form.isReading,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        form.problem?.let { Text(evidenceProblemText(it), style = MaterialTheme.typography.bodySmall, color = ErrorRed) }

        if (form.withoutEvidence) {
            PaymentNotice(
                icon = R.drawable.ic_info,
                title = stringResource(R.string.trip_cancel_penalty_title),
                text = stringResource(R.string.trip_cancel_penalty_text),
                tone = NoticeTone.Warning,
            )
        }
        form.error?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
        FixoraPrimaryButton(
            text = stringResource(R.string.trip_cancel_confirm),
            onClick = onConfirm,
            loading = form.isSending,
            enabled = !form.isReading,
        )
    }
}

@Composable
internal fun EvidenceRow(item: PickedEvidence, enabled: Boolean, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Mist200, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
    ) {
        Icon(
            painterResource(
                when (item.kind) {
                    EvidenceKind.Photo -> R.drawable.ic_image
                    EvidenceKind.Document, EvidenceKind.Video -> R.drawable.ic_attach_file
                },
            ),
            contentDescription = null,
            tint = Navy800,
            modifier = Modifier.size(20.dp),
        )
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Navy900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val description = stringResource(R.string.evidence_remove, item.name)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onRemove),
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = description, tint = Slate500, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun evidenceProblemText(problem: EvidenceProblem): String = when (problem) {
    EvidenceProblem.WrongType -> stringResource(R.string.trip_cancel_evidence_wrong_type)
    EvidenceProblem.TooLarge -> stringResource(R.string.trip_cancel_evidence_too_large)
    EvidenceProblem.Unreadable -> stringResource(R.string.trip_cancel_evidence_unreadable)
}
