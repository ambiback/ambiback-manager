package com.ambiback.manager.ui.device

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ambiback.manager.R

private const val TAB_QUICK = 0
private const val TAB_FULL = 1
private const val TAB_WEB = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_QUICK) }
    var fullReload by remember { mutableIntStateOf(0) }
    var webReload by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_quick_control),
        stringResource(R.string.tab_full_control),
        stringResource(R.string.tab_web_interface)
    )

    DisposableEffect(selectedTab) {
        if (selectedTab == TAB_QUICK) {
            viewModel.startStatusPolling()
        } else {
            viewModel.stopStatusPolling()
        }
        onDispose { viewModel.stopStatusPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.deviceName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = viewModel.deviceIp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            when (selectedTab) {
                                TAB_QUICK -> viewModel.refresh()
                                TAB_FULL -> fullReload++
                                else -> webReload++
                            }
                        },
                        enabled = !(selectedTab == TAB_QUICK && uiState.isRefreshing)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("http://${viewModel.deviceIp}/"))
                            )
                        } catch (_: ActivityNotFoundException) {
                            // No browser installed; nothing sensible to do.
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.open_in_browser)
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            // Keep all tabs composed so WebViews survive tab switches. Clip, or a WebView
            // can draw over the tab row.
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                TabPane(visible = selectedTab == TAB_QUICK) {
                    QuickControlContent(uiState = uiState, viewModel = viewModel)
                }
                TabPane(visible = selectedTab == TAB_FULL) {
                    FullControlWebView(
                        deviceIp = viewModel.deviceIp,
                        claimKey = uiState.claimKey,
                        active = selectedTab == TAB_FULL,
                        reloadSignal = fullReload
                    )
                }
                TabPane(visible = selectedTab == TAB_WEB) {
                    WebViewContent(
                        deviceIp = viewModel.deviceIp,
                        active = selectedTab == TAB_WEB,
                        reloadSignal = webReload
                    )
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
            // Hidden panes are invisible, take no taps and are skipped by screen readers.
            .then(
                if (visible) Modifier
                else Modifier
                    .alpha(0f)
                    .clearAndSetSemantics { }
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent() } } }
            )
    ) {
        content()
    }
}
