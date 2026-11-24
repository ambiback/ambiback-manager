package com.ambiback.manager.ui.provisioning

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProvisioningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.ensureHomeSsidCaptured()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Device") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.expiresInMs > 0) {
                BottomAppBar {
                    Text(
                        text = "Setup window expires in: ${uiState.expiresInMs / 1000}s",
                        style = MaterialTheme.typography.labelLarge,
                        // Only turn red when time is actually running short.
                        color = if (uiState.expiresInMs <= 15_000) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val step = uiState.step) {
                is ProvisioningStep.Idle ->
                    IdleContent(
                        onAutoConnect = { viewModel.startPreScan() },
                        onManualConnect = { viewModel.onUserJoinedAp() }
                    )

                is ProvisioningStep.Scanning ->
                    ProgressContent(
                        message = step.message,
                        onCancel = { viewModel.cancelAutoConnect() }
                    )

                is ProvisioningStep.SelectDevice ->
                    SelectDeviceContent(
                        ssids = step.ssids,
                        onSelect = { viewModel.onDeviceSelected(it) }
                    )

                is ProvisioningStep.WaitingForAutoConnect ->
                    ProgressContent(
                        message = "Waiting for you to select an AmbiBack device…",
                        onCancel = { viewModel.cancelAutoConnect() }
                    )

                is ProvisioningStep.ResolvingGateway ->
                    ProgressContent(message = "Resolving controller address…")

                is ProvisioningStep.Connected ->
                    ConnectedContent(
                        step = step,
                        onStartProvisioning = { viewModel.startSession() },
                        onStartWps = { viewModel.startWps() }
                    )

                is ProvisioningStep.StartingSession ->
                    ProgressContent(message = "Starting session…")

                is ProvisioningStep.StartingWps ->
                    ProgressContent(message = "Starting WPS mode…")

                is ProvisioningStep.EnterCredentials ->
                    CredentialEntryContent(
                        step = step,
                        onSubmit = { ssid, password -> viewModel.submitCredentials(ssid, password) }
                    )

                is ProvisioningStep.Submitting ->
                    ProgressContent(message = "Submitting credentials…")

                is ProvisioningStep.Monitoring ->
                    MonitoringContent(onFinish = { viewModel.finishProvisioning() })

                is ProvisioningStep.Finishing ->
                    ProgressContent(message = "Applying credentials…")

                is ProvisioningStep.Rebooting ->
                    ProgressContent(
                        message = "${step.message}\n(Rebooting in ${(step.remainingMs / 1000).coerceAtLeast(0)}s)"
                    )

                is ProvisioningStep.Expired ->
                    ExpiredContent(step = step, onReset = { viewModel.reset() })

                is ProvisioningStep.FinishFailed ->
                    FinishFailedContent(
                        step = step,
                        onEditCredentials = { viewModel.retryAfterFinishFail() },
                        onReset = { viewModel.reset() }
                    )

                is ProvisioningStep.Success ->
                    SuccessContent(onDone = onNavigateBack)

                is ProvisioningStep.WpsInstructions ->
                    WpsInstructionsContent(step = step, onDone = onNavigateBack)

                is ProvisioningStep.Error ->
                    ErrorContent(
                        step = step,
                        onRetry = { viewModel.reset() },
                        onBack  = onNavigateBack
                    )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step composables
// ---------------------------------------------------------------------------

@Composable
private fun IdleContent(onAutoConnect: () -> Unit, onManualConnect: () -> Unit) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }

    val scanPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }

    fun hasScanPermissions(): Boolean =
        scanPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            permissionDenied = false
            onAutoConnect()
        } else {
            permissionDenied = true
        }
    }

    StepCard {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Add AmbiBack Device", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Power on the controller in AP mode, then scan to connect automatically.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(24.dp))

        if (permissionDenied) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Wi‑Fi and location permissions are required to scan for nearby AmbiBack devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open App Settings")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (hasScanPermissions()) onAutoConnect()
                else permissionLauncher.launch(scanPermissions)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan for New Devices")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onManualConnect, modifier = Modifier.fillMaxWidth()) {
            Text("I've connected to the device WiFi manually")
        }
    }
}

