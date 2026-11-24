package com.ambiback.manager.ui.device

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambiback.manager.data.local.ClaimKeyStore
import com.ambiback.manager.data.model.DeviceConfig
import com.ambiback.manager.data.model.DeviceStatus
import com.ambiback.manager.data.model.LedMode
import com.ambiback.manager.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

/** Identifies which control is currently sending a command. */
enum class SendingControl {
    Power, Mode, Brightness, Color, Preset, Speed, Intensity, HealthFloor
}

data class DeviceDetailUiState(
    val configState: UiState<DeviceConfig> = UiState.Loading,
    val statusState: UiState<DeviceStatus> = UiState.Loading,
    val sendingControls: Set<SendingControl> = emptySet(),
    val actionError: String? = null,
    val claimKey: String? = null,
    /** True while a manual refresh reloads config and status without clearing the form. */
    val isRefreshing: Boolean = false,
    /** Bumped on every status reply, so optimistic switch/chip state resyncs even if nothing changed. */
    val statusVersion: Int = 0
) {
    val isSending: Boolean get() = sendingControls.isNotEmpty()
}

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val claimKeyStore: ClaimKeyStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val deviceIp: String = checkNotNull(savedStateHandle["deviceIp"])
    val deviceName: String = savedStateHandle["deviceName"] ?: "Device"

    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    private var statusPollJob: Job? = null

    init {
        loadConfig()
        loadStatus()
    }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(configState = UiState.Loading) }
            fetchConfig()
        }
    }

    fun loadStatus() {
        viewModelScope.launch { fetchStatus() }
    }

    /**
     * Reload config and status. Keeps the current form on screen; a failed refresh of a
     * loaded config is reported as a message instead of replacing the controls.
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val hadConfig = _uiState.value.configState is UiState.Success
                val configOk = fetchConfig(keepLoadedOnError = hadConfig)
                fetchStatus()
                if (!configOk && hadConfig) {
                    _uiState.update { it.copy(actionError = REFRESH_FAILED) }
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun fetchConfig(keepLoadedOnError: Boolean = false): Boolean =
        repository.getConfig(deviceIp).fold(
            onSuccess = { config ->
                val claimKey = claimKeyStore.get(config.deviceMac)
                _uiState.update { it.copy(configState = UiState.Success(config), claimKey = claimKey) }
                true
            },
            onFailure = { e ->
                if (!keepLoadedOnError) {
                    _uiState.update { it.copy(configState = UiState.Error(e.message ?: "Failed to load config")) }
                }
                false
            }
        )

    private suspend fun fetchStatus() {
        repository.getStatus(deviceIp).fold(
            onSuccess = { status -> applyStatus(status) },
            onFailure = { e ->
                _uiState.update { it.copy(statusState = UiState.Error(e.message ?: "Failed to load status")) }
            }
        )
    }

    private fun applyStatus(status: DeviceStatus) {
        _uiState.update { it.copy(statusState = UiState.Success(status), statusVersion = it.statusVersion + 1) }
    }

    /** Poll device status every [intervalMs] while the Quick Control tab is visible. */
    fun startStatusPolling(intervalMs: Long = 5_000L) {
        if (statusPollJob?.isActive == true) return
        statusPollJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                // Skip a poll while a command is in flight; it would undo the optimistic UI.
                if (_uiState.value.isSending) continue
                repository.getStatus(deviceIp).onSuccess { status -> applyStatus(status) }
            }
        }
    }

    fun stopStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = null
    }

    fun setMode(mode: Int) = sendCommand(SendingControl.Mode, refreshStatus = true) {
        repository.setColorMode(deviceIp, mode)
    }

    fun turnOff() = sendCommand(SendingControl.Power, refreshStatus = true) {
        repository.setColorMode(deviceIp, LedMode.OFF)
    }

    fun turnOn(mode: Int = LedMode.AMBIENT) = sendCommand(SendingControl.Power, refreshStatus = true) {
        repository.setColorMode(deviceIp, mode)
    }

    /** @param brightness 0-255 */
    fun setBrightness(brightness: Int) = sendCommand(SendingControl.Brightness) {
        repository.setParam(deviceIp, "setbrightness", brightness)
    }

    /**
     * Show a solid color: switches to Ambient with the Solid scene first, because a running
     * scene would otherwise paint over the color. Sent as WWRRGGBB hex (W=0, RGB strips).
     */
    fun setColor(color: Color) = sendCommand(SendingControl.Color, refreshStatus = true) {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        val hex = "00%02X%02X%02X".format(r, g, b)
        repository.setColorMode(deviceIp, LedMode.AMBIENT).onFailure { return@sendCommand Result.failure(it) }
        repository.setParam(deviceIp, "setambientpreset", SOLID_PRESET).onFailure { return@sendCommand Result.failure(it) }
        repository.setColor(deviceIp, hex)
    }

    fun setPreset(preset: Int) = sendCommand(SendingControl.Preset, refreshStatus = true) {
        val modeResult = repository.setColorMode(deviceIp, LedMode.AMBIENT)
        if (modeResult.isFailure) return@sendCommand modeResult
        repository.setParam(deviceIp, "setambientpreset", preset)
    }

    /** @param speed 1-10 */
    fun setAmbientSpeed(speed: Int) = sendCommand(SendingControl.Speed) {
        repository.setParam(deviceIp, "setambientspeed", speed)
    }

    /** @param intensity 1-100 */
    fun setAmbientIntensity(intensity: Int) = sendCommand(SendingControl.Intensity) {
        repository.setParam(deviceIp, "setambientintensity", intensity)
    }

    /** @param floor 1-100 — minimum brightness floor for health presets 12-15 */
    fun setHealthPulseFloor(floor: Int) = sendCommand(SendingControl.HealthFloor) {
        repository.setParam(deviceIp, "setambienthealthintensity", floor)
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    /**
     * @param refreshStatus reload the status shortly after success, so the power switch and
     * mode chips show what the controller applied (it switches modes asynchronously).
     */
    private fun sendCommand(
        control: SendingControl,
        refreshStatus: Boolean = false,
        block: suspend () -> Result<Unit>
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(sendingControls = it.sendingControls + control, actionError = null)
            }
            val result = try {
                block()
            } finally {
                _uiState.update { it.copy(sendingControls = it.sendingControls - control) }
            }
            result.onFailure { e ->
                _uiState.update { it.copy(actionError = e.message) }
                fetchStatus()
            }
            if (result.isSuccess && refreshStatus) {
                delay(STATUS_SETTLE_MS)
                fetchStatus()
            }
        }
    }

    private companion object {
        const val SOLID_PRESET = 0
        const val STATUS_SETTLE_MS = 800L
        const val REFRESH_FAILED = "Could not refresh. Showing the last loaded values."
    }

    override fun onCleared() {
        stopStatusPolling()
        super.onCleared()
    }
}
