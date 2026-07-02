package io.openlist.client.core.network

import io.openlist.client.core.common.SafeLogger
import io.openlist.client.core.database.AppPreferences
import io.openlist.client.core.network.interceptor.AuthInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and caches one [OpenListApi] (Retrofit) per instance base URL, so
 * switching instances reuses connections and never crosses configuration
 * (v0.1_PRD §8.3). Token attachment is handled by [AuthInterceptor] using the
 * active [InstanceContext]. Self-signed-cert / proxy support is a reserved
 * extension point for later versions.
 *
 * Debug-logging (Settings' "调试日志" switch) is read from an [AtomicBoolean]
 * kept in sync by a background collector rather than a blocking DataStore
 * read, so toggling it takes effect immediately for every cached client
 * without ever risking a main-thread block. [SafeLogger] redacts regardless
 * of this flag, so it only controls how much is logged, never whether
 * secrets could leak.
 */
@Singleton
class OpenListClientFactory @Inject constructor(
    private val authInterceptor: AuthInterceptor,
    private val json: Json,
    appPreferences: AppPreferences,
) {
    private val apiCache = ConcurrentHashMap<String, OpenListApi>()
    private val loggingEnabled = AtomicBoolean(false)
    private val loggingInterceptor = HttpLoggingInterceptor { message -> SafeLogger.d("OkHttp", message) }

    init {
        appPreferences.loggingEnabled
            .onEach { enabled -> loggingEnabled.set(enabled) }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    fun apiFor(baseUrl: String): OpenListApi {
        val normalized = ensureTrailingSlash(baseUrl)
        return apiCache.getOrPut(normalized) { buildApi(normalized) }
    }

    private fun buildApi(baseUrl: String): OpenListApi {
        val dynamicLogging = Interceptor { chain ->
            loggingInterceptor.level =
                if (loggingEnabled.get()) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            loggingInterceptor.intercept(chain)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(dynamicLogging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(KotlinxJsonConverterFactory(json, contentType))
            .build()
            .create()
    }

    private fun ensureTrailingSlash(baseUrl: String): String =
        if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}
