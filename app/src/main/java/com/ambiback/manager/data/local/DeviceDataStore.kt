package com.ambiback.manager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ambiback.manager.utils.DeviceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "devices")

/** JSON-serializable persisted device entry. */
data class PersistedDevice(
    val deviceId: String,
    val deviceIp: String,
    val deviceName: String = "",
    val deviceMac: String = ""
) {
    val deviceKey: String get() = DeviceKeys.of(deviceMac, deviceId)
}

/**
 * Persists and retrieves the set of known device identifiers between app sessions.
 * Entries are stored as JSON ([PersistedDevice]); legacy "id|ip|name" pipe entries
 * are migrated on read.
 */
@Singleton
class DeviceDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KNOWN_DEVICES_KEY = stringSetPreferencesKey("known_device_entries")

    /** Flow of parsed known devices (JSON + legacy pipe migration handled internally). */
    val knownDevices: Flow<List<PersistedDevice>> = context.dataStore.data.map { prefs ->
        (prefs[KNOWN_DEVICES_KEY] ?: emptySet()).mapNotNull { PersistedDeviceCodec.parseEntry(it) }
    }

    /** Upsert a device entry. Called whenever a device is discovered or verified reachable. */
    suspend fun saveDevice(device: PersistedDevice) {
        context.dataStore.edit { prefs ->
            prefs[KNOWN_DEVICES_KEY] =
                PersistedDeviceCodec.upsert(prefs[KNOWN_DEVICES_KEY] ?: emptySet(), device)
        }
    }

    /** Remove the device identified by [deviceKey] from the persistent store. */
    suspend fun removeDevice(deviceKey: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KNOWN_DEVICES_KEY] ?: return@edit
            prefs[KNOWN_DEVICES_KEY] = PersistedDeviceCodec.remove(current, deviceKey)
        }
    }

    /** Clear every persisted device in a single atomic edit. */
    suspend fun clearAllDevices() {
        context.dataStore.edit { prefs ->
            prefs[KNOWN_DEVICES_KEY] = emptySet()
        }
    }
}
