package com.ambiback.manager.ui.provisioning

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ambiback.manager.data.model.ProvisioningStatus
import com.ambiback.manager.data.network.GatewayResolver
import com.ambiback.manager.data.repository.ProvisioningRepository
import com.ambiback.manager.utils.PasswordGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Step / state machine
// ---------------------------------------------------------------------------

sealed class ProvisioningStep {
    /** Initial screen — shows instructions to join the controller AP. */
    object Idle : ProvisioningStep()

    /** Waiting for user to select an AP from the Android 10+ system sheet. */
    object WaitingForAutoConnect : ProvisioningStep()

    /** Polling WiFi scan results for nearby controllers. */
    data class Scanning(val message: String = "Scanning for AmbiBack devices…") : ProvisioningStep()

    /** Showing multiple found controllers to the user. */
    data class SelectDevice(val ssids: List<String>) : ProvisioningStep()

    /** Resolving gateway IP + calling /getprovisioningasjson. */
    object ResolvingGateway : ProvisioningStep()

    /** Controller reachable; shows device info and "Start Provisioning" button. */
    data class Connected(val status: ProvisioningStatus, val gatewayIp: String) : ProvisioningStep()

    /** Calling /provisioning/start */
    object StartingSession : ProvisioningStep()

    /** Calling /startwps?forreal=1 */
    object StartingWps : ProvisioningStep()

    /** WPS was triggered; user needs to complete router-side pairing. */
    data class WpsInstructions(val message: String, val gatewayIp: String) : ProvisioningStep()

    /** User enters target WiFi SSID + password. */
    data class EnterCredentials(
        val token: String,
        val gatewayIp: String,
        val defaultSsid: String = ""
    ) : ProvisioningStep()

    /** Calling /provisioning/submit */
    object Submitting : ProvisioningStep()

    /** Credentials staged — shows "Finish" button. */
    data class Monitoring(val token: String, val gatewayIp: String) : ProvisioningStep()

    /** Calling /provisioning/finish */
    object Finishing : ProvisioningStep()

    /** Final reboot phase with countdown. */
    data class Rebooting(val message: String, val remainingMs: Long) : ProvisioningStep()

    /** Session expired. */
    data class Expired(val message: String) : ProvisioningStep()

    /**
     * Finish failed (wrong password, etc.).
     * Firmware kept state=pending — user can fix credentials and retry
     * without restarting the session.
     */
    data class FinishFailed(
        val token: String,
        val gatewayIp: String,
        val message: String
    ) : ProvisioningStep()

    /** Provisioning complete. */
    data class Success(val message: String) : ProvisioningStep()

    /** Unrecoverable error. */
    data class Error(val message: String, val canRetry: Boolean) : ProvisioningStep()
}

data class ProvisioningUiState(
    val step: ProvisioningStep = ProvisioningStep.Idle,
    val expiresInMs: Long = 0
)

