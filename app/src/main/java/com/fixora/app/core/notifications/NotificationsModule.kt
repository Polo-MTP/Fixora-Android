package com.fixora.app.core.notifications

import com.fixora.app.core.session.SessionStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {

    @Binds
    abstract fun bindPushTokenProvider(impl: FirebasePushTokenProvider): PushTokenProvider

    companion object {
        // Retrofit sin calificador: agrega el token de acceso y lo renueva cuando vence.
        @Provides
        @Singleton
        fun provideDeviceTokenApi(retrofit: Retrofit): DeviceTokenApi = retrofit.create()

        // Vive lo mismo que la app: el registro sigue aunque se cierre la pantalla que lo pidió.
        @Provides
        @Singleton
        fun provideDeviceTokenRegistrar(
            api: DeviceTokenApi,
            tokens: PushTokenProvider,
            sessionStore: SessionStore,
            json: Json,
        ): DeviceTokenRegistrar = DeviceTokenRegistrar(
            api = api,
            tokens = tokens,
            userId = sessionStore.session.map { it?.userId },
            json = json,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
}
