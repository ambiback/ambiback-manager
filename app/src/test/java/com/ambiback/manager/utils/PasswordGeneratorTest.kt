package com.ambiback.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun generateApPassword_knownMac_matchesFirmware() {
        val mac = "A1B2C3D4E5F6"
        val expected = "002522852025"
        assertEquals(expected, PasswordGenerator.generateApPassword(mac, 0x0000FFFFL))
    }

    @Test
    fun generateApPassword_customSeed_matchesCrc32() {
        val mac = "A1B2C3D4E5F6"
        val expected = "003944669358"
        assertEquals(expected, PasswordGenerator.generateApPassword(mac, 0x12345678L))
    }
}
