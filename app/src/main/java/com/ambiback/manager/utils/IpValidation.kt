package com.ambiback.manager.utils

/**
 * Validates that [value] is a numeric IPv4 address (not a hostname or IPv6).
 * Controllers are addressed over LAN HTTP/WebView URLs that do not bracket IPv6 hosts,
 * so only IPv4 is accepted before add-by-IP / WebView injection.
 */
object IpValidation {

    private val IPV4_REGEX = Regex(
        """^(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)$"""
    )

    fun isValidIp(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isNotEmpty() && IPV4_REGEX.matches(trimmed)
    }
}
