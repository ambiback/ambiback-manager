package com.ambiback.manager.ui.devices

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambiback.manager.data.local.AppSettingsDataStore
import com.ambiback.manager.data.model.Device
import com.ambiback.manager.data.repository.DeviceRepository
import com.ambiback.manager.utils.DeviceKeys
import com.ambiback.manager.utils.IpValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceListUiState(
    val devices: List<Device> = emptyList(),
    val isScanning: Boolean = false,
    val isAddingByIp: Boolean = false,
    val scanComplete: Boolean = false,
    val error: String? = null,
    /** Why the last "Add by IP" attempt failed; shown inside the dialog, not behind it. */
    val addByIpError: String? = null,
    /** Device keys that were restored from storage but not yet confirmed by a live scan. */
    val unverifiedKeys: Set<String> = emptySet()
)

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val settingsDataStore: AppSettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceListUiState())
    val uiState: StateFlow<DeviceListUiState> = _uiState.asStateFlow()

    // In-memory map keyed by Device.deviceKey for deduplication
    private val deviceMap = mutableMapOf<String, Device>()

    init {
        // Restore first, then optionally scan — avoids the race where a blank stub
        // overwrites a freshly discovered live device.
        viewModelScope.launch {
            restorePersistedDevices()
            val scanOnStartup = settingsDataStore.scanOnStartup.firstOrNull() ?: true
            if (scanOnStartup) startScan(reportErrors = false)
        }
    }

    private suspend fun restorePersistedDevices() {
        val devices = repository.persistedDevices.firstOrNull() ?: return
        val unverified = mutableSetOf<String>()
        devices.forEach { persisted ->
            unverified.add(persisted.deviceKey)
            addDevice(
                Device(
                    deviceId = persisted.deviceId,
                    deviceMac = persisted.deviceMac,
                    deviceName = persisted.deviceName,
                    deviceIp = persisted.deviceIp,
                    deviceType = "",
                    deviceVersion = "",
                    ledStatus = 0
                )
            )
        }
        _uiState.update { it.copy(unverifiedKeys = unverified) }
    }

    fun scan() = startScan(reportErrors = true)

    /** The automatic startup scan only logs failures, e.g. when the phone has no Wi-Fi. */
    private fun startScan(reportErrors: Boolean) {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true, scanComplete = false, error = null) }

        viewModelScope.launch {
            try {
                repository.discoverDevices().collect { device ->
                    addDevice(device)
                    repository.saveDevice(device)
                    _uiState.update { it.copy(unverifiedKeys = it.unverifiedKeys - device.deviceKey) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (reportErrors) {
                    _uiState.update { it.copy(error = e.message) }
                } else {
                    Log.w("DeviceListViewModel", "Startup scan failed", e)
                }
            } finally {
                _uiState.update { it.copy(isScanning = false, scanComplete = true) }
            }
        }
    }

    /**
     * Merge [device] into the map. When the incoming entry has blank fields
     * (e.g. a restored stub), keep any non-blank values already present.
     * An entry saved without a MAC is replaced once its DeviceID is seen with one.
     */
    private fun addDevice(device: Device) {
        val alreadyUpgraded = device.deviceMac.isBlank() && deviceMap.values.any {
            it.deviceMac.isNotBlank() && it.deviceId == device.deviceId && it.deviceIp == device.deviceIp
        }
        if (alreadyUpgraded) return

        val key = device.deviceKey
        val stubKey = DeviceKeys.idKey(device.deviceId)
        val stub = if (key != stubKey) deviceMap.remove(stubKey) else null
        val existing = deviceMap[key] ?: stub
        // A restored stub has no type/version; 0 there means "unknown", not "Off".
        val isStub = device.deviceType.isBlank() && device.deviceVersion.isBlank()
        val merged = if (existing != null) {
            device.copy(
                deviceMac = device.deviceMac.ifBlank { existing.deviceMac },
                deviceName = device.deviceName.ifBlank { existing.deviceName },
                deviceType = device.deviceType.ifBlank { existing.deviceType },
                deviceVersion = device.deviceVersion.ifBlank { existing.deviceVersion },
                deviceIp = device.deviceIp.ifBlank { existing.deviceIp },
                ledStatus = if (isStub) existing.ledStatus else device.ledStatus
            )
        } else {
            device
        }
        deviceMap[key] = merged
        _uiState.update {
            it.copy(
                devices = deviceMap.values.toList(),
                unverifiedKeys = if (stub != null) it.unverifiedKeys - stubKey + key else it.unverifiedKeys
            )
        }
    }

    fun addDeviceByIp(deviceIp: String, onSuccess: () -> Unit = {}) {
        val normalizedIp = deviceIp.trim()
        if (normalizedIp.isBlank()) {
            _uiState.update { it.copy(error = "Device IP is required.") }
            return
        }
        if (!IpValidation.isValidIp(normalizedIp)) {
            _uiState.update { it.copy(error = "Invalid IP address: $normalizedIp") }
            return
        }
        if (_uiState.value.isAddingByIp) return

        _uiState.update { it.copy(isAddingByIp = true, error = null, addByIpError = null) }
        viewModelScope.launch {
            try {
                repository.verifyDevice(normalizedIp)
                    .onSuccess { device ->
                        addDevice(device)
                        repository.saveDevice(device)
                        _uiState.update { it.copy(unverifiedKeys = it.unverifiedKeys - device.deviceKey) }
                        onSuccess()
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(addByIpError = e.message.orEmpty()) }
                    }
            } finally {
                _uiState.update { it.copy(isAddingByIp = false) }
            }
        }
    }

    /** Persist forget first so snackbar Undo cannot race an in-flight remove. */
    suspend fun forgetDevice(deviceKey: String) {
        val ip = deviceMap[deviceKey]?.deviceIp
        repository.forgetDevice(deviceKey, ip)
        deviceMap.remove(deviceKey)
        _uiState.update {
            it.copy(
                devices = deviceMap.values.toList(),
                unverifiedKeys = it.unverifiedKeys - deviceKey
            )
        }
    }

    /** Restore a previously forgotten device (used by snackbar undo). */
    suspend fun restoreDevice(device: Device) {
        addDevice(device)
        repository.saveDevice(device)
    }

    fun forgetAllDevices() {
        viewModelScope.launch {
            repository.forgetAllDevices()
            deviceMap.clear()
            _uiState.update { it.copy(devices = emptyList(), unverifiedKeys = emptySet()) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearAddByIpError() {
        _uiState.update { it.copy(addByIpError = null) }
    }
}
