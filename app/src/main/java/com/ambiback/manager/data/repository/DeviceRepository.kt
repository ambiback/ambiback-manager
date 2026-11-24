package com.ambiback.manager.data.repository

import android.content.Context
import com.ambiback.manager.R
import com.ambiback.manager.data.local.DeviceDataStore
import com.ambiback.manager.data.local.PersistedDevice
import com.ambiback.manager.data.model.Device
import com.ambiback.manager.data.model.DeviceConfig
import com.ambiback.manager.data.model.DeviceStatus
import com.ambiback.manager.data.network.RetrofitFactory
import com.ambiback.manager.data.network.UdpDiscoveryService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for device data.
 * Orchestrates UDP discovery, per-device HTTP calls, and DataStore persistence.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val discoveryService: UdpDiscoveryService,
    private val dataStore: DeviceDataStore,
    private val retrofitFactory: RetrofitFactory,
    @ApplicationContext private val context: Context
) {

    /** Returns a Flow that emits each device found during a UDP broadcast scan. */
    fun discoverDevices(): Flow<Device> = discoveryService.discover()

    /** Persist a newly discovered (or updated) device. */
    suspend fun saveDevice(device: Device) =
        dataStore.saveDevice(
            PersistedDevice(device.deviceId, device.deviceIp, device.deviceName, device.deviceMac)
        )

    /** Remove a device from persistent storage and drop its cached HTTP client when [deviceIp] is known. */
    suspend fun forgetDevice(deviceKey: String, deviceIp: String? = null) {
        dataStore.removeDevice(deviceKey)
        if (!deviceIp.isNullOrBlank()) retrofitFactory.evict(deviceIp)
    }

    /** Clear all persisted devices in one atomic write. */
    suspend fun forgetAllDevices() {
        dataStore.clearAllDevices()
        retrofitFactory.evictAll()
    }

    /** Emits the list of all persisted devices (wire format stays inside DataStore). */
    val persistedDevices: Flow<List<PersistedDevice>> = dataStore.knownDevices

    // ---- Per-device HTTP calls ----

    suspend fun getConfig(deviceIp: String): Result<DeviceConfig> = runCatchingFriendly {
        retrofitFactory.createService(deviceIp).getConfig()
    }

    suspend fun getStatus(deviceIp: String): Result<DeviceStatus> = runCatchingFriendly {
        retrofitFactory.createService(deviceIp).getStatus()
    }

    /**
     * Verify that [deviceIp] is an AmbiBack controller and map its config into a saved device entry.
     */
    suspend fun verifyDevice(deviceIp: String): Result<Device> = runCatchingFriendly {
        val config = retrofitFactory.createService(deviceIp).getConfig()
        require(config.deviceId.isNotBlank()) { context.getString(R.string.net_err_no_id, deviceIp) }

        Device(
            deviceId = config.deviceId,
            deviceMac = config.deviceMac,
            deviceName = config.deviceName,
            deviceIp = config.deviceIp.ifBlank { deviceIp },
            deviceType = config.deviceType,
            deviceVersion = config.deviceVersion,
            ledStatus = config.ledMode
        )
    }

    suspend fun setColorMode(deviceIp: String, mode: Int): Result<Unit> = runCatchingFriendly {
        retrofitFactory.createService(deviceIp).setColorMode(mode)
    }

    /** @param color 8-character WWRRGGBB hex string */
    suspend fun setColor(deviceIp: String, color: String): Result<Unit> = runCatchingFriendly {
        retrofitFactory.createService(deviceIp).setColor(color)
    }

    suspend fun setParam(deviceIp: String, param: String, value: Int): Result<Unit> = runCatchingFriendly {
        retrofitFactory.createService(deviceIp).setParam(param, value)
    }

    private inline fun <T> runCatchingFriendly(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e), e))
        }

    private fun friendlyMessage(e: Throwable): String = when (e) {
        is SocketTimeoutException -> context.getString(R.string.net_err_timeout)
        is ConnectException -> context.getString(R.string.net_err_connect)
        is UnknownHostException -> context.getString(R.string.net_err_host)
        else -> e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.net_err_unknown)
    }
}
