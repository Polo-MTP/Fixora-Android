package com.fixora.app.feature.auth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraTextLink
import com.fixora.app.core.designsystem.theme.Slate500
import kotlinx.coroutines.delay

/** Segundos que la API pide esperar entre un envío del código y el siguiente. */
const val RESEND_SECONDS = 60

/**
 * Cuenta regresiva para poder pedir otro código; al llegar a cero muestra «Reenviar código». Se
 * reinicia cada vez que cambia [round] (se envió un código nuevo).
 */
@Composable
fun ResendCountdown(round: Int, startSeconds: Int, onResend: () -> Unit) {
    var secondsLeft by rememberSaveable(round) { mutableIntStateOf(startSeconds) }
    LaunchedEffect(round) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (secondsLeft > 0) {
            Text(
                text = stringResource(R.string.verify_resend_in, "%d:%02d".format(secondsLeft / 60, secondsLeft % 60)),
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500,
            )
        } else {
            Text("${stringResource(R.string.verify_not_received)} ", style = MaterialTheme.typography.bodyMedium, color = Slate500)
            FixoraTextLink(stringResource(R.string.verify_resend), onClick = onResend)
        }
    }
}
