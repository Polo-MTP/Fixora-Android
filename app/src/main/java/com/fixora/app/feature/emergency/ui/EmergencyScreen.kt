package com.fixora.app.feature.emergency.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fixora.app.R
import com.fixora.app.core.designsystem.theme.FixoraTheme
import com.fixora.app.core.designsystem.theme.Mist200
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.ui.SystemBarsAppearance
import com.fixora.app.core.ui.message
import com.fixora.app.feature.account.ui.components.LoadErrorNotice
import com.fixora.app.feature.availability.location.FusedLocationSource
import com.fixora.app.feature.map.domain.ServiceCategory
import com.fixora.app.feature.map.ui.iconRes

private val EmergencyRed = Color(0xFFD32F2F)
private val WarningBackground = Color(0xFFFFF4E0)
private val WarningText = Color(0xFF8A4B00)
private val MapDark = Color(0xFF5B6B80)
private val UserBlue = Color(0xFF2563EB)

/** Confirmar la emergencia (ANDROID-16, Figma «Fixora - Confirmar Emergencia»). */
@Composable
fun EmergencyScreen(
    onCancel: () -> Unit,
    onSent: (requestId: String) -> Unit,
    viewModel: EmergencyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sentMessage = stringResource(R.string.emergency_sent)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        viewModel.onPermissionResult(results.values.any { it })
    }
    LaunchedEffect(Unit) { viewModel.requestPermission.collect { permission.launch(FusedLocationSource.LOCATION_PERMISSIONS) } }
    LaunchedEffect(state.createdRequestId) {
        state.createdRequestId?.let {
            Toast.makeText(context, sentMessage, Toast.LENGTH_SHORT).show()
            onSent(it)
        }
    }
    EmergencyContent(
        state = state,
        onCategory = viewModel::onCategorySelected,
        onRetryCategories = viewModel::retryCategories,
        onLocate = viewModel::locate,
        onSend = viewModel::send,
        onCancel = onCancel,
    )
}

@Composable
private fun EmergencyContent(
    state: EmergencyUiState,
    onCategory: (String) -> Unit,
    onRetryCategories: () -> Unit,
    onLocate: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    SystemBarsAppearance(lightIcons = true)
    Box(
        Modifier
            .fillMaxSize()
            .background(MapDark),
    ) {
        BackdropMap(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .statusBarsPadding()
                    .height(150.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(White)
                    .navigationBarsPadding(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 40.dp, height = 5.dp)
                            .background(Mist200, CircleShape),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .background(EmergencyRed.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, EmergencyRed.copy(alpha = 0.2f), CircleShape),
                    ) {
                        Icon(painterResource(R.drawable.ic_siren), contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(28.dp))
                    }
                    Text(
                        stringResource(R.string.emergency_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Navy900,
                    )
                    Text(stringResource(R.string.emergency_subtitle), style = MaterialTheme.typography.bodyMedium, color = Slate500)

                    Text(
                        stringResource(R.string.emergency_which_service),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Navy900,
                    )
                    val categoriesError = state.categoriesError
                    when {
                        state.categories.isNotEmpty() -> FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.categories.forEach { category ->
                                CategoryChip(category, category.id == state.categoryId) { onCategory(category.id) }
                            }
                        }
                        categoriesError != null -> LoadErrorNotice(categoriesError, onRetry = onRetryCategories)
                        else -> CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }

                    SummaryCard(state, onLocate)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(WarningBackground)
                            .padding(14.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = WarningText, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.emergency_warning), style = MaterialTheme.typography.bodySmall, color = WarningText)
                    }
                    state.sendError?.let { error ->
                        Text(error.emergencyMessage(), style = MaterialTheme.typography.bodySmall, color = EmergencyRed)
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp, top = 4.dp),
                ) {
                    Button(
                        onClick = onSend,
                        enabled = state.canSend,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmergencyRed,
                            contentColor = White,
                            disabledContainerColor = EmergencyRed.copy(alpha = 0.35f),
                            disabledContentColor = White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(color = White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                        } else {
                            Icon(painterResource(R.drawable.ic_siren), contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                stringResource(R.string.emergency_send),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.emergency_cancel),
                        style = MaterialTheme.typography.titleSmall,
                        color = Navy900,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = onCancel)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: ServiceCategory, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Navy900 else White)
            .border(1.dp, if (selected) Navy900 else Mist200, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Icon(painterResource(category.iconRes()), contentDescription = null, tint = if (selected) White else Navy800, modifier = Modifier.size(16.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) White else Navy900,
        )
    }
}

