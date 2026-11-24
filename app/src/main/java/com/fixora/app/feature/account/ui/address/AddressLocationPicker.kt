package com.fixora.app.feature.account.ui.address

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fixora.app.BuildConfig
import com.fixora.app.R
import com.fixora.app.core.designsystem.component.FixoraPrimaryButton
import com.fixora.app.core.designsystem.theme.Mist50
import com.fixora.app.core.designsystem.theme.Navy900
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.Slate500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.feature.account.ui.components.BackButton
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.map.ui.MapLifecycle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Altura aproximada del panel de abajo, para que no tape los créditos de OpenStreetMap. */
private const val BOTTOM_PANEL_DP = 104

/**
 * Pantalla completa para marcar la dirección en el mapa (ANDROID-41). El pin se queda fijo en el
 * centro y se mueve el mapa: es más preciso con el dedo que arrastrar un marcador.
 */
@Composable
fun AddressLocationPicker(
    picker: LocationPicker,
    onMyLocation: () -> Unit,
    onConfirm: (Coordinates) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        val mapView = remember {
            MapLibre.getInstance(context)
            MapView(context).apply { onCreate(Bundle()) }
        }
        var map by remember { mutableStateOf<MapLibreMap?>(null) }
        var center by remember { mutableStateOf(picker.start) }

        MapLifecycle(mapView)

        LaunchedEffect(mapView) {
            mapView.getMapAsync { loaded ->
                loaded.uiSettings.isRotateGesturesEnabled = false
                loaded.uiSettings.isTiltGesturesEnabled = false
                loaded.uiSettings.isCompassEnabled = false
                val margin = context.dp(8)
                val bottom = context.dp(BOTTOM_PANEL_DP)
                loaded.uiSettings.setAttributionMargins(margin, 0, 0, bottom + margin)
                loaded.uiSettings.setLogoMargins(margin * 4, 0, 0, bottom + margin)
                loaded.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(picker.start.latitude, picker.start.longitude))
                    .zoom(picker.zoom)
                    .build()
                loaded.addOnCameraIdleListener {
                    loaded.cameraPosition.target?.let { center = Coordinates(it.latitude, it.longitude) }
                }
                loaded.setStyle(Style.Builder().fromUri(BuildConfig.MAP_STYLE_URL))
                map = loaded
            }
        }

        // «Mi ubicación» o la ubicación del celular que llegó después de abrir.
        LaunchedEffect(map, picker.recenterId) {
            val loaded = map ?: return@LaunchedEffect
            val target = picker.recenter ?: return@LaunchedEffect
            loaded.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), LocationPicker.PRECISE_ZOOM),
                600,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Mist50),
        ) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            // La punta del pin queda justo en el centro del mapa; el punto oscuro marca el lugar exacto.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Navy900.copy(alpha = 0.45f)),
            )
            Icon(
                painter = painterResource(R.drawable.ic_place),
                contentDescription = null,
                tint = Orange500,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-18).dp)
                    .size(44.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                BackButton(onDismiss)
                Column(
                    Modifier
                        .weight(1f)
                        .shadow(4.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(White)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.address_location_picker_title),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Navy900,
                    )
                    Text(
                        stringResource(R.string.address_location_picker_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                val description = stringResource(R.string.map_my_location)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(end = 16.dp, bottom = 12.dp)
                        .size(48.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(White)
                        .clickable(onClickLabel = description, role = Role.Button, onClick = onMyLocation)
                        .semantics { contentDescription = description },
                ) {
                    Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, tint = Navy900, modifier = Modifier.size(24.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(White, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    FixoraPrimaryButton(
                        text = stringResource(R.string.address_location_confirm),
                        onClick = { onConfirm(center) },
                    )
                }
            }
        }
    }
}

/**
 * El mapa abierto para marcar la dirección. [recenter] mueve la cámara cada vez que cambia
 * [recenterId], aunque sea al mismo punto (el usuario pudo haber movido el mapa entre tanto).
 */
data class LocationPicker(
    val start: Coordinates,
    val zoom: Double,
    val recenter: Coordinates? = null,
    val recenterId: Int = 0,
) {
    companion object {
        /** Se ven las calles y los números de las casas. */
        const val PRECISE_ZOOM = 17.0

        /** Sin ubicación conocida: la ciudad completa, para buscar con el dedo. */
        const val CITY_ZOOM = 11.0
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
