package com.ambiback.manager.ui.devices

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    val focusManager = LocalFocusManager.current
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
    var manualIp by rememberSaveable { mutableStateOf("") }
    var manualIpError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                actions = {
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
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
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
            isRefreshing = uiState.isScanning,
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
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.devices, key = { it.deviceId }) { device ->
                            DeviceCard(
                                device = device,
                                unverified = device.deviceId in uiState.unverifiedIds,
                                onClick = { onNavigateToDevice(device.deviceIp, device.deviceName) },
                                onForget = {
                                    val removedLabel = device.deviceName.ifEmpty { device.deviceIp }
                                    scope.launch {
                                        viewModel.forgetDevice(device.deviceId)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Removed $removedLabel",
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
            title = { Text(stringResource(R.string.clear_all_devices)) },
            text = { Text(stringResource(R.string.clear_all_devices_confirm)) },
            confirmButton = {
                Button(onClick = {
                    showClearAllDialog = false
                    viewModel.forgetAllDevices()
                }) {
                    Text(stringResource(R.string.clear_all_devices))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddByIpDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isAddingByIp) showAddByIpDialog = false
            },
            title = { Text(stringResource(R.string.add_by_ip_title)) },
            text = {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = {
                        manualIp = it
                        manualIpError = null
                    },
                    label = { Text(stringResource(R.string.device_ip)) },
                    singleLine = true,
                    isError = manualIpError != null,
                    supportingText = manualIpError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (!IpValidation.isValidIp(manualIp)) {
                                manualIpError = "Invalid IP address"
                            } else {
                                viewModel.addDeviceByIp(manualIp) {
                                    manualIp = ""
                                    showAddByIpDialog = false
                                }
                            }
                        }
                    ),
                    enabled = !uiState.isAddingByIp,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!IpValidation.isValidIp(manualIp)) {
                            manualIpError = "Invalid IP address"
                        } else {
                            viewModel.addDeviceByIp(manualIp) {
                                manualIp = ""
                                showAddByIpDialog = false
                            }
                        }
                    },
                    enabled = !uiState.isAddingByIp
                ) {
                    Text(
                        if (uiState.isAddingByIp) stringResource(R.string.checking)
                        else stringResource(R.string.add)
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAddByIpDialog = false },
                    enabled = !uiState.isAddingByIp
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceCard(
    device: Device,
    unverified: Boolean,
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotColor = when (device.ledStatus) {
                LedMode.AMBIENT -> LedAmbient
                LedMode.VIDEO, LedMode.CAM -> LedVideo
                else -> LedOff
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape)
            )

            Icon(
                imageVector = Icons.Default.Router,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.deviceName.ifEmpty { stringResource(R.string.unnamed_device) },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (unverified) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.unverified),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
                Text(
                    text = device.deviceIp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.deviceType.isNotBlank() || device.deviceVersion.isNotBlank()) {
                    Text(
                        text = listOfNotNull(
                            device.deviceType.takeIf { it.isNotBlank() },
                            device.deviceVersion.takeIf { it.isNotBlank() }?.let { "v$it" }
                        ).joinToString("  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onForget) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.forget_device),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_devices_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
