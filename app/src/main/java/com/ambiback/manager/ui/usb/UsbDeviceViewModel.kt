package com.ambiback.manager.ui.usb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambiback.manager.R
import com.ambiback.manager.data.usb.EspRomFlasher
import com.ambiback.manager.data.usb.UsbCommandProtocol
import com.ambiback.manager.data.usb.UsbConnectionState
import com.ambiback.manager.data.usb.UsbDeviceInfo
import com.ambiback.manager.data.usb.UsbSerialManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsbDeviceViewModel @Inject constructor(
    private val usbSerialManager: UsbSerialManager,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────

    data class UiState(
        val connectionState: UsbConnectionState = UsbConnectionState.Disconnected,
        val deviceInfo: UsbDeviceInfo?          = null,
        val commandStatus: CommandStatus        = CommandStatus.Idle,
        val wifiSsid: String                    = "",
        val isFlashing: Boolean                 = false,
        val flashProgress: Float                = 0f,
        val flashStatus: String                 = "",
        val selectedFirmwareUri: Uri?           = null,
        val selectedFirmwareName: String?       = null
    )

    sealed class CommandStatus {
        object Idle    : CommandStatus()
        object Loading : CommandStatus()
        data class Success(val message: String) : CommandStatus()
        data class Error  (val message: String) : CommandStatus()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Emits Unit when the USB screen should be dismissed (device disconnected). */
    private val _navigateBack = MutableSharedFlow<Unit>()
    val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    /** True when the user manually tapped the USB button; screen stays open on disconnect. */
    private val openedByUser: Boolean = savedStateHandle["openedByUser"] ?: false

    init {
        viewModelScope.launch {
            usbSerialManager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is UsbConnectionState.Disconnected && !openedByUser) {
                    // Small delay so any success snackbar stays visible during device reboot.
                    delay(1200)
                    _navigateBack.emit(Unit)
                }
            }
        }
    }

    // ── WiFi SSID ─────────────────────────────────────────────────────────────

    /**
     * Reads the phone's connected WiFi SSID.  Must be called only after
     * ACCESS_FINE_LOCATION has been granted (required on API 29+).
     */
    fun loadCurrentSsid() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        @Suppress("DEPRECATION")
        val raw = (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.connectionInfo?.ssid
        val ssid = raw?.removeSurrounding("\"")
            ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
            ?: ""
        _uiState.update { it.copy(wifiSsid = ssid) }
    }

    fun updateWifiSsid(ssid: String) {
        _uiState.update { it.copy(wifiSsid = ssid) }
    }

    // ── Serial commands ───────────────────────────────────────────────────────

    fun getInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            setStatus(CommandStatus.Loading)
            try {
                val raw  = usbSerialManager.sendCommand(UsbCommandProtocol.buildInfoCommand())
                val info = UsbCommandProtocol.parseInfoResponse(raw)
                if (info != null) {
                    _uiState.update { it.copy(deviceInfo = info) }
                    setStatus(CommandStatus.Success(context.getString(R.string.usb_info_loaded)))
                } else {
                    setStatus(CommandStatus.Error(context.getString(R.string.usb_no_response)))
                }
            } catch (e: Exception) {
                setStatus(CommandStatus.Error(e.message ?: context.getString(R.string.usb_unknown_error)))
            }
        }
    }

    fun resetWifi() = sendSimple(UsbCommandProtocol.buildResetWifiCommand(), context.getString(R.string.usb_wifi_reset_done))

    fun setWifi(ssid: String, password: String) =
        sendSimple(UsbCommandProtocol.buildSetWifiCommand(ssid, password), context.getString(R.string.usb_wifi_sent))

    fun factoryReset() = sendSimple(UsbCommandProtocol.buildFactoryResetCommand(), context.getString(R.string.usb_factory_sent))

    /** Stores the firmware zip URI/name selected by the user. */
    fun setSelectedFirmware(uri: Uri, name: String) {
        _uiState.update { it.copy(selectedFirmwareUri = uri, selectedFirmwareName = name) }
    }

    // ── Firmware flash ────────────────────────────────────────────────────────

    fun flashFirmware() {
        val uri  = _uiState.value.selectedFirmwareUri ?: return
        val port = (usbSerialManager.connectionState.value as? UsbConnectionState.Connected)?.port ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isFlashing = true, flashProgress = 0f, flashStatus = "") }
            try {
                EspRomFlasher(port).flash(context, uri) { progress, status ->
                    _uiState.update { it.copy(flashProgress = progress, flashStatus = status) }
                }
                setStatus(CommandStatus.Success(context.getString(R.string.usb_flash_done)))
            } catch (e: Exception) {
                setStatus(CommandStatus.Error(context.getString(R.string.usb_flash_failed, e.message.orEmpty())))
                _uiState.update { it.copy(flashProgress = 0f, flashStatus = "") }
            } finally {
                _uiState.update { it.copy(isFlashing = false) }
            }
        }
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    fun clearStatus() = setStatus(CommandStatus.Idle)

    private fun setStatus(s: CommandStatus) = _uiState.update { it.copy(commandStatus = s) }

    private fun sendSimple(cmd: ByteArray, successMsg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            setStatus(CommandStatus.Loading)
            try {
                // Short timeout — some commands cause the device to reboot immediately.
                usbSerialManager.sendCommand(cmd, timeoutMs = 500)
                setStatus(CommandStatus.Success(successMsg))
            } catch (e: Exception) {
                setStatus(CommandStatus.Error(e.message ?: context.getString(R.string.usb_unknown_error)))
            }
        }
    }

}
