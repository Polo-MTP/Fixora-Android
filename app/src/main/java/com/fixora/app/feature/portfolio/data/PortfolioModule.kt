package com.fixora.app.feature.portfolio.data

import com.fixora.app.feature.portfolio.ui.certificates.CertificateFileReader
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
object PortfolioModule {
    // Retrofit sin calificador: agrega el token de acceso y lo renueva cuando vence.
    @Provides
    @Singleton
    fun providePortfolioApi(retrofit: Retrofit): PortfolioApi = retrofit.create()

    @Provides
    @Singleton
    fun providePortfolioRepository(api: PortfolioApi, json: Json): PortfolioRepository = PortfolioRepository(api, json)

    @Provides
    fun provideCertificateFileReader(reader: DocumentReader): CertificateFileReader = CertificateFileReader(reader::read)
}
