package com.fixora.app.core.session

import com.fixora.app.core.network.PublicApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    abstract fun bindSessionTokens(store: SessionStore): SessionTokens

    companion object {
        // Usa el cliente público: renovar no lleva token de acceso y no debe volver a pasar por
        // TokenAuthenticator.
        @Provides
        @Singleton
        fun provideSessionApi(@PublicApi retrofit: Retrofit): SessionApi = retrofit.create()
    }
}
