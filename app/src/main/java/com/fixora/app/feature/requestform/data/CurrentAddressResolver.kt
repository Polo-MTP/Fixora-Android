package com.fixora.app.feature.requestform.data

import com.fixora.app.feature.availability.location.AddressGeocoder
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.profile.data.PhotoReader
import com.fixora.app.feature.requestform.domain.CurrentAddress
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
    private val location: LocationSource,
    private val geocoder: AddressGeocoder,
) : CurrentAddressResolver {

    override suspend fun resolve(): CurrentAddress? {
        if (!location.hasPermission()) return null
        val here = location.current() ?: return null
        val address = geocoder.lookup(here) ?: return null
        if (address.street.isBlank()) return null
        return CurrentAddress(
            street = address.street,
            number = address.number,
            colony = address.colony.ifBlank { address.city },
            municipality = address.municipality,
            state = address.state,
            postalCode = address.postalCode,
            position = here,
        ).takeIf { it.colony.isNotBlank() && it.municipality.isNotBlank() && it.state.isNotBlank() && it.postalCode.isNotBlank() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RequestFormDeviceModule {
    @Provides
    fun provideCurrentAddressResolver(location: LocationSource, geocoder: AddressGeocoder): CurrentAddressResolver =
        GeocoderAddressResolver(location, geocoder)

    @Provides
    fun provideRequestPhotoReader(reader: PhotoReader): RequestPhotoReader = RequestPhotoReader(reader::readAsJpeg)
}
