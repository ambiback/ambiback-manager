package com.ambiback.manager.ui.device

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ambiback.manager.R
import com.ambiback.manager.data.model.DeviceConfig
import com.ambiback.manager.data.model.DeviceStatus
import com.ambiback.manager.data.model.LedMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class PresetInfo(
    @StringRes val label: Int,
    val gradient: List<Color>,
    /** Fast flicker presets get a photosensitivity warning before they start. */
    val flickers: Boolean = false
)

/** Index in this list is the firmware's `ambientpreset` value. */
private val AMBIENT_SCENES = listOf(
    PresetInfo(R.string.scene_solid,        listOf(Color(0xFFD0D0D0), Color(0xFFFFFFFF))),
    PresetInfo(R.string.scene_fire,         listOf(Color(0xFFBB0000), Color(0xFFFF6600), Color(0xFFFFCC00))),
    PresetInfo(R.string.scene_christmas,    listOf(Color(0xFFCC0000), Color(0xFF00AA00))),
    PresetInfo(R.string.scene_halloween,    listOf(Color(0xFFFF6600), Color(0xFF440088))),
    PresetInfo(R.string.scene_ocean,        listOf(Color(0xFF001A66), Color(0xFF0077CC), Color(0xFF00CCCC))),
    PresetInfo(R.string.scene_sunset,       listOf(Color(0xFFFF3366), Color(0xFFFF7700), Color(0xFF6633CC))),
    PresetInfo(R.string.scene_aurora,       listOf(Color(0xFF00CC66), Color(0xFF009999), Color(0xFF0033CC))),
    PresetInfo(R.string.scene_candle,       listOf(Color(0xFFCC5500), Color(0xFFFF9900), Color(0xFFFFCC44))),
    PresetInfo(R.string.scene_rainbow,      listOf(Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF))),
    PresetInfo(R.string.scene_thunderstorm, listOf(Color(0xFF001133), Color(0xFF445566), Color(0xFF6688BB))),
    PresetInfo(R.string.scene_party2,       listOf(Color(0xFFFF0099), Color(0xFFFF6600))),
    PresetInfo(R.string.scene_party,        listOf(Color(0xFF00FFCC), Color(0xFFFF00FF))),
)

/** Presets 12–15. */
private val HEALTH_SCENES = listOf(
    PresetInfo(R.string.scene_gamma40,     listOf(Color(0xFF001166), Color(0xFF0044CC), Color(0xFF001166)), flickers = true),
    PresetInfo(R.string.scene_alpha10,     listOf(Color(0xFF003399), Color(0xFF6699FF), Color(0xFF003399)), flickers = true),
    PresetInfo(R.string.scene_gamma34_red, listOf(Color(0xFF660000), Color(0xFFCC0000), Color(0xFF660000)), flickers = true),
    PresetInfo(R.string.scene_red_evening, listOf(Color(0xFF330000), Color(0xFF880000), Color(0xFF330000))),
)

