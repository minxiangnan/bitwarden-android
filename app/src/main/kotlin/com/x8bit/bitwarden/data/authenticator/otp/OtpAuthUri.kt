@file:Suppress("CyclomaticComplexMethod", "MagicNumber")

package com.x8bit.bitwarden.data.authenticator.otp

import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorAlgorithm
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorDraft
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.model.DEFAULT_DIGITS
import com.x8bit.bitwarden.data.authenticator.model.DEFAULT_PERIOD
import com.x8bit.bitwarden.data.authenticator.model.MAX_DIGITS
import com.x8bit.bitwarden.data.authenticator.model.MAX_PERIOD
import com.x8bit.bitwarden.data.authenticator.model.MIN_DIGITS
import com.x8bit.bitwarden.data.authenticator.model.MIN_PERIOD
import com.x8bit.bitwarden.data.authenticator.model.STEAM_DIGITS
import com.x8bit.bitwarden.data.authenticator.model.normalizeAlgorithm
import java.net.URLDecoder
import java.net.URLEncoder

private val BASE32_PATTERN = Regex("^[A-Z2-7]+=*$")

/**
 * Parsed `otpauth://` URI.
 */
data class ParsedOtpAuth(
    val name: String,
    val issuer: String,
    val secret: String,
    val algorithm: AuthenticatorAlgorithm,
    val digits: Int,
    val period: Int,
    val kind: AuthenticatorKind,
    val counter: Long,
)

/**
 * Strips spaces and uppercases a user-entered secret.
 */
fun normalizeTotpSecret(secret: String): String =
    secret.replace(Regex("\\s+"), "").uppercase()

/**
 * A secret is usable when it is at least 8 base32 characters.
 */
fun isValidTotpSecret(secret: String): Boolean {
    val normalized = normalizeTotpSecret(secret)
    return normalized.length >= 8 && BASE32_PATTERN.matches(normalized)
}

/**
 * Builds the otpauth URI stored by the extension export.
 */
fun toOtpAuthUri(entry: AuthenticatorEntryView): String {
    val label = if (entry.issuer.isNotBlank()) "${entry.issuer}:${entry.name}" else entry.name
    val kind = entryKind(entry.kind, entry.digits)
    val digits = if (kind == AuthenticatorKind.STEAM) STEAM_DIGITS else entry.digits
    val algorithm = if (kind == AuthenticatorKind.STEAM) "steam" else entry.algorithm.name
    val params = linkedMapOf(
        "secret" to normalizeTotpSecret(entry.secret),
        "algorithm" to algorithm,
        "digits" to digits.toString(),
        "period" to entry.period.toString(),
    )
    if (entry.issuer.isNotBlank()) params["issuer"] = entry.issuer
    val query = params.entries.joinToString("&") { (key, value) ->
        "$key=${urlEncode(value)}"
    }
    val type = if (kind == AuthenticatorKind.HOTP) "hotp" else "totp"
    val counter = if (kind == AuthenticatorKind.HOTP) "&counter=${entry.counter}" else ""
    return "otpauth://$type/${urlEncode(label)}?$query$counter"
}

/**
 * Reads an otpauth URI. Returns null when it is not a TOTP or HOTP secret.
 */
fun parseOtpAuthUri(value: String): ParsedOtpAuth? {
    val trimmed = value.trim()
    if (!trimmed.lowercase().startsWith("otpauth://")) return null
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
    val type = uri.host?.lowercase() ?: return null
    if (uri.scheme != "otpauth" || (type != "totp" && type != "hotp")) return null
    val params = queryParams(uri.rawQuery ?: return null)
    val secret = normalizeTotpSecret(params["secret"].orEmpty())
    if (!isValidTotpSecret(secret)) return null
    val label = runCatching {
        URLDecoder.decode(uri.rawPath?.removePrefix("/").orEmpty(), Charsets.UTF_8)
    }.getOrNull() ?: return null
    val separator = label.indexOf(':')
    val issuerFromLabel = if (separator >= 0) label.substring(0, separator) else ""
    val nameFromLabel = if (separator >= 0) label.substring(separator + 1) else label
    val issuer = (params["issuer"] ?: issuerFromLabel).trim()
    val steam = params["algorithm"].orEmpty().lowercase() == "steam"
    val hotp = type == "hotp"
    val counter = boundedLong(params["counter"], 0, 0, Long.MAX_VALUE) ?: return null
    val digits = boundedInt(
        raw = params["digits"],
        fallback = if (steam) STEAM_DIGITS else DEFAULT_DIGITS,
        min = MIN_DIGITS,
        max = MAX_DIGITS,
    ) ?: return null
    val period = boundedInt(
        raw = params["period"],
        fallback = DEFAULT_PERIOD,
        min = MIN_PERIOD,
        max = MAX_PERIOD,
    ) ?: return null
    if (steam && digits != STEAM_DIGITS) return null
    return ParsedOtpAuth(
        name = nameFromLabel.trim().ifBlank { issuer.ifBlank { "TOTP" } },
        issuer = issuer,
        secret = secret,
        algorithm = normalizeAlgorithm(if (steam) "SHA1" else params["algorithm"]),
        digits = digits,
        period = period,
        kind = when {
            steam -> AuthenticatorKind.STEAM
            hotp -> AuthenticatorKind.HOTP
            else -> AuthenticatorKind.TOTP
        },
        counter = if (hotp) counter else 0,
    )
}

/**
 * Turns a parsed URI into a draft the repository can save.
 */
fun ParsedOtpAuth.toDraft(): AuthenticatorDraft = AuthenticatorDraft(
    name = name,
    issuer = issuer,
    secret = secret,
    algorithm = algorithm,
    digits = digits,
    period = period,
    kind = kind,
    counter = counter,
)

private fun queryParams(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    return query.split('&').mapNotNull { part ->
        val index = part.indexOf('=')
        if (index < 0) return@mapNotNull null
        val key = URLDecoder.decode(part.substring(0, index), Charsets.UTF_8)
        val value = URLDecoder.decode(part.substring(index + 1), Charsets.UTF_8)
        key to value
    }.toMap()
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

private fun boundedInt(raw: String?, fallback: Int, min: Int, max: Int): Int? {
    if (raw.isNullOrBlank()) return fallback
    val value = raw.toIntOrNull() ?: return null
    if (value < min || value > max) return null
    return value
}

private fun boundedLong(raw: String?, fallback: Long, min: Long, max: Long): Long? {
    if (raw.isNullOrBlank()) return fallback
    val value = raw.toLongOrNull() ?: return null
    if (value < min || value > max) return null
    return value
}
