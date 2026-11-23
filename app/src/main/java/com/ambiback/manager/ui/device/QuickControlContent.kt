package com.ambiback.manager.ui.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ambiback.manager.data.model.DeviceConfig
import com.ambiback.manager.data.model.DeviceStatus
import com.ambiback.manager.data.model.LedMode

private data class PresetInfo(val label: String, val gradient: List<Color>)

private val AMBIENT_SCENES = listOf(
    PresetInfo("Solid",        listOf(Color(0xFFD0D0D0), Color(0xFFFFFFFF))),
    PresetInfo("Fire",         listOf(Color(0xFFBB0000), Color(0xFFFF6600), Color(0xFFFFCC00))),
    PresetInfo("Christmas",    listOf(Color(0xFFCC0000), Color(0xFF00AA00))),
    PresetInfo("Halloween",    listOf(Color(0xFFFF6600), Color(0xFF440088))),
    PresetInfo("Ocean",        listOf(Color(0xFF001A66), Color(0xFF0077CC), Color(0xFF00CCCC))),
    PresetInfo("Sunset",       listOf(Color(0xFFFF3366), Color(0xFFFF7700), Color(0xFF6633CC))),
    PresetInfo("Aurora",       listOf(Color(0xFF00CC66), Color(0xFF009999), Color(0xFF0033CC))),
    PresetInfo("Candle",       listOf(Color(0xFFCC5500), Color(0xFFFF9900), Color(0xFFFFCC44))),
    PresetInfo("Rainbow",      listOf(Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF))),
    PresetInfo("Thunderstorm", listOf(Color(0xFF001133), Color(0xFF445566), Color(0xFF6688BB))),
    PresetInfo("Party 2",      listOf(Color(0xFFFF0099), Color(0xFFFF6600))),
    PresetInfo("Party",        listOf(Color(0xFF00FFCC), Color(0xFFFF00FF))),
)

private val HEALTH_SCENES = listOf(
    PresetInfo("40 Hz Gamma",      listOf(Color(0xFF001166), Color(0xFF0044CC), Color(0xFF001166))),
    PresetInfo("10 Hz Alpha",      listOf(Color(0xFF003399), Color(0xFF6699FF), Color(0xFF003399))),
    PresetInfo("34 Hz Gamma\n(Red)", listOf(Color(0xFF660000), Color(0xFFCC0000), Color(0xFF660000))),
    PresetInfo("Rotlicht\nAbend",  listOf(Color(0xFF330000), Color(0xFF880000), Color(0xFF330000))),
)

