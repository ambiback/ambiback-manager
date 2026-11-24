package com.ambiback.manager.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import com.ambiback.manager.R
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val ACTION_USB_PERMISSION = "com.ambiback.manager.USB_PERMISSION"

sealed class UsbConnectionState {
    object Disconnected : UsbConnectionState()
    /** Device briefly detached (e.g. USB-mode switch); will likely reattach within seconds. */
    object Reconnecting : UsbConnectionState()
    object RequestingPermission : UsbConnectionState()
    data class Connected(val deviceName: String, val port: UsbSerialPort) : UsbConnectionState()
}

@Singleton
class UsbSerialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectionState = MutableStateFlow<UsbConnectionState>(UsbConnectionState.Disconnected)
    val connectionState: StateFlow<UsbConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var permissionTimeoutJob: Job? = null
    private var reconnectTimeoutJob: Job? = null

    private var openPort: UsbSerialPort? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = getDevice(intent)
                    device?.let { tryConnect(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    closeConnectionTransient()
                }
                ACTION_USB_PERMISSION -> {
                    permissionTimeoutJob?.cancel()
                    permissionTimeoutJob = null
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        getDevice(intent)?.let { openConnection(it) }
                    } else {
                        _connectionState.value = UsbConnectionState.Disconnected
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        ContextCompat.registerReceiver(
            context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        checkAlreadyConnected()
    }

    private fun checkAlreadyConnected() {
        UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .firstOrNull()
            ?.let { tryConnect(it.device) }
    }

    private fun tryConnect(device: UsbDevice) {
        UsbSerialProber.getDefaultProber().probeDevice(device) ?: return

        if (usbManager.hasPermission(device)) {
            openConnection(device)
        } else {
            _connectionState.value = UsbConnectionState.RequestingPermission
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
            )
            usbManager.requestPermission(device, permIntent)
            permissionTimeoutJob?.cancel()
            permissionTimeoutJob = scope.launch {
                delay(15_000)
                if (_connectionState.value is UsbConnectionState.RequestingPermission) {
                    _connectionState.value = UsbConnectionState.Disconnected
                }
            }
        }
    }

    private fun openConnection(device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: run {
            _connectionState.value = UsbConnectionState.Disconnected
            return
        }
        if (driver.ports.isEmpty()) {
            _connectionState.value = UsbConnectionState.Disconnected
            return
        }
        val connection = usbManager.openDevice(device) ?: run {
            _connectionState.value = UsbConnectionState.Disconnected
            return
        }
        val port = driver.ports[0]
        try {
            port.open(connection)
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            openPort = port
            reconnectTimeoutJob?.cancel()
            reconnectTimeoutJob = null
            val name = device.productName
                ?: "${device.vendorId.toString(16).uppercase()}:${device.productId.toString(16).uppercase()}"
            _connectionState.value = UsbConnectionState.Connected(name, port)
        } catch (e: Exception) {
            try { port.close() } catch (_: Exception) {}
            connection.close()
            _connectionState.value = UsbConnectionState.Disconnected
        }
    }

    fun closeConnection() {
        reconnectTimeoutJob?.cancel()
        reconnectTimeoutJob = null
        try { openPort?.close() } catch (_: Exception) {}
        openPort = null
        _connectionState.value = UsbConnectionState.Disconnected
    }

    private fun closeConnectionTransient() {
        try { openPort?.close() } catch (_: Exception) {}
        openPort = null
        _connectionState.value = UsbConnectionState.Reconnecting
        reconnectTimeoutJob?.cancel()
        reconnectTimeoutJob = scope.launch {
            delay(5_000)
            if (_connectionState.value is UsbConnectionState.Reconnecting) {
                _connectionState.value = UsbConnectionState.Disconnected
            }
        }
    }

    /**
     * Write [cmd] and read back the response within [timeoutMs].
     * Stops reading early when a '}' is found (covers JSON and plain ACK responses).
     * Always runs on Dispatchers.IO.
     */
    suspend fun sendCommand(cmd: ByteArray, timeoutMs: Int = 2000): String = withContext(Dispatchers.IO) {
        val port = openPort ?: throw IllegalStateException(context.getString(R.string.usb_not_connected))
        port.write(cmd, timeoutMs)
        val buffer = ByteArray(4096)
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(50).toInt()
            val n = try { port.read(buffer, remaining) } catch (_: Exception) { break }
            if (n > 0) {
                sb.append(String(buffer, 0, n, Charsets.UTF_8))
                if (sb.contains('}')) break
            }
        }
        sb.toString()
    }

    @Suppress("DEPRECATION")
    private fun getDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
}
