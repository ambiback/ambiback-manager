package com.ambiback.manager.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [PersistedDeviceCodec] (shared with production DataStore). */
class PersistedDeviceParseTest {

    @Test
    fun parsesJsonEntry() {
        val json = PersistedDeviceCodec.toJson(PersistedDevice("id1", "10.0.0.5", "Kitchen|TV"))
        val parsed = PersistedDeviceCodec.parseEntry(json)
        assertNotNull(parsed)
        assertEquals("id1", parsed!!.deviceId)
        assertEquals("10.0.0.5", parsed.deviceIp)
        assertEquals("Kitchen|TV", parsed.deviceName)
    }

    @Test
    fun parsesLegacyPipeEntry() {
        val parsed = PersistedDeviceCodec.parseEntry("id2|192.168.0.2|Living Room")
        assertNotNull(parsed)
        assertEquals("id2", parsed!!.deviceId)
        assertEquals("192.168.0.2", parsed.deviceIp)
        assertEquals("Living Room", parsed.deviceName)
    }

    @Test
    fun rejectsBlank() {
        assertNull(PersistedDeviceCodec.parseEntry(""))
        assertNull(PersistedDeviceCodec.parseEntry("|"))
    }
}
