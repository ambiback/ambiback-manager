package com.ambiback.manager.data.network

import com.ambiback.manager.data.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class UdpDiscoveryReceiveTest {

    private fun reply(mac: String, id: String = "0XFF00shared") = """
        {
        	"DeviceType": "AmbiBack",
        	"DeviceVersion": "0.5.231",
        	"DeviceID": "$id",
        	"DeviceMAC": "$mac",
        	"DeviceName": "Test",
        	"DeviceIP": "192.168.0.9",
        	"LedStatus": "2",
        	"Finished": "1"
        }
    """.trimIndent() + "\n"

    private fun collect(socket: DatagramSocket, windowMs: Long): CompletableFuture<List<Device>> =
        CompletableFuture.supplyAsync {
            runBlocking {
                flow { receiveReplies(socket, windowMs) }.flowOn(Dispatchers.IO).toList()
            }
        }

    @Test
    fun scanEndsWhenNoControllerAnswers() {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { socket ->
            socket.soTimeout = 100
            val devices = collect(socket, 500).get(5, TimeUnit.SECONDS)
            assertTrue(devices.isEmpty())
        }
    }

    @Test
    fun controllersSharingDeviceIdAreBothFoundAndRepliesDeduplicated() {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { socket ->
            socket.soTimeout = 100
            val result = collect(socket, 800)
            DatagramSocket().use { sender ->
                listOf("AABBCC000001", "AABBCC000001", "AABBCC000002").forEach { mac ->
                    val data = reply(mac).toByteArray()
                    sender.send(DatagramPacket(data, data.size, socket.localAddress, socket.localPort))
                }
            }
            val devices = result.get(5, TimeUnit.SECONDS)
            assertEquals(setOf("AABBCC000001", "AABBCC000002"), devices.map { it.deviceMac }.toSet())
            assertEquals(2, devices.size)
        }
    }

    @Test
    fun parsesFirmwareReply() {
        val device = parseDiscoveryReply(reply("AABBCC000001").trim())
        assertNotNull(device)
        assertEquals(2, device!!.ledStatus)
    }

    @Test
    fun ignoresUnusableReplies() {
        assertNull(parseDiscoveryReply("HELLO AMBIBACK DEVICES"))
        assertNull(parseDiscoveryReply("{broken"))
        assertNull(parseDiscoveryReply("""{"DeviceName":"No ID"}"""))
    }

    @Test
    fun replyWithoutOptionalFieldsDoesNotLeaveNulls() {
        val device = parseDiscoveryReply("""{"DeviceID":"id1","DeviceIP":"10.0.0.5"}""")
        assertEquals("", device!!.deviceMac)
        assertEquals("id:id1", device.deviceKey)
    }
}
