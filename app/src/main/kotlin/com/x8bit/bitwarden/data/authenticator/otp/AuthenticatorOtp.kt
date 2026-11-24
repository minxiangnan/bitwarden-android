@file:Suppress("MagicNumber", "TooManyFunctions")

package com.x8bit.bitwarden.data.authenticator.otp

import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorAlgorithm
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
private const val STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"

/**
 * Decodes a base32 secret. Returns null when the secret is empty or not base32.
 */
fun decodeBase32(secret: String): ByteArray? {
    val normalized = secret.replace("=", "").replace(Regex("\\s+"), "").uppercase()
    if (normalized.isEmpty() || normalized.any { it !in BASE32_ALPHABET }) {
        return null
    }
    var buffer = 0
    var bitsLeft = 0
    val bytes = ArrayList<Byte>()
    for (char in normalized) {
        val index = BASE32_ALPHABET.indexOf(char)
        if (index < 0) return null
        buffer = (buffer shl 5) or index
        bitsLeft += 5
        if (bitsLeft >= 8) {
            bitsLeft -= 8
            bytes.add(((buffer shr bitsLeft) and 0xff).toByte())
            buffer = buffer and ((1 shl bitsLeft) - 1)
        }
    }
    return bytes.toByteArray()
}

/**
 * Encodes bytes as unpadded base32.
 */
fun encodeBase32(bytes: ByteArray): String {
    var bits = 0
    var value = 0
    val output = StringBuilder()
    for (byte in bytes) {
        value = (value shl 8) or (byte.toInt() and 0xff)
        bits += 8
        while (bits >= 5) {
            bits -= 5
            output.append(BASE32_ALPHABET[(value ushr bits) and 31])
        }
    }
    if (bits > 0) {
        output.append(BASE32_ALPHABET[(value shl (5 - bits)) and 31])
    }
    return output.toString()
}

/**
 * HOTP for a raw key. [counter] is the moving factor.
 */
fun hotpFromKey(
    key: ByteArray,
    counter: Long,
    digits: Int,
    algorithm: AuthenticatorAlgorithm,
): String {
    val mac = hmac(algorithm, key, counterBytes(counter))
    return dynamicTruncate(mac, digits)
}

/**
 * HOTP from a base32 secret. Returns null when the secret cannot be decoded.
 */
fun hotpCode(
    secret: String,
    counter: Long,
    digits: Int,
    algorithm: AuthenticatorAlgorithm,
): String? {
    val key = decodeBase32(secret) ?: return null
    if (key.isEmpty()) return null
    return hotpFromKey(key, counter, digits, algorithm)
}

/**
 * TOTP at [nowMs]. The step is `floor(now / period)`.
 */
fun totpCode(
    secret: String,
    nowMs: Long,
    period: Int,
    digits: Int,
    algorithm: AuthenticatorAlgorithm,
): String? {
    val step = nowMs / 1000 / period
    return hotpCode(secret, step, digits, algorithm)
}

/**
 * Steam Guard code. Always SHA1, 30 seconds, 5 characters from the Steam alphabet.
 */
fun steamCode(secret: String, nowMs: Long): String? {
    val key = decodeBase32(secret) ?: return null
    if (key.isEmpty()) return null
    val mac = hmac(AuthenticatorAlgorithm.SHA1, key, counterBytes(nowMs / 1000 / 30))
    val offset = mac.last().toInt() and 0x0f
    var full = ((mac[offset].toInt() and 0x7f) shl 24) or
        ((mac[offset + 1].toInt() and 0xff) shl 16) or
        ((mac[offset + 2].toInt() and 0xff) shl 8) or
        (mac[offset + 3].toInt() and 0xff)
    val code = StringBuilder()
    repeat(5) {
        code.append(STEAM_ALPHABET[full % STEAM_ALPHABET.length])
        full /= STEAM_ALPHABET.length
    }
    return code.toString()
}

/**
 * Kind implied by an entry. A 5-digit code with no explicit kind is the older Steam form.
 */
fun entryKind(kind: AuthenticatorKind?, digits: Int): AuthenticatorKind {
    if (kind == AuthenticatorKind.HOTP || kind == AuthenticatorKind.STEAM) return kind
    if (kind == null && digits == 5) return AuthenticatorKind.STEAM
    return AuthenticatorKind.TOTP
}

/**
 * Current code for an entry, or null when the secret is unusable.
 */
fun currentCode(entry: AuthenticatorEntryView, nowMs: Long): String? {
    if (entry.undecryptable || entry.secret.isBlank()) return null
    return when (entryKind(entry.kind, entry.digits)) {
        AuthenticatorKind.STEAM -> steamCode(entry.secret, nowMs)
        AuthenticatorKind.HOTP -> hotpCode(
            secret = entry.secret,
            counter = entry.counter,
            digits = entry.digits,
            algorithm = entry.algorithm,
        )
        AuthenticatorKind.TOTP -> totpCode(
            secret = entry.secret,
            nowMs = nowMs,
            period = entry.period.coerceAtLeast(1),
            digits = entry.digits,
            algorithm = entry.algorithm,
        )
    }
}

/**
 * The code in the next TOTP window. HOTP and Steam have no preview.
 */
fun nextTotpCode(entry: AuthenticatorEntryView, nowMs: Long): String? {
    if (entry.undecryptable || entry.secret.isBlank()) return null
    if (entryKind(entry.kind, entry.digits) != AuthenticatorKind.TOTP) return null
    val period = entry.period.coerceAtLeast(1)
    return totpCode(
        secret = entry.secret,
        nowMs = nowMs + period * 1000L,
        period = period,
        digits = entry.digits,
        algorithm = entry.algorithm,
    )
}

/**
 * Seconds left in the current TOTP period.
 */
fun secondsRemaining(nowMs: Long, period: Int): Int {
    val safePeriod = period.coerceAtLeast(1)
    val elapsed = ((nowMs / 1000) % safePeriod).toInt()
    return safePeriod - elapsed
}

/**
 * Inserts a space in the middle of a numeric code so it is easier to read.
 */
fun formatAuthenticatorCode(code: String): String {
    if (code.length <= 4) return code
    val half = code.length / 2
    return code.substring(0, half) + " " + code.substring(half)
}

private fun counterBytes(counter: Long): ByteArray {
    val bytes = ByteArray(8)
    var value = counter
    for (index in 7 downTo 0) {
        bytes[index] = (value and 0xff).toByte()
        value = value ushr 8
    }
    return bytes
}

private fun hmac(
    algorithm: AuthenticatorAlgorithm,
    key: ByteArray,
    data: ByteArray,
): ByteArray {
    val name = when (algorithm) {
        AuthenticatorAlgorithm.SHA256 -> "HmacSHA256"
        AuthenticatorAlgorithm.SHA512 -> "HmacSHA512"
        AuthenticatorAlgorithm.SHA1 -> "HmacSHA1"
    }
    val mac = Mac.getInstance(name)
    mac.init(SecretKeySpec(key, name))
    return mac.doFinal(data)
}

private fun dynamicTruncate(mac: ByteArray, digits: Int): String {
    val offset = mac.last().toInt() and 0x0f
    val binary = ((mac[offset].toInt() and 0x7f) shl 24) or
        ((mac[offset + 1].toInt() and 0xff) shl 16) or
        ((mac[offset + 2].toInt() and 0xff) shl 8) or
        (mac[offset + 3].toInt() and 0xff)
    val modulus = Math.pow(10.0, digits.toDouble()).toInt()
    return (binary % modulus).toString().padStart(digits, '0')
}
