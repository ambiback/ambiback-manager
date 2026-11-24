package com.ambiback.manager.ui.usb

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ambiback.manager.data.usb.UsbConnectionState
import com.ambiback.manager.data.usb.UsbDeviceInfo
import com.ambiback.manager.ui.theme.LedAmbient
import com.ambiback.manager.ui.theme.LedVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbDeviceScreen(
    onNavigateBack: () -> Unit,
    viewModel: UsbDeviceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Navigate back when device disconnects ─────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onNavigateBack() }
    }

    // ── Keep screen on while flashing ─────────────────────────────────────────
    val activity = context as? Activity
    DisposableEffect(state.isFlashing) {
        if (state.isFlashing) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // ── Snackbar on command completion ────────────────────────────────────────
    LaunchedEffect(state.commandStatus) {
        when (val s = state.commandStatus) {
            is UsbDeviceViewModel.CommandStatus.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearStatus()
            }
            is UsbDeviceViewModel.CommandStatus.Error -> {
                snackbarHostState.showSnackbar("Error: ${s.message}")
                viewModel.clearStatus()
            }
            else -> Unit
        }
    }

    // ── Location permission — needed to read current WiFi SSID ────────────────
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.loadCurrentSsid()
    }
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) viewModel.loadCurrentSsid()
    }

    // ── Firmware zip picker ───────────────────────────────────────────────────
    val firmwarePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = getFileName(context, uri) ?: uri.lastPathSegment ?: "firmware.zip"
        viewModel.setSelectedFirmware(uri, name)
    }

    // ── Confirmation dialog state ─────────────────────────────────────────────
    var showResetWifiDialog    by remember { mutableStateOf(false) }
    var showFactoryResetDialog by remember { mutableStateOf(false) }
    var showFlashConfirmDialog by remember { mutableStateOf(false) }
    var passwordVisible        by remember { mutableStateOf(false) }
    var passwordText           by remember { mutableStateOf("") }

    val isBusy = state.commandStatus is UsbDeviceViewModel.CommandStatus.Loading ||
            state.isFlashing ||
            state.connectionState is UsbConnectionState.Reconnecting

    // ── Confirmation dialogs ──────────────────────────────────────────────────
    if (showResetWifiDialog) {
        AlertDialog(
            onDismissRequest = { showResetWifiDialog = false },
            icon    = { Icon(Icons.Default.WifiOff, contentDescription = null) },
            title   = { Text("Reset WiFi?") },
            text    = { Text("This will erase the device's stored WiFi credentials. The device will reboot and disconnect from the network.") },
            confirmButton = {
                TextButton(onClick = { showResetWifiDialog = false; viewModel.resetWifi() }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetWifiDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showFactoryResetDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryResetDialog = false },
            icon    = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Factory Reset?") },
            text    = { Text("This will erase ALL device settings including WiFi, LED configuration and relay data. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { showFactoryResetDialog = false; viewModel.factoryReset() },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Factory Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showFlashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFlashConfirmDialog = false },
            icon    = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Flash Firmware?") },
            text    = {
                Text(
                    "This will erase and rewrite the device firmware. " +
                        "Do not unplug the USB cable or leave this screen until flashing completes. " +
                        "Selected: ${state.selectedFirmwareName ?: "firmware.zip"}"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showFlashConfirmDialog = false
                    viewModel.flashFirmware()
                }) { Text("Flash") }
            },
            dismissButton = {
                TextButton(onClick = { showFlashConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USB Device") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Connection Status ─────────────────────────────────────────────
            ConnectionStatusCard(
                state          = state.connectionState,
                onOpenUsbSettings = {
                    try {
                        context.startActivity(Intent("android.settings.USB_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {}
                    }
                }
            )

            // ── Device Info ───────────────────────────────────────────────────
            SectionCard(title = "Device Info") {
                Button(
                    onClick   = { viewModel.getInfo() },
                    enabled   = !isBusy,
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    if (state.commandStatus is UsbDeviceViewModel.CommandStatus.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Get Info")
                }

                AnimatedVisibility(visible = state.deviceInfo != null) {
                    state.deviceInfo?.let { DeviceInfoCard(it) }
                }
            }

            // ── WiFi Configuration ────────────────────────────────────────────
            SectionCard(title = "WiFi Configuration") {
                // The permission only pre-fills the SSID; typing it by hand always works.
                if (!hasLocationPermission) {
                    PermissionExplanationCard(
                        onGrantClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                run {
                    OutlinedTextField(
                        value         = state.wifiSsid,
                        onValueChange = { viewModel.updateWifiSsid(it) },
                        label         = { Text("WiFi SSID") },
                        leadingIcon   = { Icon(Icons.Default.Wifi, contentDescription = null) },
                        singleLine    = true,
                        enabled       = !isBusy,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value         = passwordText,
                        onValueChange = { passwordText = it },
                        label         = { Text("WiFi Password") },
                        singleLine    = true,
                        enabled       = !isBusy,
                        modifier      = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible)
                            VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                // Icon shows the action, as on the setup screen.
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier             = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick  = { viewModel.setWifi(state.wifiSsid, passwordText) },
                            enabled  = !isBusy && state.wifiSsid.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Set WiFi")
                        }

                        OutlinedButton(
                            onClick  = { showResetWifiDialog = true },
                            enabled  = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.WifiOff, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reset WiFi")
                        }
                    }
                }
            }

            // ── Firmware Update ───────────────────────────────────────────────
            SectionCard(title = "Firmware Update") {
                OutlinedButton(
                    onClick  = { firmwarePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    enabled  = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Flash Package (.zip)…")
                }

                if (state.selectedFirmwareName != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = state.selectedFirmwareName!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick  = { showFlashConfirmDialog = true },
                    enabled  = !isBusy && state.selectedFirmwareUri != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Flash Firmware")
                }

                AnimatedVisibility(visible = state.isFlashing || (state.flashProgress > 0f && state.flashProgress < 1f)) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        LinearProgressIndicator(
                            progress         = { state.flashProgress },
                            modifier         = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = state.flashStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Danger Zone (last, away from everyday actions) ────────────────
            SectionCard(title = "Danger Zone") {
                OutlinedButton(
                    onClick  = { showFactoryResetDialog = true },
                    enabled  = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Factory Reset")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun ConnectionStatusCard(
    state: UsbConnectionState,
    onOpenUsbSettings: () -> Unit = {}
) {
    val (label, dotColor, hint) = when (state) {
        is UsbConnectionState.Connected ->
            Triple("Connected: ${state.deviceName}", LedAmbient, null)
        is UsbConnectionState.Reconnecting ->
            Triple(
                "Reconnecting…", LedVideo,
                "USB mode changed — waiting for device to re-enumerate. This takes a few seconds."
            )
        is UsbConnectionState.RequestingPermission ->
            Triple(
                "Requesting USB permission…", LedVideo,
                "Tap Allow in the Android permission dialog. If it disappeared, unplug and replug the cable."
            )
        is UsbConnectionState.Disconnected ->
            Triple(
                "Disconnected", MaterialTheme.colorScheme.error,
                "USB cable connected but not detected? Select \"This device\" in Android USB settings so the phone acts as USB host."
            )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Usb, contentDescription = null)
                Surface(shape = CircleShape, color = dotColor, modifier = Modifier.size(10.dp)) {}
                Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            if (hint != null) {
                Text(
                    text  = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state is UsbConnectionState.Disconnected) {
                OutlinedButton(
                    onClick  = onOpenUsbSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Usb, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open USB Settings")
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(info: UsbDeviceInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow("Type",    info.deviceType)
            InfoRow("Version", info.deviceVersion)
            InfoRow("ID",      info.deviceId)
            InfoRow("MAC",     info.deviceMac)
            InfoRow("Name",    info.deviceName)
            InfoRow("IP",      info.deviceIp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text       = "$label:",
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PermissionExplanationCard(onGrantClick: () -> Unit) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text  = "Fill in the SSID automatically?",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text  = "Android only shares the name of your current WiFi network (SSID) with apps that have Location permission. It is used for nothing else. You can also type the SSID below.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onGrantClick, modifier = Modifier.fillMaxWidth()) {
                Text("Grant Location Permission")
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun getFileName(context: Context, uri: android.net.Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
    }
} catch (_: Exception) { null }
