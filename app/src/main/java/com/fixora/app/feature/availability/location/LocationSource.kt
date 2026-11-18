package com.fixora.app.feature.availability.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.fixora.app.core.gms.awaitOrNull
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

data class Coordinates(val latitude: Double, val longitude: Double)

/** Ubicación del celular. Interfaz para probar sin GPS. */
interface LocationSource {
    /** Basta la ubicación aproximada; con la precisa el técnico aparece mejor en el mapa. */
    fun hasPermission(): Boolean

    /** Ubicación actual, o null si está apagada o no se pudo obtener. */
    suspend fun current(): Coordinates?

    /** Ubicaciones cada [intervalMs] mientras se escuche; vacío sin permiso. */
    fun updates(intervalMs: Long): Flow<Coordinates>
}

@Singleton
class FusedLocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun hasPermission(): Boolean = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // Revisado con hasPermission().
    override suspend fun current(): Coordinates? {
        if (!hasPermission()) return null
        val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).awaitOrNull()
            ?: client.lastLocation.awaitOrNull()
        return location?.let { Coordinates(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission") // Revisado con hasPermission().
    override fun updates(intervalMs: Long): Flow<Coordinates> {
        if (!hasPermission()) return emptyFlow()
        return callbackFlow {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { trySend(Coordinates(it.latitude, it.longitude)) }
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            awaitClose { client.removeLocationUpdates(callback) }
        }
    }

    companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
