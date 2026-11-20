package com.fixora.app.feature.auth.ui.login

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.feature.auth.ui.components.AuthScaffold

/**
 * Aviso para el proveedor cuya solicitud sigue en revisión (ANDROID-4): la app es solo para
 * proveedores aprobados. Explica qué sigue y lleva al portal, donde corrige documentos observados.
 */
@Composable
internal fun ProviderInReviewContent(
    onBack: () -> Unit,
    onOpenPortal: () -> Unit,
) {
    AuthScaffold(
        title = stringResource(R.string.provider_review_title),
        subtitle = AnnotatedString(stringResource(R.string.provider_review_subtitle)),
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ReviewStep(
                icon = R.drawable.ic_schedule,
                title = stringResource(R.string.provider_review_step_review_title),
                text = stringResource(R.string.provider_review_step_review),
            )
            ReviewStep(
                icon = R.drawable.ic_bell,
                title = stringResource(R.string.provider_review_step_notice_title),
                text = stringResource(R.string.provider_review_step_notice),
            )
            ReviewStep(
                icon = R.drawable.ic_certificate,
                title = stringResource(R.string.provider_review_step_documents_title),
                text = stringResource(R.string.provider_review_step_documents),
            )
        }
        FixoraPrimaryButton(
            text = stringResource(R.string.provider_review_ok),
            onClick = onBack,
            modifier = Modifier.padding(top = 6.dp),
        )
        FixoraTextLink(
            text = stringResource(R.string.provider_review_portal),
            onClick = onOpenPortal,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun ReviewStep(@DrawableRes icon: Int, title: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Mist200, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(Orange500.copy(alpha = 0.1f), CircleShape),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Orange500, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Navy900)
            Text(text, style = MaterialTheme.typography.bodySmall, color = Slate500)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProviderInReviewPreview() {
    FixoraTheme { ProviderInReviewContent(onBack = {}, onOpenPortal = {}) }
}
