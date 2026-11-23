package com.ambiback.manager.data.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UsbCommandProtocolTest {

    @Test
    fun parseInfoResponse_extractsJsonPayload() {
        val raw = """%info: {"DeviceType":"AmbiBack","DeviceVersion":"1.2.3","DeviceID":"ABC","DeviceMAC":"AABBCCDDEEFF","DeviceName":"Living","DeviceIP":"192.168.1.50"}"""
        val info = UsbCommandProtocol.parseInfoResponse(raw)
        assertNotNull(info)
        assertEquals("AmbiBack", info!!.deviceType)
        assertEquals("1.2.3", info.deviceVersion)
        assertEquals("ABC", info.deviceId)
        assertEquals("Living", info.deviceName)
        assertEquals("192.168.1.50", info.deviceIp)
    }

    @Test
    fun parseInfoResponse_returnsNullWithoutJson() {
        assertNull(UsbCommandProtocol.parseInfoResponse("ACK"))
        assertNull(UsbCommandProtocol.parseInfoResponse(""))
    }

    @Test
    fun buildSetWifiCommand_nullDelimited() {
        val cmd = UsbCommandProtocol.buildSetWifiCommand("HomeNet", "secret")
        val text = String(cmd, Charsets.UTF_8)
        assertEquals("%WIFI:HomeNet\u0000secret\u0000", text)
    }
}