@Composable
private fun SelectDeviceContent(ssids: List<String>, onSelect: (String) -> Unit) {
    StepCard {
        Text("Select Device", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "Multiple AmbiBack devices found. Which one would you like to configure?",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            ssids.forEach { ssid ->
                OutlinedButton(
                    onClick = { onSelect(ssid) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(ssid)
                }
            }
        }
    }
}

@Composable
private fun ProgressContent(message: String, onCancel: (() -> Unit)? = null) {
    StepCard(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onCancel != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    step: ProvisioningStep.Connected,
    onStartProvisioning: () -> Unit,
    onStartWps: () -> Unit
) {
    StepCard {
        Text("Controller Found", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        InfoRow("AP SSID", step.status.apSsid.ifEmpty { "—" })
        InfoRow("AP IP",   step.gatewayIp)
        InfoRow("State",   step.status.state)
        Spacer(Modifier.height(12.dp))
        Text(
            "Choose how you want to connect the controller to your WiFi network.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStartWps, modifier = Modifier.fillMaxWidth()) {
            Text("Start WPS Mode")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStartProvisioning, modifier = Modifier.fillMaxWidth()) {
            Text("Use WiFi Credentials")
        }
    }
}

@Composable
private fun CredentialEntryContent(
    step: ProvisioningStep.EnterCredentials,
    onSubmit: (ssid: String, password: String) -> Unit
) {
    var ssid     by rememberSaveable(step.defaultSsid) { mutableStateOf(step.defaultSsid) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var ssidError by remember { mutableStateOf(false) }

    StepCard {
        Text("Enter WiFi Credentials", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Enter the SSID and password of the WiFi network the controller should join.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value         = ssid,
            onValueChange = { ssid = it; ssidError = false },
            label         = { Text("WiFi SSID") },
            singleLine    = true,
            isError       = ssidError,
            supportingText = if (ssidError) {
                { Text("SSID is required") }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value         = password,
            onValueChange = { password = it },
            label         = { Text("Password (leave empty for open network)") },
            singleLine    = true,
            visualTransformation = if (showPass) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(
                        if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPass) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (ssid.isBlank()) { ssidError = true } else { onSubmit(ssid, password) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun MonitoringContent(onFinish: () -> Unit) {
    StepCard(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text("Credentials Staged", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "The controller has the credentials ready. Tap Finish to apply them.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Finish")
        }
    }
}

@Composable
private fun FinishFailedContent(
    step: ProvisioningStep.FinishFailed,
    onEditCredentials: () -> Unit,
    onReset: () -> Unit
) {
    StepCard(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        Text("Connection Failed", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            step.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You can edit the credentials and try again, or start over.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onEditCredentials, modifier = Modifier.fillMaxWidth()) {
            Text("Edit Credentials")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Start Over")
        }
    }
}

@Composable
private fun SuccessContent(onDone: () -> Unit) {
    StepCard(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Provisioning Finished", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Device credentials stored! Reconnect your phone to your home WiFi network, then scan for the device in the AmbiBack app.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

@Composable
private fun WpsInstructionsContent(step: ProvisioningStep.WpsInstructions, onDone: () -> Unit) {
    StepCard(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("WPS Started", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            step.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Press the WPS button on your router now. The controller may leave setup WiFi or reboot while pairing.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "After that, reconnect your phone to the normal WiFi network and scan for the device in the AmbiBack app.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Devices")
        }
    }
}

@Composable
private fun ExpiredContent(step: ProvisioningStep.Expired, onReset: () -> Unit) {
    StepCard(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text("Setup Expired", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = step.message.ifEmpty { "The 60-second setup window has expired. Please restart the AmbiBack controller and try again." },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Start Over")
        }
    }
}

@Composable
private fun ErrorContent(
    step: ProvisioningStep.Error,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    StepCard(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        Text("Error", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            step.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(24.dp))
        if (step.canRetry) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Start Over")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun StepCard(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable (ColumnScope.() -> Unit)
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.Top,
            content             = content
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