private val HEALTH_PRESET_START = AMBIENT_SCENES.size
private const val SOLID_PRESET = 0
private val MAX_PRESET = HEALTH_PRESET_START + HEALTH_SCENES.size - 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickControlContent(
    uiState: DeviceDetailUiState,
    viewModel: DeviceDetailViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = uiState.configState) {
            is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is UiState.Error -> LoadErrorState(
                message = stringResource(R.string.config_load_failed),
                detail = s.message,
                onRetry = { viewModel.loadConfig(); viewModel.loadStatus() },
                modifier = Modifier.align(Alignment.Center)
            )
            is UiState.Success -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                QuickControlForm(
                    config = s.data,
                    status = (uiState.statusState as? UiState.Success)?.data,
                    statusVersion = uiState.statusVersion,
                    sendingControls = uiState.sendingControls,
                    claimKey = uiState.claimKey,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Shared full-screen error with a retry button, used by all device tabs. */
@Composable
internal fun LoadErrorState(
    message: String,
    detail: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 480.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun QuickControlForm(
    config: DeviceConfig,
    status: DeviceStatus?,
    statusVersion: Int,
    sendingControls: Set<SendingControl>,
    claimKey: String?,
    viewModel: DeviceDetailViewModel,
    snackbarHostState: SnackbarHostState
) {
    val hasCamera = config.hasCamera
    val defaultOnMode = if (hasCamera) LedMode.CAM else LedMode.AMBIENT
    // Mirrors the web UI: camera controllers only offer Camera mode.
    val modes = if (hasCamera) {
        listOf(R.string.mode_camera to LedMode.CAM)
    } else {
        listOf(R.string.mode_ambient to LedMode.AMBIENT, R.string.mode_video to LedMode.VIDEO)
    }
    val reportedMode = status?.ledMode ?: config.ledMode

    // Optimistic UI state; resynced to the controller on every status reply.
    var isOn by remember(statusVersion, config) { mutableStateOf(reportedMode != LedMode.OFF) }
    var selectedMode by remember(statusVersion, config) {
        mutableIntStateOf(reportedMode.takeIf { it != LedMode.OFF } ?: defaultOnMode)
    }
    var selectedPreset by remember(config) { mutableIntStateOf(config.ambientPreset.coerceIn(0, MAX_PRESET)) }
    var brightness by remember(config) { mutableFloatStateOf(config.brightness.coerceIn(0, 255).toFloat()) }
    var speed by remember(config) { mutableFloatStateOf(config.ambientSpeed.coerceIn(1, 10).toFloat()) }
    var intensity by remember(config) { mutableFloatStateOf(config.ambientIntensity.coerceIn(1, 100).toFloat()) }
    var healthDepth by remember(config) {
        mutableFloatStateOf(config.ambientHealthIntensity.coerceIn(1, 100).toFloat())
    }
    var pickedColor by remember { mutableStateOf(Color(1f, 0.5f, 0f)) }
    var pendingFlickerPreset by rememberSaveable { mutableStateOf<Int?>(null) }
    var flickerAcknowledged by rememberSaveable { mutableStateOf(false) }

    fun busy(control: SendingControl) = control in sendingControls

    fun applyPreset(index: Int) {
        selectedPreset = index
        selectedMode = LedMode.AMBIENT
        isOn = true
        viewModel.setPreset(index)
    }

    fun choosePreset(index: Int) {
        val preset = HEALTH_SCENES.getOrNull(index - HEALTH_PRESET_START)
        if (preset?.flickers == true && !flickerAcknowledged) {
            pendingFlickerPreset = index
        } else {
            applyPreset(index)
        }
    }

    pendingFlickerPreset?.let { index ->
        val name = stringResource(HEALTH_SCENES[index - HEALTH_PRESET_START].label)
        AlertDialog(
            onDismissRequest = { pendingFlickerPreset = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.flicker_title)) },
            text = { Text(stringResource(R.string.flicker_text, name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingFlickerPreset = null
                    flickerAcknowledged = true
                    applyPreset(index)
                }) { Text(stringResource(R.string.flicker_start)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingFlickerPreset = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        status?.let { StatusCard(it) }

        ControlSection(title = stringResource(R.string.section_power)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = isOn,
                        enabled = !busy(SendingControl.Power),
                        role = Role.Switch,
                        onValueChange = { on ->
                            isOn = on
                            if (on) viewModel.turnOn(selectedMode) else viewModel.turnOff()
                        }
                    )
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (isOn) R.string.leds_on else R.string.leds_off),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isOn,
                    onCheckedChange = null,
                    enabled = !busy(SendingControl.Power)
                )
            }
        }

        if (modes.size > 1) {
            ControlSection(title = stringResource(R.string.section_mode)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEach { (label, mode) ->
                        FilterChip(
                            selected = isOn && selectedMode == mode,
                            onClick = {
                                selectedMode = mode
                                isOn = true
                                viewModel.setMode(mode)
                            },
                            label = { Text(stringResource(label)) },
                            enabled = !busy(SendingControl.Mode)
                        )
                    }
                }
            }
        }

        ControlSection(title = stringResource(R.string.section_brightness)) {
            SliderWithValue(
                label = stringResource(R.string.section_brightness),
                value = brightness,
                valueText = { "${(it * 100f / 255f).roundToInt()} %" },
                onValueChange = { brightness = it },
                onValueChangeFinished = { viewModel.setBrightness(brightness.roundToInt()) },
                valueRange = 0f..255f,
                enabled = !busy(SendingControl.Brightness),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (!hasCamera) {
            ControlSection(title = stringResource(R.string.section_color)) {
                SimpleColorPicker(
                    initialColor = pickedColor,
                    onColorSelected = { color ->
                        pickedColor = color
                        selectedPreset = SOLID_PRESET
                        selectedMode = LedMode.AMBIENT
                        isOn = true
                        viewModel.setColor(color)
                    },
                    enabled = !busy(SendingControl.Color),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            CollapsibleSection(
                title = stringResource(R.string.section_scenes),
                subtitle = stringResource(R.string.scenes_desc),
                defaultExpanded = true
            ) {
                PresetGrid(
                    presets = AMBIENT_SCENES,
                    startIndex = 0,
                    selectedPreset = selectedPreset,
                    enabled = !busy(SendingControl.Preset),
                    onPresetSelected = ::choosePreset
                )
                Spacer(Modifier.height(4.dp))
                SliderWithValue(
                    label = stringResource(R.string.scene_speed),
                    showLabel = true,
                    value = speed,
                    valueText = { it.roundToInt().toString() },
                    onValueChange = { speed = it },
                    onValueChangeFinished = { viewModel.setAmbientSpeed(speed.roundToInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    enabled = !busy(SendingControl.Speed)
                )
                SliderWithValue(
                    label = stringResource(R.string.scene_intensity),
                    showLabel = true,
                    value = intensity,
                    valueText = { it.roundToInt().toString() },
                    onValueChange = { intensity = it },
                    onValueChangeFinished = { viewModel.setAmbientIntensity(intensity.roundToInt()) },
                    valueRange = 1f..100f,
                    enabled = !busy(SendingControl.Intensity)
                )
                Spacer(Modifier.height(8.dp))
            }

            CollapsibleSection(
                title = stringResource(R.string.section_health),
                subtitle = stringResource(R.string.health_desc),
                // Open when a health scene is running, so its controls are in view.
                defaultExpanded = selectedPreset >= HEALTH_PRESET_START
            ) {
                PresetGrid(
                    presets = HEALTH_SCENES,
                    startIndex = HEALTH_PRESET_START,
                    selectedPreset = selectedPreset,
                    enabled = !busy(SendingControl.Preset),
                    onPresetSelected = ::choosePreset
                )
                Spacer(Modifier.height(4.dp))
                SliderWithValue(
                    label = stringResource(R.string.health_depth),
                    showLabel = true,
                    supportingText = stringResource(R.string.health_depth_desc),
                    value = healthDepth,
                    valueText = { it.roundToInt().toString() },
                    onValueChange = { healthDepth = it },
                    onValueChangeFinished = { viewModel.setHealthPulseFloor(healthDepth.roundToInt()) },
                    valueRange = 1f..100f,
                    enabled = !busy(SendingControl.HealthFloor)
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        claimKey?.let { DeviceKeyCard(it, snackbarHostState) }

        // Room for the snackbar so it never covers the last control.
        Spacer(Modifier.height(72.dp))
    }
}

/** Shows the pairing key, which the browser UI needs for protected actions. */
@Composable
private fun DeviceKeyCard(claimKey: String, snackbarHostState: SnackbarHostState) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.device_key_copied)
    var visible by remember(claimKey) { mutableStateOf(false) }

    ControlSection(title = stringResource(R.string.device_key_title)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.device_key_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (visible) claimKey else "•".repeat(16),
                style = MaterialTheme.typography.bodyLarge
            )
            Row {
                TextButton(onClick = { visible = !visible }) {
                    Text(stringResource(if (visible) R.string.hide else R.string.show))
                }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(claimKey))
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                }) {
                    Text(stringResource(R.string.copy))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: DeviceStatus) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusMetric(
                    stringResource(R.string.status_wifi),
                    if (status.wifiSignal != 0) "${status.wifiSignal} dBm" else "—"
                )
                StatusMetric(stringResource(R.string.status_led_fps), status.fpsLedOut.toString())
                StatusMetric(stringResource(R.string.status_net_fps), status.fpsNetIn.toString())
                StatusMetric(stringResource(R.string.status_heap), formatHeap(status.freeHeap))
            }
            if (status.wifiName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = status.wifiName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {}
    ) {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatHeap(bytes: Int): String = when {
    bytes <= 0 -> "—"
    bytes >= 1024 * 1024 -> "%.1fM".format(bytes / (1024f * 1024f))
    bytes >= 1024 -> "${bytes / 1024}K"
    else -> bytes.toString()
}

@Composable
private fun PresetGrid(
    presets: List<PresetInfo>,
    startIndex: Int,
    selectedPreset: Int,
    enabled: Boolean,
    onPresetSelected: (Int) -> Unit
) {
    // Two columns on phones, more when the screen is wider (tablet, landscape).
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 720.dp -> 4
            maxWidth >= 480.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.chunked(columns).forEachIndexed { chunkIdx, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEachIndexed { rowIdx, preset ->
                        val presetIndex = startIndex + chunkIdx * columns + rowIdx
                        PresetButton(
                            label = stringResource(preset.label),
                            gradientColors = preset.gradient,
                            isSelected = selectedPreset == presetIndex,
                            onClick = { onPresetSelected(presetIndex) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    gradientColors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { selected = isSelected },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Brush.horizontalGradient(gradientColors))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Slider with its current value beside it. [label] names the slider for screen readers and,
 * with [showLabel], is shown above it.
 */
@Composable
private fun SliderWithValue(
    label: String,
    value: Float,
    valueText: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    supportingText: String? = null,
    steps: Int = 0
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label },
                enabled = enabled
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .widthIn(min = 56.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueText(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ControlSection(
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            content()
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    subtitle: String,
    defaultExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }
    val toggleLabel = stringResource(if (expanded) R.string.collapse else R.string.expand)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = toggleLabel, role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    content()
                }
            }
        }
    }
}

@Composable
private fun SimpleColorPicker(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val hsv = remember(initialColor) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.argb(
                255,
                (initialColor.red * 255).toInt(),
                (initialColor.green * 255).toInt(),
                (initialColor.blue * 255).toInt()
            ),
            arr
        )
        arr
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1].coerceIn(0.05f, 1f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceIn(0.05f, 1f)) }

    val selectedColor = Color.hsv(hue, saturation, value)
    val selectedLabel = stringResource(R.string.color_selected)
    val hueLabel = stringResource(R.string.color_hue)
    val saturationLabel = stringResource(R.string.color_saturation)
    val brightnessLabel = stringResource(R.string.color_brightness)
    fun commit() = onColorSelected(Color.hsv(hue, saturation, value))

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hueLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(selectedColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .semantics { contentDescription = selectedLabel }
            )
        }
        // The color spectrum is the hue slider's track, so the thumb sits on the chosen color.
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.horizontalGradient((0..12).map { i -> Color.hsv(i * 30f, 1f, 1f) }))
            )
            Slider(
                value = hue,
                onValueChange = { hue = it },
                onValueChangeFinished = ::commit,
                valueRange = 0f..360f,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = hueLabel }
            )
        }
        Text(saturationLabel, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            onValueChangeFinished = ::commit,
            valueRange = 0f..1f,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = saturationLabel }
        )
        Text(brightnessLabel, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = ::commit,
            valueRange = 0f..1f,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = brightnessLabel }
        )
    }
}
