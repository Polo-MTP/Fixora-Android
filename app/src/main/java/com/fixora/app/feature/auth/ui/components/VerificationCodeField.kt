package com.fixora.app.feature.auth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.White

/**
 * Código de verificación en casillas. Es un solo campo de texto dibujado como [length] cajas:
 * así funcionan solos el teclado numérico, borrar y pegar el código completo.
 */
@Composable
fun VerificationCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    length: Int = 6,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = code,
        onValueChange = { onCodeChange(it.filter(Char::isDigit).take(length)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        decorationBox = { innerTextField ->
            // El texto real queda invisible; lo que se ve son las casillas.
            Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(length) { index ->
                    val active = focused && (index == code.length || (index == length - 1 && code.length == length))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 48.dp, height = 58.dp)
                            .background(White, RoundedCornerShape(14.dp))
                            .border(1.5.dp, if (active) Navy800 else Mist200, RoundedCornerShape(14.dp)),
                    ) {
                        Text(
                            text = code.getOrNull(index)?.toString().orEmpty(),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            color = Navy900,
                        )
                    }
                }
            }
        },
    )
}