/** Lo que se va a mandar: el servicio y desde dónde (como la tarjeta del diseño). */
@Composable
private fun SummaryCard(state: EmergencyUiState, onLocate: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Mist200, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Navy900),
            ) {
                val icon = state.category?.iconRes() ?: R.drawable.ic_service_general
                Icon(painterResource(icon), contentDescription = null, tint = White, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.category?.name ?: stringResource(R.string.emergency_no_service),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (state.category != null) Navy900 else Slate500,
                )
                Text(stringResource(R.string.emergency_nearby), style = MaterialTheme.typography.bodySmall, color = Slate500)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Mist200),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, tint = UserBlue, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.emergency_your_location), style = MaterialTheme.typography.bodyMedium, color = Navy900, modifier = Modifier.weight(1f))
            when (state.location) {
                LocationStatus.Locating -> CircularProgressIndicator(color = Navy800, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                LocationStatus.Ready -> Text(stringResource(R.string.emergency_location_ready), style = MaterialTheme.typography.labelLarge, color = Slate500)
                LocationStatus.NoPermission, LocationStatus.Unavailable -> Text(
                    stringResource(R.string.emergency_location_activate),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = EmergencyRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onLocate)
                        .padding(4.dp),
                )
            }
        }
        if (state.location == LocationStatus.Unavailable || state.location == LocationStatus.NoPermission) {
            Text(
                stringResource(
                    if (state.location == LocationStatus.NoPermission) R.string.emergency_location_no_permission else R.string.emergency_location_off,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
        }
    }
}

/** El que no haya técnicos cerca se explica con qué hacer. */
@Composable
private fun ApiError.emergencyMessage(): String =
    if (this is ApiError.Http && code == "NO_TECHNICIANS_AVAILABLE") stringResource(R.string.emergency_no_technicians) else message()

/** Mapa de fondo, oscurecido como en el diseño, con la ubicación del cliente latiendo. */
@Composable
private fun BackdropMap(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ubicacion")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulso")
    Canvas(modifier) {
        val w = size.width
        val street = Color(0xFF6E7D91)
        val stroke = 26.dp.toPx()
        drawLine(street, Offset(w * 0.28f, 0f), Offset(w * 0.18f, 200.dp.toPx()), strokeWidth = stroke)
        drawLine(street, Offset(w * 0.62f, 0f), Offset(w * 0.68f, 200.dp.toPx()), strokeWidth = stroke)
        drawLine(street, Offset(0f, 60.dp.toPx()), Offset(w, 110.dp.toPx()), strokeWidth = stroke)
        val me = Offset(w * 0.24f, 150.dp.toPx())
        drawCircle(UserBlue.copy(alpha = 0.35f * (1f - pulse)), radius = 10.dp.toPx() + pulse * 26.dp.toPx(), center = me)
        drawCircle(White, radius = 11.dp.toPx(), center = me)
        drawCircle(UserBlue, radius = 8.dp.toPx(), center = me)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EmergencyPreview() {
    FixoraTheme {
        EmergencyContent(
            state = EmergencyUiState(
                categories = listOf(ServiceCategory("1", "Plomería", "plomeria"), ServiceCategory("2", "Electricidad", "electricidad")),
                categoryId = "1",
                location = LocationStatus.Ready,
            ),
            onCategory = {}, onRetryCategories = {}, onLocate = {}, onSend = {}, onCancel = {},
        )
    }
}
