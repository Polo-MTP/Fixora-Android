package com.fixora.app.feature.map.ui

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.map.domain.MapProvider
import com.fixora.app.feature.map.domain.ServiceCategory
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val PROVIDERS_SOURCE = "providers"
private const val PROVIDERS_LAYER = "providers-layer"
private const val USER_SOURCE = "user"
private const val USER_HALO_LAYER = "user-halo"
private const val USER_DOT_LAYER = "user-dot"
private const val PROP_ID = "id"
private const val PROP_ICON = "icon"
private const val PROP_ORDER = "order"
private val UserBlue = android.graphics.Color.rgb(37, 99, 235)

/**
 * Mapa de MapLibre con los mapas de OpenFreeMap (ANDROID-10). Dibuja un marcador por proveedor y
 * el punto azul del usuario; al tocar un marcador avisa con [onSelect] (null al tocar el mapa).
 * [bottomPadding] deja libre la parte que tapa el panel de abajo.
 */
@Composable
fun ProvidersMap(
    styleUrl: String,
    providers: List<MapProvider>,
    category: ServiceCategory?,
    selectedId: String?,
    userLocation: Coordinates?,
    camera: CameraRequest?,
    bottomPadding: Dp,
    onSelect: (String?) -> Unit,
    onCameraIdle: (Coordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)

    MapLifecycle(mapView)

    LaunchedEffect(mapView, styleUrl) {
        mapView.getMapAsync { loaded ->
            map = loaded
            loaded.uiSettings.isRotateGesturesEnabled = false
            loaded.uiSettings.isTiltGesturesEnabled = false
            loaded.uiSettings.isCompassEnabled = false
            loaded.addOnMapClickListener { point ->
                val screen = loaded.projection.toScreenLocation(point)
                val feature = loaded.queryRenderedFeatures(screen, PROVIDERS_LAYER).firstOrNull()
                currentOnSelect(feature?.getStringProperty(PROP_ID))
                true
            }
            loaded.addOnCameraIdleListener {
                val target = loaded.cameraPosition.target ?: return@addOnCameraIdleListener
                currentOnCameraIdle(Coordinates(target.latitude, target.longitude))
            }
            loaded.setStyle(Style.Builder().fromUri(styleUrl)) { ready ->
                ready.addSource(GeoJsonSource(USER_SOURCE))
                ready.addSource(GeoJsonSource(PROVIDERS_SOURCE))
                ready.addLayer(
                    CircleLayer(USER_HALO_LAYER, USER_SOURCE).withProperties(
                        PropertyFactory.circleRadius(16f),
                        PropertyFactory.circleColor(UserBlue),
                        PropertyFactory.circleOpacity(0.2f),
                    ),
                )
                ready.addLayer(
                    CircleLayer(USER_DOT_LAYER, USER_SOURCE).withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor(UserBlue),
                        PropertyFactory.circleStrokeWidth(2.5f),
                        PropertyFactory.circleStrokeColor(White.toArgb()),
                    ),
                )
                ready.addLayer(
                    SymbolLayer(PROVIDERS_LAYER, PROVIDERS_SOURCE).withProperties(
                        PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        // El elegido se dibuja encima de los demás.
                        PropertyFactory.symbolSortKey(Expression.get(PROP_ORDER)),
                    ),
                )
                style = ready
            }
        }
    }

    // Marcadores: se vuelven a poner cuando cambian los proveedores, el servicio o el elegido.
    LaunchedEffect(style, providers, category, selectedId) {
        val ready = style ?: return@LaunchedEffect
        val features = providers.map { provider ->
            val look = provider.markerLook(category, selected = provider.id == selectedId)
            if (ready.getImage(look.imageId) == null) ready.addImage(look.imageId, look.toBitmap(context))
            Feature.fromGeometry(Point.fromLngLat(provider.position.longitude, provider.position.latitude)).apply {
                addStringProperty(PROP_ID, provider.id)
                addStringProperty(PROP_ICON, look.imageId)
                addNumberProperty(PROP_ORDER, if (look.selected) 1 else 0)
            }
        }
        ready.getSourceAs<GeoJsonSource>(PROVIDERS_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(style, userLocation) {
        val ready = style ?: return@LaunchedEffect
        val features = listOfNotNull(userLocation?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) })
        ready.getSourceAs<GeoJsonSource>(USER_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(map, camera?.id) {
        val loaded = map ?: return@LaunchedEffect
        val request = camera ?: return@LaunchedEffect
        loaded.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(request.center.latitude, request.center.longitude), request.zoom),
            600,
        )
    }

    val bottomPx = with(LocalDensity.current) { bottomPadding.roundToPx() }
    LaunchedEffect(map, bottomPx) {
        val loaded = map ?: return@LaunchedEffect
        // Los créditos de OpenStreetMap deben verse: se suben para que no los tape el panel.
        val margin = context.dp(8)
        loaded.uiSettings.setAttributionMargins(margin, 0, 0, bottomPx + margin)
        loaded.uiSettings.setLogoMargins(margin * 4, 0, 0, bottomPx + margin)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** MapView necesita enterarse del ciclo de vida de la pantalla para pausar y liberar memoria. */
@Composable
private fun MapLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Al salir de la pestaña: se detiene y se libera aunque la pantalla siga viva.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
