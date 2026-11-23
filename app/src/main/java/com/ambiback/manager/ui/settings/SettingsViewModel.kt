package com.ambiback.manager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambiback.manager.data.local.AppSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "system",
    val scanOnStartup: Boolean = false,
    val httpTimeoutSec: Int = 5,
    val useDynamicColor: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: AppSettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsDataStore.themeMode,
        settingsDataStore.scanOnStartup,
        settingsDataStore.httpTimeoutSec,
        settingsDataStore.useDynamicColor
    ) { themeMode, scanOnStartup, httpTimeoutSec, useDynamicColor ->
        SettingsUiState(themeMode, scanOnStartup, httpTimeoutSec, useDynamicColor)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setScanOnStartup(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setScanOnStartup(enabled) }
    }

    fun setHttpTimeoutSec(seconds: Int) {
        viewModelScope.launch { settingsDataStore.setHttpTimeoutSec(seconds) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setUseDynamicColor(enabled) }
    }
}
