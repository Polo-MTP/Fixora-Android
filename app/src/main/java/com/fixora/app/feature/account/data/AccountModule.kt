package com.fixora.app.feature.account.data

import com.fixora.app.core.session.SessionStore
import com.fixora.app.feature.account.ui.photo.AccountPhotoReader
import com.fixora.app.feature.profile.data.PhotoReader
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
object AccountModule {

    // Retrofit sin calificador: agrega el token de acceso y lo renueva cuando vence.
    @Provides
    @Singleton
    fun provideAccountApi(retrofit: Retrofit): AccountApi = retrofit.create()

    @Provides
    @Singleton
    fun provideAccountRepository(api: AccountApi, json: Json, sessionStore: SessionStore): AccountRepository =
        RemoteAccountRepository(api, json, sessionStore.session.map { it?.userId })

    @Provides
    fun provideAccountPhotoReader(reader: PhotoReader): AccountPhotoReader = AccountPhotoReader(reader::readAsJpeg)
}
