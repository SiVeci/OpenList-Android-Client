package io.openlist.client.documents

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.openlist.client.core.common.DispatcherProvider
import io.openlist.client.core.domain.SystemDocumentsRepository

/**
 * The future DocumentsProvider obtains process-wide dependencies through Hilt
 * rather than constructing a database, token holder, or OkHttp client itself.
 *
 * The provider is deliberately not registered until P2.  Its repository is
 * added to this narrow entry point together with the P2 mapping implementation.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DocumentsProviderEntryPoint {
    fun systemDocumentsRepository(): SystemDocumentsRepository
    fun dispatcherProvider(): DispatcherProvider
}
