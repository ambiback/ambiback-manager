package com.ambiback.manager.utils

import com.ambiback.manager.BuildConfig
import java.util.Locale

/**
 * Computes the Android WiFi provisioning password from the controller's MAC address.
 * Matches the ESP32 FIRMWARE exact implementation of `esp_crc32_le`.
 */
object PasswordGenerator {

    /**
     * Calculates an ESP32-compatible CRC32 password using the MAC address.
     * The firmware uses `esp_crc32_le(AMBIBACK_AP_PASSWORD_SEED, macBytes, 12)` which internally
     * applies `~crc` at the start and end.
     * 
     * @param mac The 12-character MAC address string (e.g. A1B2C3D4E5F6)
     * @param seed 32-bit seed, must match AMBIBACK_AP_PASSWORD_SEED of the firmware.
     * @return 12-digit unsigned decimal string.
     */
    fun generateApPassword(mac: String, seed: Long = BuildConfig.AP_PASSWORD_SEED): String {
        val bytes = mac.toByteArray(Charsets.US_ASCII)
        
        // esp_crc32_le immediately applies ~crc to the seed
        var crc = seed.inv() and 0xFFFFFFFFL
        val poly = 0xEDB88320L
        
        for (b in bytes) {
            crc = crc xor (b.toLong() and 0xFFL)
            for (i in 0 until 8) {
                crc = if ((crc and 1L) != 0L) {
                    (crc shr 1) xor poly
                } else {
                    crc shr 1
                }
            }
        }
        
        // Final ~crc step
        val finalCrc = crc.inv() and 0xFFFFFFFFL
        
        // Format as unsigned 12-digit string
        return String.format(Locale.US, "%012d", finalCrc)
    }
}
