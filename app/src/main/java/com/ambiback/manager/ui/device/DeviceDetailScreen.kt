package com.ambiback.manager.ui.device

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Quick Control", "Full Control", "Web Interface")

    DisposableEffect(selectedTab) {
        if (selectedTab == 0) {
            viewModel.startStatusPolling()
        } else {
            viewModel.stopStatusPolling()
        }
        onDispose { viewModel.stopStatusPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.deviceName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Keep all tabs composed so WebViews survive tab switches.
            Box(modifier = Modifier.fillMaxSize()) {
                TabPane(visible = selectedTab == 0) {
                    QuickControlContent(uiState = uiState, viewModel = viewModel)
                }
                TabPane(visible = selectedTab == 1) {
                    FullControlWebView(deviceIp = viewModel.deviceIp, claimKey = uiState.claimKey)
                }
                TabPane(visible = selectedTab == 2) {
                    WebViewContent(deviceIp = viewModel.deviceIp)
                }
            }
        }
    }
}

@Composable
private fun TabPane(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .alpha(if (visible) 1f else 0f)
            // Swallow pointer events on hidden panes so they don't intercept taps.
            .then(
                if (visible) Modifier
                else Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent() } } }
            )
    ) {
        content()
    }
}
