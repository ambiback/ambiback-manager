package com.ambiback.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimKeysTest {

    @Test
    fun generate_createsValidKey() {
        val key = ClaimKeys.generate()
        assertEquals(32, key.length)
        assertTrue(ClaimKeys.isValid(key))
    }

    @Test
    fun generate_createsDifferentKeys() {
        assertEquals(50, (1..50).map { ClaimKeys.generate() }.toSet().size)
    }

    @Test
    fun isValid_rejectsWrongFormats() {
        assertFalse(ClaimKeys.isValid(null))
        assertFalse(ClaimKeys.isValid(""))
        assertFalse(ClaimKeys.isValid("abc"))
        assertFalse(ClaimKeys.isValid("g".repeat(32)))
        assertFalse(ClaimKeys.isValid("a".repeat(33)))
        assertFalse(ClaimKeys.isValid("';alert(1);//" + "a".repeat(19)))
    }
}
