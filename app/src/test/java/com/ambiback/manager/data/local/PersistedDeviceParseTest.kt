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

    @Test
    fun olderJsonEntryWithoutMacUsesIdKey() {
        val parsed = PersistedDeviceCodec.parseEntry(
            """{"deviceId":"id1","deviceIp":"10.0.0.5","deviceName":"TV"}"""
        )
        assertEquals("", parsed!!.deviceMac)
        assertEquals("id:id1", parsed.deviceKey)
    }

    @Test
    fun jsonEntryWithoutIdIsRejected() {
        assertNull(PersistedDeviceCodec.parseEntry("""{"deviceIp":"10.0.0.5"}"""))
    }

    @Test
    fun controllersSharingDeviceIdAreKeptSeparately() {
        val first = PersistedDevice("0XFF00shared", "10.0.0.5", "A", "AABBCC000001")
        val second = PersistedDevice("0XFF00shared", "10.0.0.6", "B", "AABBCC000002")
        val entries = PersistedDeviceCodec.upsert(PersistedDeviceCodec.upsert(emptySet(), first), second)
        assertEquals(2, entries.size)

        val remaining = PersistedDeviceCodec.remove(entries, first.deviceKey)
        assertEquals(listOf("B"), remaining.map { PersistedDeviceCodec.parseEntry(it)!!.deviceName })
    }

    @Test
    fun entryWithoutMacDoesNotDuplicateUpgradedController() {
        val upgraded = PersistedDevice("id1", "10.0.0.5", "TV", "AABBCC000001")
        val entries = PersistedDeviceCodec.upsert(emptySet(), upgraded)
        val afterUndo = PersistedDeviceCodec.upsert(entries, PersistedDevice("id1", "10.0.0.5", "TV"))
        assertEquals(entries, afterUndo)
    }

    @Test
    fun entryWithoutMacIsUpgradedWhenControllerAnswers() {
        val old = setOf("id1|10.0.0.5|TV")
        val upgraded = PersistedDevice("id1", "10.0.0.9", "TV", "AABBCC000001")
        val entries = PersistedDeviceCodec.upsert(old, upgraded)
        assertEquals(listOf(upgraded), entries.map { PersistedDeviceCodec.parseEntry(it) })
    }
}
