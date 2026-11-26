package com.x8bit.bitwarden.data.platform.update

import java.security.MessageDigest

/**
 * `sha256sum` lines: `<hash><whitespace><filename>`, with an optional `*` before the name.
 */
fun parseSha256Sums(text: String): Map<String, String> {
    val sums = LinkedHashMap<String, String>()
    for (rawLine in text.lineSequence()) {
        val parts = rawLine.trim().split(Regex("\\s+"))
        val hash = parts.getOrNull(0)?.lowercase().orEmpty()
        val name = parts.getOrNull(1)?.removePrefix("*")
        val validHash = hash.length == SHA256_HEX_LENGTH &&
            hash.all { character -> character in '0'..'9' || character in 'a'..'f' }
        if (validHash && !name.isNullOrEmpty()) {
            sums[name] = hash
        }
    }
    return sums
}

/**
 * Lowercase hex SHA-256 of [bytes].
 */
fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private const val SHA256_HEX_LENGTH: Int = 64
