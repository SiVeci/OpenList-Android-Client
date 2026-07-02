package io.openlist.client.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openlist_prefs")

// "Current instance" lives solely in InstanceEntity.isCurrent (Room) — there is
// deliberately no second copy here, to avoid two sources of truth drifting.
/** Non-sensitive app-level settings only. Tokens never go here — see core/auth CryptoManager. */
@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
    }

    val loggingEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.LOGGING_ENABLED] ?: false }

    suspend fun setLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOGGING_ENABLED] = enabled }
    }
}
