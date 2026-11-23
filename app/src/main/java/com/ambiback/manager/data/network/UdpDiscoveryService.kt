package com.ambiback.manager.data.network

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.ambiback.manager.data.model.Device
import com.google.gson.Gson
import com.google.gson.JsonParseException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

private const val UDP_PORT = 1337
private const val DISCOVERY_BROADCAST = "HELLO AMBIBACK DEVICES"
private const val DISCOVERY_TIMEOUT_MS = 4000L
private const val SOCKET_RECEIVE_TIMEOUT_MS = 300

/**
 * Mirrors the old UdpServerThread.java discovery logic as a cold Kotlin Flow.
 *
 * Key points that match the old implementation:
 *  1. The RECEIVE socket is bound to port 1337 on 0.0.0.0 — ESP32 controllers
 *     broadcast their response to port 1337 on the subnet, not back to the
 *     sender's random ephemeral port.
 *  2. The SEND socket uses a separate random-port socket so the receive socket
 *     binding is free when sending.
 *  3. The broadcast destination is the subnet-directed broadcast address
 *     (e.g. 192.168.0.255) derived from DhcpInfo, falling back to 255.255.255.255.
 *  4. A MulticastLock is held for the duration so Android delivers all broadcast
 *     UDP packets to the socket.
 */
@Singleton
class UdpDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val TAG = "AmbiBackUDP"

    fun discover(): Flow<Device> = flow {
        Log.d(TAG, "discover() started")
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val multicastLock = wifiManager.createMulticastLock("ambiback_discovery")
        multicastLock.setReferenceCounted(true)

        var receiveSocket: DatagramSocket? = null
        try {
            // Bind first; only acquire the MulticastLock once the socket is ready
            // so a BindException cannot leak the lock.
            receiveSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(UDP_PORT))
                broadcast = true
                soTimeout = SOCKET_RECEIVE_TIMEOUT_MS
            }
            multicastLock.acquire()
            Log.d(TAG, "Receive socket bound to :$UDP_PORT")

            val broadcastAddress = getSubnetBroadcast(wifiManager)
            Log.d(TAG, "Broadcasting HELLO to $broadcastAddress:$UDP_PORT")

            DatagramSocket().use { sendSocket ->
                sendSocket.broadcast = true
                val sendData = DISCOVERY_BROADCAST.toByteArray(Charsets.UTF_8)
                sendSocket.send(
                    DatagramPacket(sendData, sendData.size, broadcastAddress, UDP_PORT)
                )
            }
            Log.d(TAG, "HELLO sent, listening for ${DISCOVERY_TIMEOUT_MS}ms…")

            val receiveBuffer = ByteArray(4096)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            val seen = mutableSetOf<String>()

            withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
                while (true) {
                    // Reset length so truncated packets from a previous receive
                    // do not permanently shrink the buffer capacity.
                    receivePacket.setLength(receiveBuffer.size)
                    try {
                        receiveSocket.receive(receivePacket)
                        val json = String(
                            receivePacket.data, 0, receivePacket.length, Charsets.UTF_8
                        ).trim()
                        if (!json.startsWith("{")) continue
                        val device = gson.fromJson(json, Device::class.java)
                        if (device?.deviceId != null && seen.add(device.deviceId)) {
                            Log.i(TAG, "Found device: ${device.deviceName} @ ${device.deviceIp}")
                            emit(device)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Brief receive timeout — keep looping until discovery window ends
                    } catch (_: JsonParseException) {
                        // Malformed packet — ignore
                    }
                }
            }
        } finally {
            receiveSocket?.close()
            if (multicastLock.isHeld) multicastLock.release()
        }
    }.flowOn(Dispatchers.IO)

    /** Returns the subnet-directed broadcast address (e.g. 192.168.0.255), or 255.255.255.255. */
    private fun getSubnetBroadcast(wifiManager: WifiManager): InetAddress {
        return try {
            val dhcp: DhcpInfo = wifiManager.dhcpInfo
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val quads = ByteArray(4) { k -> (broadcast shr (k * 8)).toByte() }
            InetAddress.getByAddress(quads)
        } catch (_: Exception) {
            InetAddress.getByName("255.255.255.255")
        }
    }
}
