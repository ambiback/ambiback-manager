package com.ambiback.manager.ui.device

import org.junit.Assert.assertEquals
import org.junit.Test

class RewriteDeviceParamTest {

    @Test
    fun rewritesQueryParam() {
        val url = "https://example.com/app?device=0.0.0.0&foo=1"
        assertEquals(
            "https://example.com/app?device=192.168.1.10&foo=1",
            rewriteDeviceParam(url, "192.168.1.10")
        )
    }

    @Test
    fun leavesUrlWithoutDeviceParamUnchanged() {
        val url = "https://example.com/app?foo=1"
        assertEquals(url, rewriteDeviceParam(url, "192.168.1.10"))
    }

    @Test
    fun rewritesAmpersandForm() {
        val url = "https://example.com/app?x=1&device=bad#frag"
        assertEquals(
            "https://example.com/app?x=1&device=10.0.0.2#frag",
            rewriteDeviceParam(url, "10.0.0.2")
        )
    }
}
