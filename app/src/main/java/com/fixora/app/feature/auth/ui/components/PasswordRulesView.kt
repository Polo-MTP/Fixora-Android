package com.fixora.app.feature.auth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.SuccessGreen
import com.fixora.app.feature.auth.domain.PasswordRules

/** Requisitos de la contraseña: se marcan en verde mientras se cumplen. */
@Composable
fun PasswordRulesView(rules: PasswordRules) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Rule(stringResource(R.string.register_rule_length), rules.minLength, Modifier.weight(1f))
            Rule(stringResource(R.string.register_rule_uppercase), rules.uppercase, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Rule(stringResource(R.string.register_rule_digit), rules.digit, Modifier.weight(1f))
            Rule(stringResource(R.string.register_rule_lowercase), rules.lowercase, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Rule(text: String, met: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = (if (met) "● " else "○ ") + text,
        style = MaterialTheme.typography.labelSmall,
        color = if (met) SuccessGreen else Slate500,
        modifier = modifier,
    )
}
