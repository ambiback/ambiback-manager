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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
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
private val replyGson = Gson()

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

            receiveReplies(receiveSocket, DISCOVERY_TIMEOUT_MS) { device ->
                Log.i(TAG, "Found device: ${device.deviceName} @ ${device.deviceIp}")
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

/**
 * Emits each distinct controller that answers within [windowMs].
 * The socket needs a short soTimeout: receive() blocks without a suspension point, so the
 * explicit [ensureActive] check is what lets the timeout or a cancellation end the loop.
 */
internal suspend fun FlowCollector<Device>.receiveReplies(
    socket: DatagramSocket,
    windowMs: Long,
    onFound: (Device) -> Unit = {}
) {
    val receiveBuffer = ByteArray(4096)
    val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
    val seen = mutableSetOf<String>()

    withTimeoutOrNull(windowMs) {
        while (true) {
            ensureActive()
            // Reset length so truncated packets from a previous receive
            // do not permanently shrink the buffer capacity.
            receivePacket.setLength(receiveBuffer.size)
            try {
                socket.receive(receivePacket)
            } catch (_: SocketTimeoutException) {
                continue
            }
            val json = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8).trim()
            val device = parseDiscoveryReply(json) ?: continue
            if (seen.add(device.deviceKey)) {
                onFound(device)
                emit(device)
            }
        }
    }
}

/** Parses a controller reply, or returns null when it is not a usable device announcement. */
internal fun parseDiscoveryReply(json: String): Device? {
    if (!json.startsWith("{")) return null
    val raw = try {
        replyGson.fromJson(json, Device::class.java)
    } catch (_: JsonParseException) {
        null
    } ?: return null
    // Gson can leave non-null Kotlin fields null when a key is missing.
    val id: String? = raw.deviceId
    val ip: String? = raw.deviceIp
    val mac: String? = raw.deviceMac
    val name: String? = raw.deviceName
    val type: String? = raw.deviceType
    val version: String? = raw.deviceVersion
    if (id.isNullOrBlank() || ip.isNullOrBlank()) return null
    return Device(id, mac.orEmpty(), name.orEmpty(), ip, type.orEmpty(), version.orEmpty(), raw.ledStatus)
}
