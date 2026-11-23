package com.fixora.app.feature.requestform.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.fixora.app.feature.availability.location.Coordinates
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.profile.data.PhotoReader
import com.fixora.app.feature.requestform.domain.CurrentAddress
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** Convierte la ubicación del celular en una dirección (calle, colonia, C.P.). Interfaz para probar. */
fun interface CurrentAddressResolver {
    /** null sin permiso, sin señal o si el celular no pudo leer la dirección. */
    suspend fun resolve(): CurrentAddress?
}

/** Lee la foto elegida como JPEG reducido. Interfaz para probar el formulario sin Android. */
fun interface RequestPhotoReader {
    suspend fun read(uri: android.net.Uri): ByteArray?
}

class GeocoderAddressResolver(
    private val context: Context,
    private val location: LocationSource,
) : CurrentAddressResolver {

    override suspend fun resolve(): CurrentAddress? {
        if (!location.hasPermission() || !Geocoder.isPresent()) return null
        val here = location.current() ?: return null
        val address = geocode(here) ?: return null
        val street = address.thoroughfare ?: return null
        return CurrentAddress(
            street = street,
            number = address.subThoroughfare.orEmpty(),
            colony = address.subLocality ?: address.locality.orEmpty(),
            municipality = address.locality ?: address.subAdminArea.orEmpty(),
            state = address.adminArea.orEmpty(),
            postalCode = address.postalCode.orEmpty(),
            position = here,
        ).takeIf { it.colony.isNotBlank() && it.municipality.isNotBlank() && it.state.isNotBlank() && it.postalCode.isNotBlank() }
    }

    private suspend fun geocode(at: Coordinates): Address? {
        val geocoder = Geocoder(context, Locale.forLanguageTag("es-MX"))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(at.latitude, at.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = continuation.resume(null)
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(at.latitude, at.longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RequestFormDeviceModule {
    @Provides
    fun provideCurrentAddressResolver(@ApplicationContext context: Context, location: LocationSource): CurrentAddressResolver =
        GeocoderAddressResolver(context, location)

    @Provides
    fun provideRequestPhotoReader(reader: PhotoReader): RequestPhotoReader = RequestPhotoReader(reader::readAsJpeg)
}
