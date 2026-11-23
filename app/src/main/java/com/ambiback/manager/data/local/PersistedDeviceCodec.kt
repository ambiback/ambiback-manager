package com.ambiback.manager.data.local

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Pure codec for persisted device entries (JSON + legacy pipe format).
 * Kept free of Android / DataStore dependencies so unit tests can cover it directly.
 */
object PersistedDeviceCodec {
    private val gson = Gson()

    fun toJson(device: PersistedDevice): String = gson.toJson(device)

    fun parseEntry(entry: String): PersistedDevice? {
        if (entry.isBlank()) return null
        if (entry.trimStart().startsWith("{")) {
            val parsed = try {
                gson.fromJson(entry, PersistedDevice::class.java)
            } catch (_: JsonSyntaxException) {
                null
            } ?: return null
            // Gson skips Kotlin defaults, so fields missing from older entries arrive as null.
            val id: String? = parsed.deviceId
            val ip: String? = parsed.deviceIp
            val name: String? = parsed.deviceName
            val mac: String? = parsed.deviceMac
            if (id.isNullOrBlank() || ip.isNullOrBlank()) return null
            return PersistedDevice(id, ip, name.orEmpty(), mac.orEmpty())
        }
        val parts = entry.split("|")
        return if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            PersistedDevice(
                deviceId = parts[0],
                deviceIp = parts[1],
                deviceName = parts.getOrElse(2) { "" }
            )
        } else {
            null
        }
    }

    /**
     * Adds or replaces [device]. An entry without a MAC and with the same DeviceID is replaced
     * too, which upgrades entries saved by older app versions. A MAC-less [device] is ignored
     * when the same DeviceID and IP are already stored with a MAC.
     */
    fun upsert(entries: Set<String>, device: PersistedDevice): Set<String> {
        val alreadyUpgraded = device.deviceMac.isBlank() && entries.any { entry ->
            val existing = parseEntry(entry) ?: return@any false
            existing.deviceMac.isNotBlank() &&
                existing.deviceId == device.deviceId &&
                existing.deviceIp == device.deviceIp
        }
        if (alreadyUpgraded) return entries

        val retained = entries.filterNot { entry ->
            val existing = parseEntry(entry) ?: return@filterNot false
            existing.deviceKey == device.deviceKey ||
                (existing.deviceMac.isBlank() && existing.deviceId == device.deviceId)
        }
        return (retained + toJson(device)).toSet()
    }

    /** Removes only the entry identified by [deviceKey]. */
    fun remove(entries: Set<String>, deviceKey: String): Set<String> =
        entries.filterNot { parseEntry(it)?.deviceKey == deviceKey }.toSet()
}