private val HEALTH_PRESET_START = AMBIENT_SCENES.size

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
            is UiState.Error -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Could not load config: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.loadConfig(); viewModel.loadStatus() }) {
                    Text("Retry")
                }
            }
            is UiState.Success -> QuickControlForm(
                config = s.data,
                status = (uiState.statusState as? UiState.Success)?.data,
                sendingControls = uiState.sendingControls,
                claimKey = uiState.claimKey,
                viewModel = viewModel
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun QuickControlForm(
    config: DeviceConfig,
    status: DeviceStatus?,
    sendingControls: Set<SendingControl>,
    claimKey: String?,
    viewModel: DeviceDetailViewModel
) {
    var isOn by remember(status) {
        mutableStateOf(status?.ledMode?.let { it != LedMode.OFF } ?: true)
    }
    var selectedMode by remember(status) {
        mutableIntStateOf(status?.ledMode?.takeIf { it != LedMode.OFF } ?: LedMode.AMBIENT)
    }
    var selectedPreset by remember(config) { mutableIntStateOf(config.ambientPreset.coerceIn(0, 15)) }
    var brightness by remember(config) { mutableFloatStateOf(config.brightness.toFloat()) }
    var speed by remember(config) { mutableFloatStateOf(config.ambientSpeed.coerceIn(1, 10).toFloat()) }
    var intensity by remember(config) { mutableFloatStateOf(config.ambientIntensity.coerceIn(1, 100).toFloat()) }
    var healthFloor by remember(config) {
        mutableFloatStateOf(config.ambientHealthIntensity.coerceIn(1, 100).toFloat())
    }
    var pickedColor by remember {
        mutableStateOf(Color(1f, 0.5f, 0f))
    }

    fun busy(control: SendingControl) = control in sendingControls

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        status?.let { StatusCard(it) }
        claimKey?.let { DeviceKeyCard(it) }

        Spacer(Modifier.height(8.dp))

        ControlSection(title = "Power") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isOn) "LEDs On" else "LEDs Off",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = isOn,
                    onCheckedChange = { on ->
                        isOn = on
                        if (on) viewModel.turnOn(selectedMode) else viewModel.turnOff()
                    },
                    enabled = !busy(SendingControl.Power)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        ControlSection(title = "Mode") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Ambient" to LedMode.AMBIENT, "Video" to LedMode.VIDEO).forEach { (label, mode) ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = {
                            selectedMode = mode
                            isOn = true
                            viewModel.setMode(mode)
                        },
                        label = { Text(label) },
                        enabled = !busy(SendingControl.Mode)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        ControlSection(title = "Brightness") {
            SliderWithValue(
                value = brightness,
                onValueChange = { brightness = it },
                onValueChangeFinished = { viewModel.setBrightness(brightness.toInt()) },
                valueRange = 0f..255f,
                enabled = !busy(SendingControl.Brightness),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        ControlSection(title = "Solid Color") {
            SimpleColorPicker(
                initialColor = pickedColor,
                onColorSelected = { color ->
                    pickedColor = color
                    viewModel.setColor(color)
                },
                enabled = !busy(SendingControl.Color),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        CollapsibleSection(
            title = "Health Scenes",
            subtitle = "Evidence-based light modes. Open only if you want the experimental presets.",
            defaultExpanded = true
        ) {
            PresetGrid(
                presets = HEALTH_SCENES,
                startIndex = HEALTH_PRESET_START,
                selectedPreset = selectedPreset,
                enabled = !busy(SendingControl.Preset),
                onPresetSelected = { idx ->
                    selectedPreset = idx
                    selectedMode = LedMode.AMBIENT
                    isOn = true
                    viewModel.setPreset(idx)
                }
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Health Pulse Floor (1–100)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Controls how dark health presets (12–15) dip — lower = brighter floor, higher = reaches black",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            SliderWithValue(
                value = healthFloor,
                onValueChange = { healthFloor = it },
                onValueChangeFinished = { viewModel.setHealthPulseFloor(healthFloor.toInt()) },
                valueRange = 1f..100f,
                enabled = !busy(SendingControl.HealthFloor)
            )

            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))

        CollapsibleSection(
            title = "Ambient Scenes",
            subtitle = "Choose a themed preset. Solid is static color only.",
            defaultExpanded = true
        ) {
            PresetGrid(
                presets = AMBIENT_SCENES,
                startIndex = 0,
                selectedPreset = selectedPreset,
                enabled = !busy(SendingControl.Preset),
                onPresetSelected = { idx ->
                    selectedPreset = idx
                    selectedMode = LedMode.AMBIENT
                    isOn = true
                    viewModel.setPreset(idx)
                }
            )

            Spacer(Modifier.height(12.dp))

            SliderRow(
                label = "Speed (1–10)",
                value = speed,
                onValueChange = { speed = it },
                onValueChangeFinished = { viewModel.setAmbientSpeed(speed.toInt()) },
                valueRange = 1f..10f,
                steps = 8,
                enabled = !busy(SendingControl.Speed)
            )

            Spacer(Modifier.height(4.dp))

            SliderRow(
                label = "Intensity (1–100)",
                value = intensity,
                onValueChange = { intensity = it },
                onValueChangeFinished = { viewModel.setAmbientIntensity(intensity.toInt()) },
                valueRange = 1f..100f,
                enabled = !busy(SendingControl.Intensity)
            )

            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(80.dp))
    }
}

/** Shows the pairing key, which the browser UI needs for protected actions. */
@Composable
private fun DeviceKeyCard(claimKey: String) {
    val clipboard = LocalClipboardManager.current
    var visible by remember(claimKey) { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Device key", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Needed for reboot, WPS and firmware updates in the browser.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (visible) claimKey else "\u2022".repeat(16),
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Hide" else "Show")
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(claimKey)) }) {
                    Text("Copy")
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusMetric("WiFi", if (status.wifiSignal != 0) "${status.wifiSignal} dBm" else "—")
                StatusMetric("LED FPS", status.fpsLedOut.toString())
                StatusMetric("Net FPS", status.fpsNetIn.toString())
                StatusMetric("Heap", formatHeap(status.freeHeap))
            }
            if (status.wifiName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = status.wifiName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    presets.chunked(2).forEachIndexed { chunkIdx, row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEachIndexed { rowIdx, preset ->
                val presetIndex = startIndex + chunkIdx * 2 + rowIdx
                PresetButton(
                    label = preset.label,
                    gradientColors = preset.gradient,
                    isSelected = selectedPreset == presetIndex,
                    onClick = { onPresetSelected(presetIndex) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
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
        modifier = modifier,
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
                    .height(7.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(
                        if (gradientColors.size > 1)
                            Brush.horizontalGradient(gradientColors)
                        else
                            SolidColor(gradientColors.first())
                    )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SliderWithValue(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f),
            enabled = enabled
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${value.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium
    )
    SliderWithValue(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled
    )
}

@Composable
private fun ControlSection(
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
    var expanded by remember { mutableStateOf(defaultExpanded) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
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
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val selectedColor = Color.hsv(hue, saturation, value)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .background(
                    brush = Brush.horizontalGradient(
                        colors = (0..12).map { i -> Color.hsv(i * 30f, 1f, 1f) }
                    )
                )
                .pointerInput(enabled, barWidthPx) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        hue = ((offset.x / barWidthPx) * 360f).coerceIn(0f, 360f)
                        onColorSelected(Color.hsv(hue, saturation, value))
                    }
                }
        )
        Spacer(Modifier.height(8.dp))
        Text("Hue", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = hue,
            onValueChange = { hue = it },
            onValueChangeFinished = { onColorSelected(Color.hsv(hue, saturation, value)) },
            valueRange = 0f..360f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Saturation", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            onValueChangeFinished = { onColorSelected(Color.hsv(hue, saturation, value)) },
            valueRange = 0f..1f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Brightness", style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { onColorSelected(Color.hsv(hue, saturation, value)) },
                valueRange = 0f..1f,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(selectedColor)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            )
        }
    }
}
