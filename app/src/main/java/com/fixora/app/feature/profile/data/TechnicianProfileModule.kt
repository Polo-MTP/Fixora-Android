package com.fixora.app.feature.profile.data

import com.fixora.app.core.session.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TechnicianProfileModule {

    // Retrofit sin calificador: agrega el token de acceso y lo renueva cuando vence.
    @Provides
    @Singleton
    fun provideTechnicianProfileApi(retrofit: Retrofit): TechnicianProfileApi = retrofit.create()

    @Provides
    @Singleton
    fun provideTechnicianProfileRepository(
        api: TechnicianProfileApi,
        json: Json,
        sessionStore: SessionStore,
    ): TechnicianProfileRepository = TechnicianProfileRepository(api, json, sessionStore.session.map { it?.userId })
}
