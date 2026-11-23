package com.ambiback.manager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * Persists application-wide preferences: theme mode, scan-on-startup, HTTP timeout,
 * and whether to use Material You dynamic color.
 */
@Singleton
class AppSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val THEME_MODE        = stringPreferencesKey("theme_mode")
    private val SCAN_ON_STARTUP   = booleanPreferencesKey("scan_on_startup")
    private val HTTP_TIMEOUT_SEC  = intPreferencesKey("http_timeout_sec")
    private val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")

    /** "system" | "light" | "dark" */
    val themeMode: Flow<String> = context.appSettingsStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "system"
    }

    /** Whether to trigger a UDP scan automatically on app launch. */
    val scanOnStartup: Flow<Boolean> = context.appSettingsStore.data.map { prefs ->
        prefs[SCAN_ON_STARTUP] ?: true
    }

    /** Seconds to wait for device HTTP responses (1–30, default 5). */
    val httpTimeoutSec: Flow<Int> = context.appSettingsStore.data.map { prefs ->
        prefs[HTTP_TIMEOUT_SEC] ?: 5
    }

    /** When true, use Material You dynamic color on Android 12+. Default false = AmbiBack palette. */
    val useDynamicColor: Flow<Boolean> = context.appSettingsStore.data.map { prefs ->
        prefs[USE_DYNAMIC_COLOR] ?: false
    }

    suspend fun setThemeMode(mode: String) {
        context.appSettingsStore.edit { prefs -> prefs[THEME_MODE] = mode }
    }

    suspend fun setScanOnStartup(enabled: Boolean) {
        context.appSettingsStore.edit { prefs -> prefs[SCAN_ON_STARTUP] = enabled }
    }

    suspend fun setHttpTimeoutSec(seconds: Int) {
        context.appSettingsStore.edit { prefs -> prefs[HTTP_TIMEOUT_SEC] = seconds.coerceIn(1, 30) }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.appSettingsStore.edit { prefs -> prefs[USE_DYNAMIC_COLOR] = enabled }
    }
}
