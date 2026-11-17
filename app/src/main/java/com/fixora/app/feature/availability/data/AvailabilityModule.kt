package com.fixora.app.feature.availability.data

import android.content.Context
import com.fixora.app.core.session.LogoutListener
import com.fixora.app.feature.availability.location.FusedLocationSource
import com.fixora.app.feature.availability.location.LocationSource
import com.fixora.app.feature.availability.service.AndroidOnlineServiceController
import com.fixora.app.feature.availability.service.OnlineServiceController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AvailabilityModule {

    @Binds
    abstract fun bindLocationSource(impl: FusedLocationSource): LocationSource

    companion object {
        /** Tiempo máximo que el cierre de sesión espera a la API para ponerse fuera de línea. */
        private const val OFFLINE_TIMEOUT_MS = 3_000L

        // Retrofit sin calificador: agrega el token de acceso y lo renueva cuando vence.
        @Provides
        @Singleton
        fun provideAvailabilityApi(retrofit: Retrofit): AvailabilityApi = retrofit.create()

        @Provides
        @Singleton
        fun provideAvailabilityRepository(api: AvailabilityApi, json: Json): AvailabilityRepository =
            AvailabilityRepository(api, json)

        @Provides
        @Singleton
        fun provideOnlineServiceController(@ApplicationContext context: Context): OnlineServiceController =
            AndroidOnlineServiceController(context)

        /** Al cerrar sesión deja de reportar la posición y se pone fuera de línea. */
        @Provides
        @IntoSet
        fun provideGoOfflineOnLogout(
            repository: AvailabilityRepository,
            controller: OnlineServiceController,
        ): LogoutListener = LogoutListener {
            controller.stop()
            if (repository.isOnline) withTimeoutOrNull(OFFLINE_TIMEOUT_MS) { repository.goOffline() }
            repository.clear()
        }
    }
}
