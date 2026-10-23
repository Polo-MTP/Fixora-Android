package com.fixora.app.feature.auth.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.ui.SystemBarsAppearance

/** Confirmación al terminar el registro. */
@Composable
fun RegisterSuccessScreen(onContinue: () -> Unit) {
    SystemBarsAppearance(lightIcons = false)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(White)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .background(SuccessGreen.copy(alpha = 0.1f), CircleShape),
        ) {
            Text("✓", fontSize = 46.sp, color = SuccessGreen)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.register_success_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Navy900,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.register_success_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        FixoraPrimaryButton(
            text = stringResource(R.string.register_success_continue),
            onClick = onContinue,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RegisterSuccessPreview() {
    FixoraTheme { RegisterSuccessScreen(onContinue = {}) }
}
