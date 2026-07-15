package io.openlist.client.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.openlist.client.core.network.SystemDocumentHttpClient
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // OpenList responses carry many fields we don't model
        coerceInputValues = true // fall back to defaults for null primitives
        explicitNulls = false
    }

    /**
     * DocumentsProvider and the app process must use this single raw HTTP
     * client.  It deliberately does not use the normal auth interceptor,
     * because signed download URLs may point to an untrusted origin.
     */
    @Provides
    @Singleton
    fun provideSystemDocumentHttpClient(): SystemDocumentHttpClient = SystemDocumentHttpClient()
}
