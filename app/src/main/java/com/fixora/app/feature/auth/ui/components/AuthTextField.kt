package com.fixora.app.feature.auth.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.ErrorRed
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White

/**
 * Campo de los formularios de cuenta: etiqueta arriba, caja blanca redondeada y el error abajo.
 * [onFocusLost] avisa cuando el usuario sale del campo, para validar en ese momento.
 * Con [onClick] el campo es de solo lectura y abre algo al tocarlo (por ejemplo, un calendario).
 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    onFocusLost: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    prefix: String? = null,
    onClick: (() -> Unit)? = null,
) {
    var hadFocus by remember { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Navy900)
        Spacer(Modifier.height(6.dp))
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                readOnly = onClick != null,
                isError = error != null,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)) },
                prefix = prefix?.let { { Text("$it ", fontWeight = FontWeight.SemiBold) } },
                visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                ),
                trailingIcon = if (isPassword) {
                    {
                        Text(
                            text = stringResource(if (showPassword) R.string.auth_password_hide else R.string.auth_password_show),
                            style = MaterialTheme.typography.labelLarge,
                            color = Slate500,
                            modifier = Modifier
                                .clickable(role = Role.Button) { showPassword = !showPassword }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    null
                },
                supportingText = error?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = White,
                    unfocusedContainerColor = White,
                    errorContainerColor = White,
                    focusedBorderColor = Navy800,
                    unfocusedBorderColor = Mist200,
                    errorBorderColor = ErrorRed,
                    cursorColor = Navy800,
                    focusedTextColor = Navy900,
                    unfocusedTextColor = Navy900,
                    errorTextColor = Navy900,
                    focusedPlaceholderColor = Slate500.copy(alpha = 0.7f),
                    unfocusedPlaceholderColor = Slate500.copy(alpha = 0.7f),
                    errorPlaceholderColor = Slate500.copy(alpha = 0.7f),
                    focusedPrefixColor = Slate500,
                    unfocusedPrefixColor = Slate500,
                    errorPrefixColor = Slate500,
                    errorSupportingTextColor = ErrorRed,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (hadFocus && !it.isFocused) onFocusLost()
                        hadFocus = it.isFocused
                    },
            )
            // Capa transparente encima: en los campos de solo lectura, el toque abre el selector.
            if (onClick != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(role = Role.Button, onClick = onClick),
                )
            }
        }
    }
}
