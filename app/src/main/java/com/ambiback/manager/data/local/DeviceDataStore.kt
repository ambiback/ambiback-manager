package com.ambiback.manager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val deviceName: String = ""
)

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
    suspend fun saveDevice(deviceId: String, deviceIp: String, deviceName: String = "") {
        val json = PersistedDeviceCodec.toJson(PersistedDevice(deviceId, deviceIp, deviceName))
        context.dataStore.edit { prefs ->
            val current = prefs[KNOWN_DEVICES_KEY]?.toMutableSet() ?: mutableSetOf()
            // Remove any stale entry for the same ID (JSON or legacy pipe)
            current.removeAll { entry ->
                PersistedDeviceCodec.parseEntry(entry)?.deviceId == deviceId
            }
            current.add(json)
            prefs[KNOWN_DEVICES_KEY] = current
        }
    }

    /** Remove a device from the persistent store. */
    suspend fun removeDevice(deviceId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KNOWN_DEVICES_KEY]?.toMutableSet() ?: return@edit
            current.removeAll { entry ->
                PersistedDeviceCodec.parseEntry(entry)?.deviceId == deviceId
            }
            prefs[KNOWN_DEVICES_KEY] = current
        }
    }

    /** Clear every persisted device in a single atomic edit. */
    suspend fun clearAllDevices() {
        context.dataStore.edit { prefs ->
            prefs[KNOWN_DEVICES_KEY] = emptySet()
        }
    }
}
