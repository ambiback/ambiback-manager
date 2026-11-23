package com.ambiback.manager.utils

import java.security.SecureRandom

/** Creates and checks the 128-bit device claim keys used by the firmware. */
object ClaimKeys {
    private const val KEY_BYTES = 16
    private val FORMAT = Regex("^[0-9a-f]{32}$")
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(KEY_BYTES)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isValid(key: String?): Boolean = key != null && FORMAT.matches(key)
}
