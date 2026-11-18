package com.fixora.app.feature.portfolio.ui.certificates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.fixora.app.core.designsystem.component.FixoraCard
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.AccountScaffold
import com.fixora.app.feature.account.ui.components.FormErrorText
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.portfolio.data.DocumentProblem
import com.fixora.app.feature.portfolio.domain.Certificate
import com.fixora.app.feature.portfolio.domain.CertificateStatus
import com.fixora.app.feature.portfolio.domain.PortfolioRules

/** Certificados del proveedor (ANDROID-8): subir, ver su revisión y elegir cuáles se muestran. */
@Composable
fun CertificatesScreen(
    onBack: () -> Unit,
    viewModel: CertificatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // Selector de documentos del sistema: PDF o imagen, sin pedir permiso de almacenamiento.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onFilePicked(uri)
    }

    CertificatesContent(
        state = state,
        onBack = onBack,
        onUpload = { pickFile.launch(PortfolioRules.CERTIFICATE_MIME_TYPES) },
        onToggle = viewModel::setVisibility,
        onRetry = viewModel::refresh,
    )
}

@Composable
private fun CertificatesContent(
    state: CertificatesUiState,
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onToggle: (Certificate, Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    AccountScaffold(
        title = stringResource(R.string.certificates_title),
        onBack = onBack,
        bottomBar = {
            FixoraPrimaryButton(
                text = stringResource(R.string.certificates_upload),
                onClick = onUpload,
                loading = state.isUploading,
            )
        },
    ) {
        Text(stringResource(R.string.certificates_hint), style = MaterialTheme.typography.bodySmall, color = Slate500)

        if (state.justUploaded) {
            Text(stringResource(R.string.certificates_uploaded), style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
        }
        state.fileProblem?.let { FormErrorText(it.message()) }
        state.uploadError?.let { FormErrorText(it.message()) }

        when {
            state.loadError != null && state.certificates.isEmpty() -> LoadErrorNotice(state.loadError, onRetry = onRetry)
            !state.isLoading && state.certificates.isEmpty() ->
                Text(stringResource(R.string.certificates_empty), style = MaterialTheme.typography.bodyMedium, color = Slate500)
            else -> state.certificates.forEach { certificate ->
                CertificateCard(
                    certificate = certificate,
                    toggling = state.togglingId == certificate.id,
                    onToggle = { onToggle(certificate, it) },
                )
            }
        }
        state.toggleError?.let { FormErrorText(it.message()) }
    }
}

@Composable
private fun CertificateCard(certificate: Certificate, toggling: Boolean, onToggle: (Boolean) -> Unit) {
    val approved = certificate.status == CertificateStatus.Approved
    FixoraCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
            Text(
                text = certificate.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Navy900,
            )
            certificate.uploadDate?.let {
                Text(stringResource(R.string.certificates_uploaded_on, it), style = MaterialTheme.typography.labelSmall, color = Slate500)
            }
            Text(
                text = certificate.status.label(),
                style = MaterialTheme.typography.bodySmall,
                color = when (certificate.status) {
                    CertificateStatus.Approved -> SuccessGreen
                    CertificateStatus.Rejected -> ErrorRed
                    CertificateStatus.Pending -> Slate500
                },
            )
            certificate.comment?.takeIf { certificate.status == CertificateStatus.Rejected }?.let {
                Text(stringResource(R.string.certificates_reason, it), style = MaterialTheme.typography.bodySmall, color = Slate500)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.certificates_show_in_profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (approved) Navy900 else Slate500,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = certificate.isPublic,
                    onCheckedChange = onToggle,
                    enabled = approved && !toggling,
                    colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = SuccessGreen),
                )
            }
        }
    }
}

@Composable
private fun CertificateStatus.label(): String = stringResource(
    when (this) {
        CertificateStatus.Pending -> R.string.certificates_status_pending
        CertificateStatus.Approved -> R.string.certificates_status_approved
        CertificateStatus.Rejected -> R.string.certificates_status_rejected
    },
)

@Composable
private fun DocumentProblem.message(): String = stringResource(
    when (this) {
        DocumentProblem.WrongType -> R.string.certificates_wrong_type
        DocumentProblem.TooLarge -> R.string.certificates_too_large
        DocumentProblem.Unreadable -> R.string.certificates_unreadable
    },
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CertificatesPreview() {
    FixoraTheme {
        CertificatesContent(
            state = CertificatesUiState(
                isLoading = false,
                certificates = listOf(
                    Certificate("1", "Certificaciones o Títulos Técnicos", CertificateStatus.Approved, null, true, "2026-11-10"),
                    Certificate("2", "Certificaciones o Títulos Técnicos", CertificateStatus.Pending, null, false, "2026-11-18"),
                    Certificate("3", "Certificaciones o Títulos Técnicos", CertificateStatus.Rejected, "La imagen no se lee.", false, "2026-11-01"),
                ),
            ),
            onBack = {}, onUpload = {}, onToggle = { _, _ -> }, onRetry = {},
        )
    }
}
