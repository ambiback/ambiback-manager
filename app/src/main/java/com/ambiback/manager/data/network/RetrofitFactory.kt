package com.ambiback.manager.data.network

import com.ambiback.manager.data.local.AppSettingsDataStore
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates a per-device [DeviceApiService] Retrofit instance.
 * Base URL is constructed from the device's IP address at call time,
 * since each controller has its own HTTP server.
 *
 * Timeouts are read from [AppSettingsDataStore]; clients and services
 * are cached per timeout / IP to avoid rebuilding on every call.
 */
@Singleton
class RetrofitFactory @Inject constructor(
    private val settingsDataStore: AppSettingsDataStore
) {
    private val clientsByTimeout = ConcurrentHashMap<Int, OkHttpClient>()
    private val services = ConcurrentHashMap<Pair<String, Int>, DeviceApiService>()

    private fun clientFor(timeoutSec: Int): OkHttpClient =
        clientsByTimeout.getOrPut(timeoutSec) {
            OkHttpClient.Builder()
                .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
                .writeTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
                .build()
        }

    suspend fun createService(deviceIp: String): DeviceApiService {
        val timeoutSec = settingsDataStore.httpTimeoutSec.first().coerceIn(1, 30)
        // Drop stale timeout variants for this IP so setting changes do not leak clients.
        services.keys
            .filter { it.first == deviceIp && it.second != timeoutSec }
            .forEach { services.remove(it) }
        if (services.size >= MAX_CACHED_SERVICES) {
            // Bound growth from discovery churn; prefer eviction of other IPs.
            services.keys
                .filter { it.first != deviceIp }
                .take(services.size - MAX_CACHED_SERVICES + 1)
                .forEach { services.remove(it) }
        }
        return services.getOrPut(deviceIp to timeoutSec) {
            Retrofit.Builder()
                .baseUrl("http://$deviceIp/")
                .client(clientFor(timeoutSec))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DeviceApiService::class.java)
        }
    }

    /** Drop cached HTTP clients for a forgotten device. */
    fun evict(deviceIp: String) {
        services.keys.filter { it.first == deviceIp }.forEach { services.remove(it) }
    }

    fun evictAll() {
        services.clear()
    }

    companion object {
        private const val MAX_CACHED_SERVICES = 32
    }
}
