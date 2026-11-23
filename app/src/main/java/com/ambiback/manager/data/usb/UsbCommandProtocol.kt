package com.ambiback.manager.data.usb

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Parsed response from the %INFO: serial command.
 * Field names match the JSON keys sent by the AmbiBack firmware.
 */
data class UsbDeviceInfo(
    @SerializedName("DeviceType")    val deviceType: String    = "",
    @SerializedName("DeviceVersion") val deviceVersion: String = "",
    @SerializedName("DeviceID")      val deviceId: String      = "",
    @SerializedName("DeviceMAC")     val deviceMac: String     = "",
    @SerializedName("DeviceName")    val deviceName: String    = "",
    @SerializedName("DeviceIP")      val deviceIp: String      = ""
)

/**
 * Builds raw byte arrays for each serial command, and parses the %INFO: response.
 * Pure Kotlin — no Android dependencies — making it straightforward to unit-test.
 *
 * Protocol (from AmbiBack.USBFlasher reference implementation):
 *   %INFO:              → JSON response  (ASCII, no terminator)
 *   %RESETWIFI\0        → resets WiFi credentials (ASCII + null)
 *   %WIFI:<ssid>\0<pw>\0 → sets WiFi   (binary, null-delimited fields)
 *   %FACTORYDEFAULT:    → factory reset (ASCII, colon terminator)
 */
object UsbCommandProtocol {

    fun buildInfoCommand(): ByteArray =
        "%INFO:".toByteArray(Charsets.UTF_8)

    fun buildResetWifiCommand(): ByteArray =
        "%RESETWIFI\u0000".toByteArray(Charsets.UTF_8)

    fun buildSetWifiCommand(ssid: String, password: String): ByteArray =
        "%WIFI:$ssid\u0000$password\u0000".toByteArray(Charsets.UTF_8)

    fun buildFactoryResetCommand(): ByteArray =
        "%FACTORYDEFAULT:".toByteArray(Charsets.UTF_8)

    /**
     * Parses a raw serial response from the device into a [UsbDeviceInfo].
     * The firmware sends: "%info: {\"DeviceType\":\"...\", ...}"
     * Returns null if the response does not contain recognisable JSON.
     */
    fun parseInfoResponse(raw: String): UsbDeviceInfo? {
        val start = raw.indexOf('{')
        val end   = raw.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return try {
            Gson().fromJson(raw.substring(start, end + 1), UsbDeviceInfo::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
