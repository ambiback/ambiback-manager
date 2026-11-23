package com.ambiback.manager.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpValidationTest {

    @Test
    fun acceptsValidIpv4() {
        assertTrue(IpValidation.isValidIp("192.168.1.1"))
        assertTrue(IpValidation.isValidIp("10.0.0.1"))
        assertTrue(IpValidation.isValidIp(" 127.0.0.1 "))
    }

    @Test
    fun rejectsInvalid() {
        assertFalse(IpValidation.isValidIp(""))
        assertFalse(IpValidation.isValidIp("not-an-ip"))
        assertFalse(IpValidation.isValidIp("192.168.1.256"))
        assertFalse(IpValidation.isValidIp("http://192.168.1.1"))
        assertFalse(IpValidation.isValidIp("::1"))
        assertFalse(IpValidation.isValidIp("2001:db8::1"))
    }
}
