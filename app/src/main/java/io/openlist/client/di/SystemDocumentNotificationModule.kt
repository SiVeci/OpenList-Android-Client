package io.openlist.client.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.openlist.client.core.domain.SystemDocumentFailureNotifier
import io.openlist.client.notifications.SystemDocumentFailureNotificationPublisher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemDocumentNotificationModule {
    @Binds
    @Singleton
    abstract fun bindSystemDocumentFailureNotifier(
        impl: SystemDocumentFailureNotificationPublisher,
    ): SystemDocumentFailureNotifier
}
