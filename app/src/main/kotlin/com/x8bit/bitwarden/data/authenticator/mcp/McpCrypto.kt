@file:Suppress("MagicNumber")

package com.x8bit.bitwarden.data.authenticator.mcp

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val HKDF_INFO = "vaultwarden-mcp-totp-v1"
private const val GCM_TAG_BITS = 128
private const val NONCE_LENGTH = 12
private const val KEY_LENGTH = 32

/**
 * Alias the server will accept. Null when the text is empty or has spaces.
 */
fun normalizeAlias(raw: String): String? {
    val alias = raw.trim().lowercase()
    if (alias.length !in 1..64 || alias.any { it.isWhitespace() }) return null
    if (!alias.matches(Regex("^[a-z0-9][a-z0-9._-]*$"))) return null
    return alias
}

/**
 * Additional authenticated data shared with vaultwarden.
 */
fun mcpAad(
    userId: String,
    source: String,
    sourceId: String,
    alias: String,
    field: String,
): ByteArray = "v1|$field|$userId|$source|$sourceId|$alias".toByteArray(Charsets.UTF_8)

/**
 * HKDF-SHA256 key: `HKDF(ikm = token, salt = keySalt, info = vaultwarden-mcp-totp-v1)`.
 */
fun deriveMcpKey(token: String, saltB64: String): ByteArray {
    val salt = decodeBase64Url(saltB64)
    return hkdfSha256(
        ikm = token.toByteArray(Charsets.UTF_8),
        salt = salt,
        info = HKDF_INFO.toByteArray(Charsets.UTF_8),
        length = KEY_LENGTH,
    )
}

/**
 * Seals one field as base64url(nonce || AES-GCM ciphertext || tag).
 */
fun sealField(key: ByteArray, aad: ByteArray, plaintext: String, nonce: ByteArray): String {
    require(nonce.size == NONCE_LENGTH)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(key, "AES"),
        GCMParameterSpec(GCM_TAG_BITS, nonce),
    )
    cipher.updateAAD(aad)
    val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    val packed = ByteArray(nonce.size + encrypted.size)
    nonce.copyInto(packed)
    encrypted.copyInto(packed, nonce.size)
    return encodeBase64Url(packed)
}

/**
 * Opens a field sealed by [sealField]. Used to confirm the phone and the server agree.
 */
fun openField(key: ByteArray, aad: ByteArray, packedB64: String): String {
    val packed = decodeBase64Url(packedB64)
    require(packed.size > NONCE_LENGTH)
    val nonce = packed.copyOfRange(0, NONCE_LENGTH)
    val encrypted = packed.copyOfRange(NONCE_LENGTH, packed.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(key, "AES"),
        GCMParameterSpec(GCM_TAG_BITS, nonce),
    )
    cipher.updateAAD(aad)
    return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
}

/**
 * Encodes bytes as unpadded base64url.
 */
fun encodeBase64Url(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/**
 * Decodes padded or unpadded base64url.
 */
fun decodeBase64Url(value: String): ByteArray {
    val pad = if (value.length % 4 == 0) "" else "=".repeat(4 - value.length % 4)
    return Base64.getUrlDecoder().decode(value + pad)
}

private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    val extract = Mac.getInstance("HmacSHA256")
    val saltKey = if (salt.isEmpty()) ByteArray(KEY_LENGTH) else salt
    extract.init(SecretKeySpec(saltKey, "HmacSHA256"))
    val prk = extract.doFinal(ikm)
    val expand = Mac.getInstance("HmacSHA256")
    expand.init(SecretKeySpec(prk, "HmacSHA256"))
    val output = ByteArray(length)
    var previous = ByteArray(0)
    var produced = 0
    var counter = 1
    while (produced < length) {
        expand.reset()
        expand.update(previous)
        expand.update(info)
        expand.update(byteArrayOf(counter.toByte()))
        previous = expand.doFinal()
        val copy = minOf(previous.size, length - produced)
        previous.copyInto(output, produced, 0, copy)
        produced += copy
        counter++
    }
    return output
}
