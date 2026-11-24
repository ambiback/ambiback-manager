package com.ambiback.manager.ui.devices

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ambiback.manager.R
import com.ambiback.manager.data.model.Device
import com.ambiback.manager.data.model.LedMode
import com.ambiback.manager.ui.theme.LedAmbient
import com.ambiback.manager.ui.theme.LedOff
import com.ambiback.manager.ui.theme.LedVideo
import com.ambiback.manager.utils.IpValidation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onNavigateToDevice: (deviceIp: String, deviceName: String) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToProvisioning: () -> Unit = {},
    onNavigateToUsb: () -> Unit = {},
    viewModel: DeviceListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(uiState.error) {
        uiState.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    var overflowExpanded by remember { mutableStateOf(false) }
    var showAddByIpDialog by rememberSaveable { mutableStateOf(false) }
    var showClearAllDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                actions = {
                    // Pull to refresh is the gesture; this is the same action as a button.
                    // Progress shows in the list itself, so the button only disables.
                    IconButton(onClick = { viewModel.scan() }, enabled = !uiState.isScanning) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.scan_for_devices)
                        )
                    }
                    IconButton(onClick = onNavigateToProvisioning) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_device)
                        )
                    }
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_by_ip)) },
                            leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                showAddByIpDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.usb_device)) },
                            leadingIcon = { Icon(Icons.Default.Usb, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                onNavigateToUsb()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_title)) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                onNavigateToSettings()
                            }
                        )
                        if (uiState.devices.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_all_devices)) },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                },
                                onClick = {
                                    overflowExpanded = false
                                    showClearAllDialog = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about_title)) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                overflowExpanded = false
                                onNavigateToAbout()
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            // An empty list shows its own centered progress; avoid a second spinner on top.
            isRefreshing = uiState.isScanning && uiState.devices.isNotEmpty(),
            onRefresh = { viewModel.scan() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.devices.isEmpty() && uiState.isScanning -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.scanning_devices),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                uiState.devices.isEmpty() -> {
                    EmptyState(
                        onScan = { viewModel.scan() },
                        onAddByIp = { showAddByIpDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.devices, key = { it.deviceKey }) { device ->
                            DeviceCard(
                                device = device,
                                unverified = device.deviceKey in uiState.unverifiedKeys,
                                scanComplete = uiState.scanComplete,
                                onClick = { onNavigateToDevice(device.deviceIp, device.deviceName) },
                                onForget = {
                                    val removedLabel = device.deviceName.ifEmpty { device.deviceIp }
                                    val message = context.getString(R.string.device_removed, removedLabel)
                                    scope.launch {
                                        viewModel.forgetDevice(device.deviceKey)
                                        val result = snackbarHostState.showSnackbar(
                                            message = message,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreDevice(device)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.clear_all_devices)) },
            text = { Text(stringResource(R.string.clear_all_devices_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        viewModel.forgetAllDevices()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.clear_all_devices))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddByIpDialog) {
        AddByIpDialog(
            isChecking = uiState.isAddingByIp,
            failure = uiState.addByIpError,
            onSubmit = { ip ->
                viewModel.addDeviceByIp(ip) { showAddByIpDialog = false }
            },
            onEdit = { viewModel.clearAddByIpError() },
            onDismiss = {
                viewModel.clearAddByIpError()
                showAddByIpDialog = false
            }
        )
    }
}

@Composable
private fun AddByIpDialog(
    isChecking: Boolean,
    failure: String?,
    onSubmit: (String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var ip by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val errorText = when {
        invalid -> stringResource(R.string.invalid_ip)
        failure != null -> stringResource(R.string.add_by_ip_failed, ip.trim(), failure).trim()
        else -> null
    }

    fun submit() {
        focusManager.clearFocus()
        if (IpValidation.isValidIp(ip.trim())) onSubmit(ip.trim()) else invalid = true
    }

    AlertDialog(
        onDismissRequest = { if (!isChecking) onDismiss() },
        icon = { Icon(Icons.Default.Lan, contentDescription = null) },
        title = { Text(stringResource(R.string.add_by_ip_title)) },
        text = {
            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it
                    invalid = false
                    onEdit()
                },
                label = { Text(stringResource(R.string.device_ip)) },
                placeholder = { Text("192.168.0.20") },
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                enabled = !isChecking,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !isChecking && ip.isNotBlank()) {
                Text(stringResource(if (isChecking) R.string.checking else R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isChecking) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@StringRes
private fun modeLabel(mode: Int): Int? = when (mode) {
    LedMode.OFF -> R.string.mode_off
    LedMode.VIDEO -> R.string.mode_video
    LedMode.AMBIENT -> R.string.mode_ambient
    LedMode.CAM -> R.string.mode_camera
    LedMode.RELAY -> R.string.mode_relay
    LedMode.DEMO -> R.string.mode_demo
    else -> null
}

@Composable
private fun DeviceCard(
    device: Device,
    unverified: Boolean,
    scanComplete: Boolean,
    onClick: () -> Unit,
    onForget: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Router,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName.ifEmpty { stringResource(R.string.unnamed_device) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.deviceIp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                // Mode is written out next to the dot, so the state never depends on color alone.
                val mode = modeLabel(device.ledStatus)
                when {
                    unverified -> StatusBadge(
                        text = stringResource(if (scanComplete) R.string.not_found else R.string.unverified),
                        isWarning = scanComplete
                    )
                    mode != null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {}
                    ) {
                        val dotColor = when (device.ledStatus) {
                            LedMode.OFF -> LedOff
                            LedMode.AMBIENT -> LedAmbient
                            else -> LedVideo
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(dotColor, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = listOfNotNull(
                                stringResource(mode),
                                device.deviceVersion.takeIf { it.isNotBlank() }?.let { "v$it" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onForget) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.forget_device),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Small non-interactive label; a chip would be announced as a button that does nothing. */
@Composable
private fun StatusBadge(text: String, isWarning: Boolean) {
    val container = if (isWarning) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
    val content = if (isWarning) MaterialTheme.colorScheme.onErrorContainer
                  else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun EmptyState(onScan: () -> Unit, onAddByIp: () -> Unit, modifier: Modifier) {
    // Must scroll: PullToRefreshBox only sees the pull gesture through a scrollable child.
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Router,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_devices_found),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_devices_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScan) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.scan_for_devices))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAddByIp) {
            Icon(Icons.Default.Lan, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_by_ip))
        }
    }
}
