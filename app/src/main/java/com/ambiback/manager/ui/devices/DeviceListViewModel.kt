package com.ambiback.manager.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambiback.manager.data.local.AppSettingsDataStore
import com.ambiback.manager.data.model.Device
import com.ambiback.manager.data.repository.DeviceRepository
import com.ambiback.manager.utils.IpValidation
import dagger.hilt.android.lifecycle.HiltViewModel
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
    /** deviceIds that were restored from storage but not yet confirmed by a live scan. */
    val unverifiedIds: Set<String> = emptySet()
)

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val settingsDataStore: AppSettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceListUiState())
    val uiState: StateFlow<DeviceListUiState> = _uiState.asStateFlow()

    // In-memory map keyed by deviceId for deduplication
    private val deviceMap = mutableMapOf<String, Device>()

    init {
        // Restore first, then optionally scan — avoids the race where a blank stub
        // overwrites a freshly discovered live device.
        viewModelScope.launch {
            restorePersistedDevices()
            val scanOnStartup = settingsDataStore.scanOnStartup.firstOrNull() ?: false
            if (scanOnStartup) scan()
        }
    }

    private suspend fun restorePersistedDevices() {
        val devices = repository.persistedDevices.firstOrNull() ?: return
        val unverified = mutableSetOf<String>()
        devices.forEach { persisted ->
            unverified.add(persisted.deviceId)
            addDevice(
                Device(
                    deviceId = persisted.deviceId,
                    deviceMac = "",
                    deviceName = persisted.deviceName,
                    deviceIp = persisted.deviceIp,
                    deviceType = "",
                    deviceVersion = "",
                    ledStatus = 0
                )
            )
        }
        _uiState.update { it.copy(unverifiedIds = unverified) }
    }

    fun scan() {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true, scanComplete = false, error = null) }

        viewModelScope.launch {
            try {
                repository.discoverDevices().collect { device ->
                    addDevice(device)
                    repository.saveDevice(device)
                    _uiState.update { it.copy(unverifiedIds = it.unverifiedIds - device.deviceId) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isScanning = false, scanComplete = true) }
            }
        }
    }

    /**
     * Merge [device] into the map. When the incoming entry has blank fields
     * (e.g. a restored stub), keep any non-blank values already present.
     */
    private fun addDevice(device: Device) {
        val existing = deviceMap[device.deviceId]
        val merged = if (existing != null) {
            device.copy(
                deviceMac = device.deviceMac.ifBlank { existing.deviceMac },
                deviceName = device.deviceName.ifBlank { existing.deviceName },
                deviceType = device.deviceType.ifBlank { existing.deviceType },
                deviceVersion = device.deviceVersion.ifBlank { existing.deviceVersion },
                deviceIp = device.deviceIp.ifBlank { existing.deviceIp },
                ledStatus = if (device.ledStatus != 0) device.ledStatus else existing.ledStatus
            )
        } else {
            device
        }
        deviceMap[merged.deviceId] = merged
        _uiState.update { it.copy(devices = deviceMap.values.toList()) }
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

        _uiState.update { it.copy(isAddingByIp = true, error = null) }
        viewModelScope.launch {
            try {
                repository.verifyDevice(normalizedIp)
                    .onSuccess { device ->
                        addDevice(device)
                        repository.saveDevice(device)
                        _uiState.update { it.copy(unverifiedIds = it.unverifiedIds - device.deviceId) }
                        onSuccess()
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(error = "No AmbiBack controller found at $normalizedIp: ${e.message}")
                        }
                    }
            } finally {
                _uiState.update { it.copy(isAddingByIp = false) }
            }
        }
    }

    /** Persist forget first so snackbar Undo cannot race an in-flight remove. */
    suspend fun forgetDevice(deviceId: String) {
        val ip = deviceMap[deviceId]?.deviceIp
        repository.forgetDevice(deviceId, ip)
        deviceMap.remove(deviceId)
        _uiState.update {
            it.copy(
                devices = deviceMap.values.toList(),
                unverifiedIds = it.unverifiedIds - deviceId
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
            _uiState.update { it.copy(devices = emptyList(), unverifiedIds = emptySet()) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
