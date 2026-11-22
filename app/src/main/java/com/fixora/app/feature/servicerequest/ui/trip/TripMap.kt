package com.fixora.app.feature.servicerequest.ui.trip

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.fixora.app.core.designsystem.theme.Navy800
import com.fixora.app.core.designsystem.theme.Orange500
import com.fixora.app.core.designsystem.theme.White
import com.fixora.app.feature.availability.location.Coordinates
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val ROUTE_SOURCE = "route"
private const val DESTINATION_SOURCE = "destination"
private const val TECHNICIAN_SOURCE = "technician"

/**
 * Mapa del traslado (ANDROID-20): la ruta en azul marino, el domicilio del cliente en naranja y el
 * técnico. La cámara encuadra a ambos al abrir y cuando aparece la ruta; después el técnico puede
 * moverla a su gusto. [topPadding] y [bottomPadding] dejan libre lo que tapan las tarjetas.
 */
@Composable
fun TripMap(
    styleUrl: String,
    destination: Coordinates?,
    technician: Coordinates?,
    route: List<Coordinates>,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    TripMapLifecycle(mapView)

    LaunchedEffect(mapView, styleUrl) {
        mapView.getMapAsync { loaded ->
            map = loaded
            loaded.uiSettings.isRotateGesturesEnabled = false
            loaded.uiSettings.isTiltGesturesEnabled = false
            loaded.uiSettings.isCompassEnabled = false
            loaded.setStyle(Style.Builder().fromUri(styleUrl)) { ready ->
                ready.addSource(GeoJsonSource(ROUTE_SOURCE))
                ready.addSource(GeoJsonSource(DESTINATION_SOURCE))
                ready.addSource(GeoJsonSource(TECHNICIAN_SOURCE))
                ready.addLayer(
                    LineLayer("route-line", ROUTE_SOURCE).withProperties(
                        PropertyFactory.lineColor(Navy800.toArgb()),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                ready.addLayer(
                    CircleLayer("destination-halo", DESTINATION_SOURCE).withProperties(
                        PropertyFactory.circleRadius(24f),
                        PropertyFactory.circleColor(Orange500.toArgb()),
                        PropertyFactory.circleOpacity(0.2f),
                    ),
                )
                ready.addLayer(
                    CircleLayer("destination-dot", DESTINATION_SOURCE).withProperties(
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleColor(Orange500.toArgb()),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor(White.toArgb()),
                    ),
                )
                ready.addLayer(
                    CircleLayer("technician-dot", TECHNICIAN_SOURCE).withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor(Navy800.toArgb()),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor(White.toArgb()),
                    ),
                )
                style = ready
            }
        }
    }

    LaunchedEffect(style, route) {
        val ready = style ?: return@LaunchedEffect
        val features = if (route.size >= 2) {
            listOf(Feature.fromGeometry(LineString.fromLngLats(route.map { Point.fromLngLat(it.longitude, it.latitude) })))
        } else {
            emptyList()
        }
        ready.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(style, destination) {
        val ready = style ?: return@LaunchedEffect
        ready.getSourceAs<GeoJsonSource>(DESTINATION_SOURCE)?.setGeoJson(destination.toFeatures())
    }

    LaunchedEffect(style, technician) {
        val ready = style ?: return@LaunchedEffect
        ready.getSourceAs<GeoJsonSource>(TECHNICIAN_SOURCE)?.setGeoJson(technician.toFeatures())
    }

    val density = LocalDensity.current
    val topPx = with(density) { topPadding.roundToPx() }
    val bottomPx = with(density) { bottomPadding.roundToPx() }

    // Se encuadra al abrir, cuando aparece el técnico y cuando llega la ruta; no a cada paso.
    val framing = listOf(destination != null, technician != null, route.size >= 2)
    LaunchedEffect(map, framing, topPx, bottomPx) {
        val loaded = map ?: return@LaunchedEffect
        val points = (route + listOfNotNull(destination, technician)).map { LatLng(it.latitude, it.longitude) }
        val side = context.dp(48)
        when {
            points.isEmpty() -> Unit
            points.distinct().size == 1 ->
                loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 15.0))
            else -> loaded.easeCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().includes(points).build(),
                    side,
                    topPx + side,
                    side,
                    bottomPx + side,
                ),
                600,
            )
        }
        // Los créditos de OpenStreetMap deben verse: se suben para que no los tape la hoja.
        val margin = context.dp(8)
        loaded.uiSettings.setAttributionMargins(margin, 0, 0, bottomPx + margin)
        loaded.uiSettings.setLogoMargins(margin * 4, 0, 0, bottomPx + margin)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun Coordinates?.toFeatures(): FeatureCollection =
    FeatureCollection.fromFeatures(listOfNotNull(this?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }))

/** MapView necesita enterarse del ciclo de vida de la pantalla para pausar y liberar memoria. */
@Composable
private fun TripMapLifecycle(mapView: MapView) {
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
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