private const val SETUP_WINDOW_TIMEOUT_MS = 60_000L

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ProvisioningViewModel @Inject constructor(
    application: Application,
    private val repository: ProvisioningRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProvisioningUiState())
    val uiState: StateFlow<ProvisioningUiState> = _uiState.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var expiryJob: Job? = null
    private var rebootJob: Job? = null
    private var scanJob: Job? = null
    private var homeSsid: String = ""

    private fun step(step: ProvisioningStep, expiresIn: String = "0") {
        val serverMs = expiresIn.toLongOrNull() ?: 0L
        val effectiveMs = if (shouldTrackExpiry(step) && serverMs > 0L) {
            minOf(serverMs, SETUP_WINDOW_TIMEOUT_MS)
        } else {
            0L
        }
        _uiState.value = ProvisioningUiState(step, effectiveMs)
        manageExpiry(step, effectiveMs)
    }

    override fun onCleared() {
        super.onCleared()
        expiryJob?.cancel()
        rebootJob?.cancel()
        scanJob?.cancel()
        releaseNetworkCallback()
    }

    /**
     * Capture the phone's current WiFi SSID before SoftAP association.
     * Retries are allowed until a real home SSID is obtained (e.g. after the user
     * grants location permission). Ignores AmbiBack AP SSIDs.
     */
    fun ensureHomeSsidCaptured() {
        if (homeSsid.isNotBlank()) return
        val wifiManager = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        if (info != null && info.networkId != -1) {
            val ssid = info.ssid
            if (ssid != null && ssid != "<unknown ssid>" && ssid != "0x") {
                val cleaned = if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid.substring(1, ssid.length - 1)
                } else {
                    ssid
                }
                if (!cleaned.startsWith("AmbiBack ", ignoreCase = true)) {
                    homeSsid = cleaned
                }
            }
        }
    }

    // ---- Public actions ------------------------------------------------

    /**
     * Called when the user confirms they have joined the controller AP.
     * Resolves the gateway IP and pings /getprovisioningasjson.
     */
    fun onUserJoinedAp() {
        ensureHomeSsidCaptured()
        step(ProvisioningStep.ResolvingGateway)
        viewModelScope.launch {
            val ip = GatewayResolver.resolve(getApplication())
            repository.getProvisioningStatus(ip)
                .onSuccess { status -> step(ProvisioningStep.Connected(status, ip), status.expiresIn) }
                .onFailure { e ->
                    step(ProvisioningStep.Error(
                        message   = "Cannot reach controller at $ip — ${e.message}",
                        canRetry  = true
                    ))
                }
        }
    }

    /**
     * Scans for AmbiBack devices in the background to automatically compute the password.
     */
    fun startPreScan() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            step(ProvisioningStep.Error("Auto-connect requires Android 10 or later.", canRetry = true))
            return
        }

        ensureHomeSsidCaptured()
        step(ProvisioningStep.Scanning())
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

            try { @Suppress("DEPRECATION") wifiManager.startScan() } catch (_: Exception) {}

            var foundMacs = listOf<String>()

            for (i in 0 until 10) {
                try {
                    @Suppress("DEPRECATION")
                    val results = wifiManager.scanResults
                    val ambiNetworks = results.filter { it.SSID?.startsWith("AmbiBack ") == true }

                    if (ambiNetworks.isNotEmpty()) {
                        foundMacs = ambiNetworks.map { it.SSID.removePrefix("AmbiBack ") }.distinct()
                        break
                    }
                } catch (e: SecurityException) {
                    step(ProvisioningStep.Error("Location permission is required to scan for setup devices.", canRetry = true))
                    return@launch
                }
                delay(1000)
            }

            when {
                foundMacs.isEmpty() -> {
                    step(ProvisioningStep.Error("No AmbiBack devices found during scan.", canRetry = true))
                }
                foundMacs.size == 1 -> {
                    val mac = foundMacs.first()
                    connectToDeviceMac(mac)
                }
                else -> {
                    step(ProvisioningStep.SelectDevice(foundMacs.map { "AmbiBack $it" }))
                }
            }
        }
    }

    /** Called from SelectDevice screen when user manually picks one of the matching devices. */
    fun onDeviceSelected(ssid: String) {
        val mac = ssid.removePrefix("AmbiBack ")
        connectToDeviceMac(mac)
    }

    private fun connectToDeviceMac(mac: String) {
        val password = PasswordGenerator.generateApPassword(mac)
        val exactSsid = "AmbiBack $mac"
        requestAutoConnect(exactSsid, password)
    }

    /**
     * Android 10+ only: Connects specifically to the target SSID + Password without prompting user.
     */
    private fun requestAutoConnect(ssid: String, password: String) {
        step(ProvisioningStep.WaitingForAutoConnect)

        val context = getApplication<Application>()
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        releaseNetworkCallback()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectivityManager.bindProcessToNetwork(network)

                step(ProvisioningStep.ResolvingGateway)

                viewModelScope.launch {
                    val ip = GatewayResolver.resolve(context)
                    repository.getProvisioningStatus(ip)
                        .onSuccess { status -> step(ProvisioningStep.Connected(status, ip), status.expiresIn) }
                        .onFailure { e ->
                            step(ProvisioningStep.Error(
                                message   = "Cannot reach controller at $ip — ${e.message}",
                                canRetry  = true
                            ))
                        }
                }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                step(ProvisioningStep.Error("Auto-connect cancelled or timed out.", canRetry = true))
            }
        }

        // Timeout overload ensures onUnavailable() fires if the system sheet is dismissed.
        connectivityManager.requestNetwork(
            request,
            networkCallback!!,
            SETUP_WINDOW_TIMEOUT_MS.toInt()
        )
    }

    /** Cancel an in-progress scan or auto-connect and return to Idle. */
    fun cancelAutoConnect() {
        scanJob?.cancel()
        scanJob = null
        releaseNetworkCallback()
        step(ProvisioningStep.Idle)
    }

    /**
     * Called when the user taps "Start Provisioning" on the Connected screen.
     */
    fun startSession() {
        val connected = _uiState.value.step as? ProvisioningStep.Connected ?: return
        step(ProvisioningStep.StartingSession)
        viewModelScope.launch {
            repository.startSession(connected.gatewayIp)
                .onSuccess { status ->
                    if (status.token.isNotEmpty()) {
                        step(ProvisioningStep.EnterCredentials(
                            token = status.token,
                            gatewayIp = connected.gatewayIp,
                            defaultSsid = homeSsid
                        ), status.expiresIn)
                    } else {
                        step(ProvisioningStep.Error("Session started but no token received.", canRetry = true))
                    }
                }
                .onFailure { e ->
                    step(ProvisioningStep.Error("Failed to start session: ${e.message}", canRetry = true))
                }
        }
    }

    /**
     * Called when the user chooses WPS on the Connected screen.
     */
    fun startWps() {
        val connected = _uiState.value.step as? ProvisioningStep.Connected ?: return
        step(ProvisioningStep.StartingWps)
        viewModelScope.launch {
            repository.startWps(connected.gatewayIp)
                .onSuccess { response ->
                    if (response.status == 1) {
                        step(
                            ProvisioningStep.WpsInstructions(
                                message = response.result.ifEmpty { "WPS mode started on the controller." },
                                gatewayIp = connected.gatewayIp
                            )
                        )
                    } else {
                        step(
                            ProvisioningStep.Error(
                                response.result.ifEmpty { "Failed to start WPS mode." },
                                canRetry = true
                            )
                        )
                    }
                }
                .onFailure { e ->
                    step(ProvisioningStep.Error("Failed to start WPS mode: ${e.message}", canRetry = true))
                }
        }
    }

    /**
     * Called when the user submits SSID + password.
     */
    fun submitCredentials(ssid: String, password: String) {
        val currentStep = _uiState.value.step
        val (token, gatewayIp) = when (currentStep) {
            is ProvisioningStep.EnterCredentials ->
                Pair(currentStep.token, currentStep.gatewayIp)
            is ProvisioningStep.FinishFailed ->
                Pair(currentStep.token, currentStep.gatewayIp)
            else -> return
        }

        if (ssid.isBlank()) {
            step(ProvisioningStep.Error("SSID is required.", canRetry = true))
            return
        }

        step(ProvisioningStep.Submitting)
        viewModelScope.launch {
            repository.submitCredentials(gatewayIp, token, ssid, password)
                .onSuccess { response ->
                    when {
                        response.success == "1" ->
                            step(ProvisioningStep.Monitoring(response.token.ifEmpty { token }, gatewayIp), response.expiresIn)

                        response.state == "idle" -> {
                            restartSession(gatewayIp)
                        }

                        else ->
                            step(ProvisioningStep.Error(
                                response.message.ifEmpty { "Credential submission failed." },
                                canRetry = true
                            ))
                    }
                }
                .onFailure { e ->
                    step(ProvisioningStep.Error("Submit failed: ${e.message}", canRetry = true))
                }
        }
    }

    /** Called when the user taps "Finish". */
    fun finishProvisioning() {
        val monitoring = _uiState.value.step as? ProvisioningStep.Monitoring ?: return
        step(ProvisioningStep.Finishing)
        viewModelScope.launch {
            repository.finishProvisioning(monitoring.gatewayIp, monitoring.token)
                .onSuccess { response ->
                    if (response.success == "1") {
                        if (response.state == "rebooting") {
                            val rebootMs = response.rebootIn.toLongOrNull() ?: 3000L
                            enterRebooting(
                                response.message.ifEmpty { "Rebooting device…" },
                                rebootMs
                            )
                        } else {
                            step(ProvisioningStep.Success(
                                response.message.ifEmpty { "Device configured successfully." }
                            ), response.expiresIn)
                        }
                    } else {
                        step(ProvisioningStep.FinishFailed(
                            token      = monitoring.token,
                            gatewayIp  = monitoring.gatewayIp,
                            message    = response.message.ifEmpty { "Failed to connect to the network." }
                        ), response.expiresIn)
                    }
                }
                .onFailure { e ->
                    step(ProvisioningStep.Error("Finish failed: ${e.message}", canRetry = false))
                }
        }
    }

    private fun enterRebooting(message: String, rebootInMs: Long) {
        rebootJob?.cancel()
        step(ProvisioningStep.Rebooting(message, rebootInMs))
        rebootJob = viewModelScope.launch {
            var remaining = rebootInMs
            while (remaining > 0L) {
                delay(1000)
                remaining = (remaining - 1000L).coerceAtLeast(0L)
                val current = _uiState.value.step
                if (current !is ProvisioningStep.Rebooting) return@launch
                _uiState.value = ProvisioningUiState(
                    step = ProvisioningStep.Rebooting(message, remaining),
                    expiresInMs = 0L
                )
            }
            if (_uiState.value.step is ProvisioningStep.Rebooting) {
                step(ProvisioningStep.Success("Device configured successfully."))
            }
        }
    }

    /**
     * Called from FinishFailed — returns to EnterCredentials keeping the same
     * token (session is still active in pending state, no need to restart).
     */
    fun retryAfterFinishFail() {
        val failed = _uiState.value.step as? ProvisioningStep.FinishFailed ?: return
        step(ProvisioningStep.EnterCredentials(failed.token, failed.gatewayIp, homeSsid))
    }

    /** Returns to Idle (full restart). */
    fun reset() {
        expiryJob?.cancel()
        rebootJob?.cancel()
        scanJob?.cancel()
        releaseNetworkCallback()
        homeSsid = ""
        step(ProvisioningStep.Idle)
    }

    // ---- Private helpers -----------------------------------------------

    private fun shouldTrackExpiry(step: ProvisioningStep): Boolean =
        when (step) {
            is ProvisioningStep.Connected,
            is ProvisioningStep.StartingSession,
            is ProvisioningStep.EnterCredentials,
            is ProvisioningStep.Submitting,
            is ProvisioningStep.Monitoring,
            is ProvisioningStep.Finishing,
            is ProvisioningStep.FinishFailed -> true
            else -> false
        }

    private fun manageExpiry(step: ProvisioningStep, expiresInMs: Long) {
        expiryJob?.cancel()
        if (!shouldTrackExpiry(step) || expiresInMs <= 0L) return

        expiryJob = viewModelScope.launch {
            var remainingMs = expiresInMs
            while (remainingMs > 0L) {
                delay(1000)
                remainingMs = (remainingMs - 1000L).coerceAtLeast(0L)
                val currentState = _uiState.value
                if (currentState.step != step) return@launch
                _uiState.value = currentState.copy(expiresInMs = remainingMs)
            }

            if (_uiState.value.step == step) {
                _uiState.value = ProvisioningUiState(
                    step = ProvisioningStep.Expired("The 60-second setup window has expired."),
                    expiresInMs = 0L
                )
            }
        }
    }

    private fun releaseNetworkCallback() {
        try {
            val context = getApplication<Application>()
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
            connectivityManager.bindProcessToNetwork(null)
        } catch (_: Exception) {}
        networkCallback = null
    }

    private fun restartSession(gatewayIp: String) {
        step(ProvisioningStep.StartingSession)
        viewModelScope.launch {
            repository.startSession(gatewayIp)
                .onSuccess { status ->
                    if (status.token.isNotEmpty()) {
                        step(ProvisioningStep.EnterCredentials(
                            token = status.token,
                            gatewayIp = gatewayIp,
                            defaultSsid = homeSsid
                        ), status.expiresIn)
                    } else {
                        step(ProvisioningStep.Error("Session restart failed — no token.", canRetry = true))
                    }
                }
                .onFailure { e ->
                    step(ProvisioningStep.Error("Session restart failed: ${e.message}", canRetry = true))
                }
        }
    }
}
