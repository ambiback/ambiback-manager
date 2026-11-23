package com.ambiback.manager.utils

/**
 * Builds the key that identifies a controller inside the app.
 *
 * The firmware's DeviceID holds only four MAC bytes, three of which are the vendor prefix,
 * so controllers from one batch can share it. The full MAC is unique. Entries saved by
 * older app versions have no MAC and fall back to [idKey] until the controller answers a scan.
 */
object DeviceKeys {
    fun of(deviceMac: String, deviceId: String): String {
        val mac = deviceMac.filter { it.isLetterOrDigit() }.uppercase()
        return if (mac.isNotEmpty()) mac else idKey(deviceId)
    }

    fun idKey(deviceId: String): String = "id:$deviceId"
}
