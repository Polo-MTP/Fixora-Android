package com.fixora.app.feature.availability.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
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

/**
 * La dirección escrita de un punto del mapa. Cualquier campo puede venir vacío: en México la
 * colonia y el número no siempre se conocen, por eso se le pide al usuario que la revise.
 */
data class GeocodedAddress(
    val street: String = "",
    val number: String = "",
    /** La colonia; vacía si el mapa no la sabe. */
    val colony: String = "",
    /** La ciudad (locality); a veces es lo único que se sabe del lugar. */
    val city: String = "",
    val municipality: String = "",
    val state: String = "",
    val postalCode: String = "",
)

/** Convierte un punto en su dirección escrita. Interfaz para probar sin Android. */
fun interface AddressGeocoder {
    /** null sin internet, sin el servicio en el celular o si no hay una dirección en ese punto. */
    suspend fun lookup(at: Coordinates): GeocodedAddress?
}

/** Usa el Geocoder del celular: no pide llave ni pasa por la API de Fixora. */
class AndroidAddressGeocoder(private val context: Context) : AddressGeocoder {

    override suspend fun lookup(at: Coordinates): GeocodedAddress? {
        if (!Geocoder.isPresent()) return null
        val address = geocode(at) ?: return null
        return GeocodedAddress(
            street = address.thoroughfare.orEmpty(),
            number = address.subThoroughfare.orEmpty(),
            colony = address.subLocality.orEmpty(),
            city = address.locality.orEmpty(),
            municipality = address.locality ?: address.subAdminArea.orEmpty(),
            state = address.adminArea.orEmpty(),
            postalCode = address.postalCode.orEmpty(),
        )
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
object AddressGeocoderModule {
    @Provides
    fun provideAddressGeocoder(@ApplicationContext context: Context): AddressGeocoder = AndroidAddressGeocoder(context)
}
