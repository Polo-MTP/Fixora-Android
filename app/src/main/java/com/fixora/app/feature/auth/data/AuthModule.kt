package com.fixora.app.feature.auth.data

import com.fixora.app.core.network.PublicApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideAuthApi(@PublicApi retrofit: Retrofit): AuthApi = retrofit.create()
}
