@file:Suppress("MagicNumber")

package com.x8bit.bitwarden.data.authenticator.meta

import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import com.x8bit.bitwarden.data.authenticator.order.TOTP_ORDER_FIELD
import com.x8bit.bitwarden.data.authenticator.order.formatTotpOrder
import com.x8bit.bitwarden.data.authenticator.order.parseTotpOrder
import kotlinx.serialization.json.put

private const val SENTINEL = "[[bwmeta:1]]"

private val KNOWN_META_KEYS = setOf(
    "kind",
    "host",
    "tags",
    "counter",
    "deletedAt",
    TOTP_ORDER_FIELD,
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Splits user notes from the encrypted trailer the extension writes.
 */
data class SplitNotes(
    val text: String,
    val meta: AuthenticatorMeta,
    val hasTrailer: Boolean,
    /** Keys other than the form fields and `zh.totp.order`. Never shown as notes. */
    val extras: JsonObject = JsonObject(emptyMap()),
)

/**
 * Parses a comma-separated tag list. Duplicates are dropped, order is kept.
 */
fun parseTagList(value: String): List<String> {
    val seen = LinkedHashSet<String>()
    val tags = ArrayList<String>()
    for (part in value.split(',', '，')) {
        val tag = part.trim()
        if (tag.isEmpty() || !seen.add(tag.lowercase())) continue
        tags.add(tag)
    }
    return tags
}

/**
 * A 5-digit TOTP keeps an explicit trailer so it is not read back as Steam.
 */
fun shouldPersistMeta(meta: AuthenticatorMeta, digits: Int): Boolean {
    return meta.kind != AuthenticatorKind.TOTP ||
        digits == 5 ||
        meta.host.isNotEmpty() ||
        meta.tags.isNotEmpty() ||
        meta.counter > 0 ||
        meta.deletedAt != null ||
        parseTotpOrder(meta.order) != null
}

/**
 * Separates the visible notes from the `[[bwmeta:1]]` trailer.
 */
fun splitAuthenticatorNotes(notes: String): SplitNotes {
    val index = notes.indexOf(SENTINEL)
    if (index < 0) {
        return SplitNotes(text = notes, meta = AuthenticatorMeta(), hasTrailer = false)
    }
    val text = notes.substring(0, index).trim()
    val parsed = runCatching {
        json.parseToJsonElement(notes.substring(index + SENTINEL.length))
    }.getOrNull()
    if (parsed !is JsonObject) {
        return SplitNotes(text = notes, meta = AuthenticatorMeta(), hasTrailer = false)
    }
    return SplitNotes(
        text = text,
        meta = sanitizeMeta(parsed),
        hasTrailer = true,
        extras = extrasFrom(parsed),
    )
}

/**
 * Joins visible notes and the trailer. The trailer is omitted when it carries no extra fields.
 */
fun joinAuthenticatorNotes(
    text: String,
    meta: AuthenticatorMeta,
    digits: Int,
    extras: JsonObject = JsonObject(emptyMap()),
): String {
    val body = text.trim()
    val merged = buildJsonObject {
        extrasFrom(extras).forEach { (key, value) -> put(key, value) }
        compactMeta(meta).forEach { (key, value) -> put(key, value) }
    }
    if (merged.isEmpty() && !shouldPersistMeta(meta, digits)) return body
    val trailer = SENTINEL + merged.toString()
    return if (body.isEmpty()) trailer else "$body\n$trailer"
}

/**
 * Digits of 5 with no trailer is the Steam entry this form used to save.
 */
fun kindFromStored(meta: AuthenticatorMeta, hasTrailer: Boolean, digits: Int): AuthenticatorKind {
    if (!hasTrailer && digits == 5) return AuthenticatorKind.STEAM
    return meta.kind
}

private fun compactMeta(meta: AuthenticatorMeta): JsonObject = buildJsonObject {
    if (meta.kind != AuthenticatorKind.TOTP) put("kind", meta.kind.name.lowercase())
    if (meta.host.isNotEmpty()) put("host", meta.host)
    if (meta.tags.isNotEmpty()) put("tags", JsonArray(meta.tags.map { JsonPrimitive(it) }))
    if (meta.counter > 0) put("counter", meta.counter)
    if (meta.deletedAt != null) put("deletedAt", meta.deletedAt)
    parseTotpOrder(meta.order)?.let { put(TOTP_ORDER_FIELD, formatTotpOrder(it)) }
}

private fun sanitizeMeta(record: JsonObject): AuthenticatorMeta {
    val kind = when (record.string("kind")) {
        "hotp" -> AuthenticatorKind.HOTP
        "steam" -> AuthenticatorKind.STEAM
        else -> AuthenticatorKind.TOTP
    }
    val host = record.string("host").orEmpty().trim().lowercase()
    val tags = (record["tags"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.let { parseTagList(it.joinToString(",")) }
        .orEmpty()
    val counter = record["counter"]?.jsonPrimitive?.longOrNull
        ?: record["counter"]?.jsonPrimitive?.intOrNull?.toLong()
        ?: 0
    val deletedAt = record.string("deletedAt")?.takeIf {
        runCatching { java.time.Instant.parse(it) }.isSuccess
    }
    return AuthenticatorMeta(
        kind = kind,
        host = host,
        tags = tags,
        counter = counter.coerceAtLeast(0),
        deletedAt = deletedAt,
        order = record.string(TOTP_ORDER_FIELD)?.let { raw ->
            parseTotpOrder(raw)?.let(::formatTotpOrder)
        },
    )
}

private fun extrasFrom(record: JsonObject): JsonObject = buildJsonObject {
    record.forEach { (key, value) ->
        if (key !in KNOWN_META_KEYS && key != "__proto__" && key != "constructor") {
            put(key, value)
        }
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
