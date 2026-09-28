package com.quio.ytm.core.api

import java.security.MessageDigest
import java.time.Instant

object InnertubeAuth {
    private const val ORIGIN = "https://music.youtube.com"

    fun generateSapisidHashHeader(sapisid: String): String {
        val timestamp = Instant.now().epochSecond
        val input = "$timestamp $sapisid $ORIGIN"
        val hash = sha1(input)
        return "SAPISIDHASH ${timestamp}_$hash"
    }

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val result = digest.digest(input.toByteArray(Charsets.UTF_8))
        return result.joinToString("") { "%02x".format(it) }
    }
}
