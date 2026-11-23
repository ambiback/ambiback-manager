package com.ambiback.manager.data.network

import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.nio.ByteOrder

/**
 * Resolves the WiFi gateway IP after the phone has associated with the controller SoftAP.
 *
 * Prefers the Android-reported DHCP gateway address over the hard-coded default,
 * in case the controller firmware is configured with a different AP subnet.
 *
 * Usage:
 * ```kotlin
 * val ip = GatewayResolver.resolve(context) // e.g. "192.168.4.1"
 * ```
 */
object GatewayResolver {

    private const val FALLBACK_IP = "192.168.4.1"

    /**
     * Returns the current WiFi gateway IP as a dotted-decimal string.
     * Falls back to [FALLBACK_IP] if the gateway is unavailable (0) or
     * WifiManager is not accessible.
     */
    fun resolve(context: Context): String {
        return try {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return FALLBACK_IP

            val gateway = wifiManager.dhcpInfo?.gateway ?: 0
            if (gateway == 0) return FALLBACK_IP

            // DHCP gateway is stored as little-endian int on some Android versions.
            val gatewayBytes = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                byteArrayOf(
                    (gateway and 0xFF).toByte(),
                    (gateway shr 8 and 0xFF).toByte(),
                    (gateway shr 16 and 0xFF).toByte(),
                    (gateway shr 24 and 0xFF).toByte()
                )
            } else {
                byteArrayOf(
                    (gateway shr 24 and 0xFF).toByte(),
                    (gateway shr 16 and 0xFF).toByte(),
                    (gateway shr 8 and 0xFF).toByte(),
                    (gateway and 0xFF).toByte()
                )
            }

            InetAddress.getByAddress(gatewayBytes).hostAddress ?: FALLBACK_IP
        } catch (_: Exception) {
            FALLBACK_IP
        }
    }
}
