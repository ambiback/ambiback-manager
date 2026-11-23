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
            return try {
                gson.fromJson(entry, PersistedDevice::class.java)
                    ?.takeIf { it.deviceId.isNotBlank() && it.deviceIp.isNotBlank() }
            } catch (_: JsonSyntaxException) {
                null
            }
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
}
