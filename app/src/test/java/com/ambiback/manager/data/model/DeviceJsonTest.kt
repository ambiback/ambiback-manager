package com.ambiback.manager.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses JSON shaped like the firmware's replies, with the same Gson the app uses. */
class DeviceJsonTest {

    private val gson = Gson()

    @Test
    fun statusUsesFirmwareFieldNames() {
        val json = """
            {
            	"devicename": "TV",
            	"deviceip": "192.168.0.9",
            	"videostatus": "disconnected",
            	"modestatus": "Ambient",
            	"wifinetwork": "Home",
            	"wifirssi": "-61",
            	"ledfps": "50",
            	"netfps": "0",
            	"freeheap": "123456"
            }
        """.trimIndent()
        val status = gson.fromJson(json, DeviceStatus::class.java)
        assertEquals(LedMode.AMBIENT, status.ledMode)
        assertEquals("Home", status.wifiName)
        assertEquals(-61, status.wifiSignal)
        assertEquals(50, status.fpsLedOut)
        assertEquals(123456, status.freeHeap)
    }

    @Test
    fun modeNamesMapToFirmwareValues() {
        assertEquals(0, LedMode.fromName("Off"))
        assertEquals(1, LedMode.fromName("Video"))
        assertEquals(2, LedMode.fromName("Ambient"))
        assertEquals(3, LedMode.fromName("Cam"))
        assertEquals(4, LedMode.fromName("Relay"))
        assertNull(LedMode.fromName("[UNKNOWN]"))
    }

    @Test
    fun configReadsModeAndDetectsCamera() {
        val plain = gson.fromJson("""{"deviceid":"id1","ledmode":"1"}""", DeviceConfig::class.java)
        assertEquals(LedMode.VIDEO, plain.ledMode)
        assertFalse(plain.hasCamera)

        val cam = gson.fromJson("""{"deviceid":"id2","ledmode":"3","cammode":"2"}""", DeviceConfig::class.java)
        assertTrue(cam.hasCamera)
    }
}
