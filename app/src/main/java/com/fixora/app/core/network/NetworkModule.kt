package com.fixora.app.core.network

import com.fixora.app.BuildConfig
import com.fixora.app.core.session.AuthInterceptor
import com.fixora.app.core.session.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

// prod.properties aún no tiene la URL de la API desplegada. Retrofit no acepta una URL vacía,
// así que mientras tanto las llamadas van a un dominio que nunca existe y fallan sin conexión.
private const val UNCONFIGURED_API_URL = "https://api-no-configurada.invalid/"

/**
 * Cliente sin sesión: login, renovar tokens y cerrar sesión. El cliente normal (sin calificador)
 * agrega el token de acceso y lo renueva solo cuando vence.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicApi

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    @PublicApi
    fun providePublicOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            // Solo en debug: método, URL, código y tiempo de cada petición en Logcat (sin cuerpos,
            // para no imprimir contraseñas ni tokens).
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @PublicApi publicClient: OkHttpClient,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = publicClient.newBuilder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    @PublicApi
    fun providePublicRetrofit(@PublicApi client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_URL.ifBlank { UNCONFIGURED_API_URL })
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
