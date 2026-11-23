package com.ambiback.manager.data.usb

import java.io.ByteArrayOutputStream

/**
 * SLIP framing (RFC 1055) used by the ESP32 ROM bootloader protocol.
 * Pure Kotlin — unit-testable without Android.
 */
object SlipFraming {
    val END: Byte = 0xC0.toByte()
    val ESC: Byte = 0xDB.toByte()
    val ESC_END: Byte = 0xDC.toByte()
    val ESC_ESC: Byte = 0xDD.toByte()

    fun encode(data: ByteArray): ByteArray {
        val out = ArrayList<Byte>(data.size + 8)
        out.add(END)
        for (b in data) when (b) {
            END -> { out.add(ESC); out.add(ESC_END) }
            ESC -> { out.add(ESC); out.add(ESC_ESC) }
            else -> out.add(b)
        }
        out.add(END)
        return out.toByteArray()
    }

    fun decode(raw: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        val start = if (raw.isNotEmpty() && raw[0] == END) 1 else 0
        val end = if (raw.size > 1 && raw.last() == END) raw.size - 1 else raw.size
        while (i < end - start) {
            val b = raw[start + i]
            if (b == ESC && i + 1 < end - start) {
                when (raw[start + i + 1]) {
                    ESC_END -> { out.write(END.toInt() and 0xFF); i += 2 }
                    ESC_ESC -> { out.write(ESC.toInt() and 0xFF); i += 2 }
                    else -> { out.write(b.toInt() and 0xFF); i++ }
                }
            } else {
                out.write(b.toInt() and 0xFF)
                i++
            }
        }
        return out.toByteArray()
    }
}
