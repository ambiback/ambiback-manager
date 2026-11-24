package com.ambiback.manager.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ambiback.manager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader(text = stringResource(R.string.settings_section_appearance))

            Column(modifier = Modifier.selectableGroup()) {
                val themeOptions = listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light"  to stringResource(R.string.theme_light),
                    "dark"   to stringResource(R.string.theme_dark)
                )
                themeOptions.forEach { (value, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(
                                selected = uiState.themeMode == value,
                                onClick  = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = uiState.themeMode == value,
                                onClick = { viewModel.setThemeMode(value) },
                                role = Role.RadioButton
                            )
                    )
                }
            }

            // Material You colors only exist on Android 12+; elsewhere the switch would do nothing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchListItem(
                    title = stringResource(R.string.setting_use_dynamic_color),
                    description = stringResource(R.string.setting_use_dynamic_color_desc),
                    checked = uiState.useDynamicColor,
                    onCheckedChange = { viewModel.setUseDynamicColor(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSectionHeader(text = stringResource(R.string.settings_section_general))

            SwitchListItem(
                title = stringResource(R.string.setting_scan_on_startup),
                description = stringResource(R.string.setting_scan_on_startup_desc),
                checked = uiState.scanOnStartup,
                onCheckedChange = { viewModel.setScanOnStartup(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SettingsSectionHeader(text = stringResource(R.string.settings_section_network))

            var timeoutInput by remember(uiState.httpTimeoutSec) {
                mutableStateOf(uiState.httpTimeoutSec.toString())
            }
            var timeoutInvalid by remember { mutableStateOf(false) }

            fun commitTimeout() {
                val parsed = timeoutInput.toIntOrNull()
                if (parsed == null || parsed !in 1..30) {
                    timeoutInvalid = true
                } else {
                    timeoutInvalid = false
                    viewModel.setHttpTimeoutSec(parsed)
                }
            }

            OutlinedTextField(
                value = timeoutInput,
                onValueChange = { raw ->
                    timeoutInput = raw.filter { it.isDigit() }.take(2)
                    timeoutInvalid = false
                },
                label   = { Text(stringResource(R.string.setting_http_timeout)) },
                suffix  = { Text(stringResource(R.string.setting_http_timeout_unit)) },
                isError = timeoutInvalid,
                // The description doubles as the error text, since it already states the range.
                supportingText = { Text(stringResource(R.string.setting_http_timeout_desc)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commitTimeout()
                        focusManager.clearFocus()
                    }
                ),
                singleLine = true,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) commitTimeout()
                    }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A settings row whose whole area toggles the switch, not just the switch itself. */
@Composable
private fun SwitchListItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent   = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent   = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    )
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
