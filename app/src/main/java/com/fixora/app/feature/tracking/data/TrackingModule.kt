package com.fixora.app.feature.tracking.data

import android.content.Context
import com.fixora.app.core.network.PublicApi
import com.fixora.app.core.session.LogoutListener
import com.fixora.app.core.session.SessionTokens
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.servicerequest.data.ServiceRequestRepository
import com.fixora.app.feature.tracking.service.AndroidTripTrackingController
import com.fixora.app.feature.tracking.service.TripTrackingController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TrackingModule {

    // Cliente sin sesión: el socket manda el token en su primer mensaje, no en un encabezado.
    @Provides
    @Singleton
    fun provideTripSocketFactory(@PublicApi client: OkHttpClient): TripSocketFactory = OkHttpTripSocketFactory(client)

    @Provides
    fun provideTripReporter(
        sockets: TripSocketFactory,
        tokens: SessionTokens,
        requests: ServiceRequestRepository,
        location: LocationSource,
        json: Json,
    ): TripReporter = TripReporter(sockets, tokens, requests, location, json)

    @Provides
    @Singleton
    fun provideTripTrackingController(@ApplicationContext context: Context): TripTrackingController =
        AndroidTripTrackingController(context)

    /** Al cerrar sesión deja de mandar la ubicación del traslado. */
    @Provides
    @IntoSet
    fun provideStopTripOnLogout(controller: TripTrackingController): LogoutListener =
        LogoutListener { controller.stop() }
}
