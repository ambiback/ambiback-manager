package com.ambiback.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ambiback.manager.data.usb.UsbSerialManager
import com.ambiback.manager.ui.navigation.AppNavigation
import com.ambiback.manager.ui.settings.SettingsViewModel
import com.ambiback.manager.ui.theme.AmbiBackManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host for the entire Compose UI tree.
 * Hilt injection is enabled via @AndroidEntryPoint.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var usbSerialManager: UsbSerialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            AmbiBackManagerTheme(
                themeMode = settingsState.themeMode,
                dynamicColor = settingsState.useDynamicColor
            ) {
                AppNavigation(usbSerialManager = usbSerialManager)
            }
        }
    }
}
