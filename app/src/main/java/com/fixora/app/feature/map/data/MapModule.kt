package com.fixora.app.feature.map.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MapModule {
    // Retrofit sin calificador: agrega el token de acceso, que pide /search/branches.
    @Provides
    @Singleton
    fun provideMapApi(retrofit: Retrofit): MapApi = retrofit.create()

    @Provides
    @Singleton
    fun provideMapRepository(api: MapApi, json: Json): MapRepository = RemoteMapRepository(api, json)
}
