package com.ambiback.manager.data.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SlipFramingTest {

    @Test
    fun roundTrip_plainPayload() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x7F)
        assertArrayEquals(payload, SlipFraming.decode(SlipFraming.encode(payload)))
    }

    @Test
    fun roundTrip_escapesSpecialBytes() {
        val payload = byteArrayOf(SlipFraming.END, 0x11, SlipFraming.ESC, 0x22)
        assertArrayEquals(payload, SlipFraming.decode(SlipFraming.encode(payload)))
    }
}
