@file:Suppress(
    "CyclomaticComplexMethod",
    "LongParameterList",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
    "MultiLineIfElse",
    "NestedBlockDepth",
    "TooManyFunctions",
)

package com.x8bit.bitwarden.data.authenticator.importing

import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorAlgorithm
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorDraft
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.model.DEFAULT_DIGITS
import com.x8bit.bitwarden.data.authenticator.model.DEFAULT_PERIOD
import com.x8bit.bitwarden.data.authenticator.otp.decodeBase32
import com.x8bit.bitwarden.data.authenticator.otp.encodeBase32
import com.x8bit.bitwarden.data.authenticator.otp.parseOtpAuthUri
import com.x8bit.bitwarden.data.authenticator.otp.toDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

private val json = Json { ignoreUnknownKeys = true }

/**
 * Result of reading a migration file or pasted text.
 */
data class AuthenticatorImportResult(
    val format: String,
    val entries: List<AuthenticatorDraft>,
    val encrypted: Boolean,
)

/**
 * Reads Google migration URIs, unencrypted Aegis, 2FAS, and Bitwarden JSON or CSV.
 * Encrypted Aegis and 2FAS backups are recognized and not imported.
 */
fun parseAuthenticatorImport(text: String): AuthenticatorImportResult {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return AuthenticatorImportResult("empty", emptyList(), false)
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        val parsed = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
            ?: return AuthenticatorImportResult("empty", emptyList(), false)
        return parseJsonImport(parsed)
    }
    val fromUris = parseUriBlob(trimmed)
    if (fromUris.entries.isNotEmpty()) return fromUris
    return parseCsv(trimmed)
}

/**
 * Decodes one `otpauth-migration://` URI into drafts.
 */
fun decodeMigrationUri(uri: String): List<AuthenticatorDraft> {
    val parsed = runCatching { java.net.URI(uri) }.getOrNull() ?: return emptyList()
    val data = queryParams(parsed.rawQuery.orEmpty())["data"] ?: return emptyList()
    val bytes = decodeBase64(data) ?: return emptyList()
    return decodeMigrationPayload(bytes)
}

/**
 * Decodes a Google Authenticator migration protobuf payload.
 */
fun decodeMigrationPayload(bytes: ByteArray): List<AuthenticatorDraft> {
    val entries = ArrayList<AuthenticatorDraft>()
    walkProtobuf(bytes) { field, wire, value ->
        if (field == 1 && wire == 2 && value is ByteArray) {
            decodeOtpParameters(value)?.let { entries.add(it) }
        }
    }
    return entries
}

private fun parseJsonImport(value: kotlinx.serialization.json.JsonElement): AuthenticatorImportResult {
    if (value is JsonArray) {
        return AuthenticatorImportResult("bitwarden-json", parseBitwardenItems(value), false)
    }
    if (value !is JsonObject) return emptyResult()
    if (value.string("format") == "zh-authenticator" && value["items"] is JsonArray) {
        return AuthenticatorImportResult(
            format = "zh-authenticator",
            entries = parseOwnExport(value["items"] as JsonArray),
            encrypted = false,
        )
    }
    if (value["servicesEncrypted"] != null) {
        return AuthenticatorImportResult("2fas", emptyList(), true)
    }
    if (value["services"] is JsonArray) {
        return AuthenticatorImportResult("2fas", parseTwoFas(value["services"] as JsonArray), false)
    }
    val header = value["header"] as? JsonObject
    if (header?.get("slots") != null) {
        return AuthenticatorImportResult("aegis", emptyList(), true)
    }
    val db = value["db"] as? JsonObject
    if (db?.get("entries") is JsonArray) {
        return AuthenticatorImportResult("aegis", parseAegis(db["entries"] as JsonArray), false)
    }
    if (value["items"] is JsonArray) {
        return AuthenticatorImportResult(
            "bitwarden-json",
            parseBitwardenItems(value["items"] as JsonArray),
            false,
        )
    }
    return emptyResult()
}

private fun parseOwnExport(items: JsonArray): List<AuthenticatorDraft> {
    return items.mapNotNull { item ->
        val record = item as? JsonObject ?: return@mapNotNull null
        val parsed = record.string("otpauth")?.let { parseOtpAuthUri(it) } ?: return@mapNotNull null
        parsed.toDraft().copy(
            notes = record.string("notes").orEmpty(),
            favorite = record.bool("favorite"),
            kind = kindOr(record.string("kind"), parsed.kind),
            host = record.string("host").orEmpty().trim().lowercase(),
            tags = (record["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            counter = record.long("counter") ?: parsed.counter,
        )
    }
}

private fun parseAegis(items: JsonArray): List<AuthenticatorDraft> {
    return items.mapNotNull { item ->
        val record = item as? JsonObject ?: return@mapNotNull null
        val info = record["info"] as? JsonObject
        val secret = info?.string("secret").orEmpty()
        if (secret.isBlank()) return@mapNotNull null
        val type = record.string("type").orEmpty().lowercase()
        draftFromParts(
            name = record.string("name") ?: record.string("issuer") ?: "TOTP",
            issuer = record.string("issuer").orEmpty(),
            secret = secret,
            algorithm = info?.string("algo") ?: "SHA1",
            digits = info?.int("digits") ?: DEFAULT_DIGITS,
            period = info?.int("period") ?: DEFAULT_PERIOD,
            notes = record.string("note").orEmpty(),
            favorite = record.bool("favorite"),
            kind = when (type) {
                "hotp" -> AuthenticatorKind.HOTP
                "steam" -> AuthenticatorKind.STEAM
                else -> AuthenticatorKind.TOTP
            },
            counter = info?.long("counter") ?: 0,
        )
    }
}

private fun parseTwoFas(items: JsonArray): List<AuthenticatorDraft> {
    return items.mapNotNull { item ->
        val record = item as? JsonObject ?: return@mapNotNull null
        val otp = record["otp"] as? JsonObject ?: JsonObject(emptyMap())
        val secret = record.string("secret").orEmpty()
        if (secret.isBlank()) return@mapNotNull null
        val token = otp.string("tokenType").orEmpty().uppercase()
        draftFromParts(
            name = otp.string("account") ?: record.string("name") ?: "TOTP",
            issuer = otp.string("issuer") ?: record.string("name").orEmpty(),
            secret = secret,
            algorithm = otp.string("algorithm") ?: "SHA1",
            digits = otp.int("digits") ?: DEFAULT_DIGITS,
            period = otp.int("period") ?: DEFAULT_PERIOD,
            notes = "",
            favorite = false,
            kind = if (token == "HOTP") AuthenticatorKind.HOTP else AuthenticatorKind.TOTP,
            counter = otp.long("counter") ?: 0,
        )
    }
}

private fun parseBitwardenItems(items: JsonArray): List<AuthenticatorDraft> {
    val entries = ArrayList<AuthenticatorDraft>()
    for (item in items) {
        val record = item as? JsonObject ?: continue
        val login = record["login"] as? JsonObject
        val totp = login?.string("totp").orEmpty()
        if (totp.isBlank()) continue
        val parsed = parseOtpAuthUri(totp)
        if (parsed != null) {
            entries.add(
                parsed.toDraft().copy(
                    name = parsed.name.ifBlank { record.string("name") ?: parsed.issuer.ifBlank { "TOTP" } },
                ),
            )
            continue
        }
        val draft = draftFromParts(
            name = record.string("name") ?: "TOTP",
            issuer = "",
            secret = totp,
            algorithm = "SHA1",
            digits = DEFAULT_DIGITS,
            period = DEFAULT_PERIOD,
            notes = "",
            favorite = false,
            kind = AuthenticatorKind.TOTP,
            counter = 0,
        )
        if (draft != null) entries.add(draft)
    }
    return entries
}

private fun parseUriBlob(text: String): AuthenticatorImportResult {
    val entries = ArrayList<AuthenticatorDraft>()
    val migrations = Regex("otpauth-migration://\\S+", RegexOption.IGNORE_CASE).findAll(text)
    var migrationCount = 0
    migrations.forEach { match ->
        migrationCount++
        entries.addAll(decodeMigrationUri(match.value))
    }
    Regex("otpauth://(?:totp|hotp)/\\S+", RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
        parseOtpAuthUri(match.value)?.toDraft()?.let { entries.add(it) }
    }
    return AuthenticatorImportResult(
        format = if (migrationCount > 0) "otpauth-migration" else "otpauth",
        entries = entries,
        encrypted = false,
    )
}

private fun parseCsv(text: String): AuthenticatorImportResult {
    val rows = parseCsvRows(text)
    if (rows.size < 2) return emptyResult()
    val header = rows.first().map { it.trim().lowercase() }
    val totpIndex = header.indexOfFirst { it == "login_totp" || it == "totp" }
    if (totpIndex < 0) return emptyResult()
    val nameIndex = header.indexOf("name")
    val entries = ArrayList<AuthenticatorDraft>()
    for (row in rows.drop(1)) {
        val totp = row.getOrNull(totpIndex)?.trim().orEmpty()
        if (totp.isEmpty()) continue
        val name = if (nameIndex >= 0) row.getOrNull(nameIndex).orEmpty() else ""
        val parsed = parseOtpAuthUri(totp)
        if (parsed != null) {
            entries.add(parsed.toDraft().copy(name = parsed.name.ifBlank { name.ifBlank { "TOTP" } }))
            continue
        }
        draftFromParts(
            name = name.ifBlank { "TOTP" },
            issuer = "",
            secret = totp,
            algorithm = "SHA1",
            digits = DEFAULT_DIGITS,
            period = DEFAULT_PERIOD,
            notes = "",
            favorite = false,
            kind = AuthenticatorKind.TOTP,
            counter = 0,
        )?.let { entries.add(it) }
    }
    return AuthenticatorImportResult("bitwarden-csv", entries, false)
}

private fun decodeOtpParameters(bytes: ByteArray): AuthenticatorDraft? {
    var secret = ByteArray(0)
    var name = ""
    var issuer = ""
    var algorithm = 1
    var digitsEnum = 1
    var otpType = 2
    var counter = 0L
    walkProtobuf(bytes) { field, _, value ->
        when (field) {
            1 -> if (value is ByteArray) secret = value
            2 -> if (value is ByteArray) name = value.toString(Charsets.UTF_8)
            3 -> if (value is ByteArray) issuer = value.toString(Charsets.UTF_8)
            4 -> if (value is Long) algorithm = value.toInt()
            5 -> if (value is Long) digitsEnum = value.toInt()
            6 -> if (value is Long) otpType = value.toInt()
            7 -> if (value is Long) counter = value
        }
    }
    if (secret.isEmpty()) return null
    return draftFromParts(
        name = name.ifBlank { issuer.ifBlank { "TOTP" } },
        issuer = issuer,
        secret = encodeBase32(secret),
        algorithm = when (algorithm) {
            2 -> "SHA256"
            3 -> "SHA512"
            else -> "SHA1"
        },
        digits = if (digitsEnum == 2) 8 else 6,
        period = DEFAULT_PERIOD,
        notes = "",
        favorite = false,
        kind = if (otpType == 1) AuthenticatorKind.HOTP else AuthenticatorKind.TOTP,
        counter = counter,
    )
}

private fun draftFromParts(
    name: String,
    issuer: String,
    secret: String,
    algorithm: String,
    digits: Int,
    period: Int,
    notes: String,
    favorite: Boolean,
    kind: AuthenticatorKind,
    counter: Long,
): AuthenticatorDraft? {
    val normalized = secret.replace(Regex("\\s+"), "").uppercase()
    if (decodeBase32(normalized) == null) return null
    val algo = when (algorithm.uppercase()) {
        "SHA256" -> AuthenticatorAlgorithm.SHA256
        "SHA512" -> AuthenticatorAlgorithm.SHA512
        else -> AuthenticatorAlgorithm.SHA1
    }
    return AuthenticatorDraft(
        name = name.trim().ifBlank { issuer.trim().ifBlank { "TOTP" } },
        issuer = issuer.trim(),
        secret = normalized,
        algorithm = algo,
        digits = digits,
        period = if (kind == AuthenticatorKind.STEAM) 30 else period,
        notes = notes,
        favorite = favorite,
        kind = kind,
        counter = if (kind == AuthenticatorKind.HOTP) counter else 0,
    )
}

private fun parseCsvRows(text: String): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var quoted = false
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (quoted) {
            if (char == '"') {
                if (text.getOrNull(index + 1) == '"') {
                    cell.append('"')
                    index++
                } else {
                    quoted = false
                }
            } else {
                cell.append(char)
            }
        } else if (char == '"') {
            quoted = true
        } else if (char == ',') {
            row.add(cell.toString())
            cell.clear()
        } else if (char == '\n') {
            row.add(cell.toString())
            rows.add(row)
            row = ArrayList()
            cell.clear()
        } else if (char != '\r') {
            cell.append(char)
        }
        index++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) {
        row.add(cell.toString())
        rows.add(row)
    }
    return rows
}

private fun walkProtobuf(
    bytes: ByteArray,
    visit: (field: Int, wire: Int, value: Any) -> Unit,
) {
    var offset = 0
    while (offset < bytes.size) {
        val tag = readVarint(bytes, offset) ?: return
        offset = tag.second
        val field = tag.first ushr 3
        val wire = tag.first and 7
        when (wire) {
            0 -> {
                val value = readVarint(bytes, offset) ?: return
                offset = value.second
                visit(field, wire, value.first.toLong())
            }
            2 -> {
                val length = readVarint(bytes, offset) ?: return
                offset = length.second
                val end = offset + length.first
                if (end > bytes.size) return
                visit(field, wire, bytes.copyOfRange(offset, end))
                offset = end
            }
            1 -> offset += 8
            5 -> offset += 4
            else -> return
        }
    }
}

private fun readVarint(bytes: ByteArray, offset: Int): Pair<Int, Int>? {
    var value = 0
    var shift = 0
    var pos = offset
    while (pos < bytes.size && shift <= 28) {
        val byte = bytes[pos].toInt() and 0xff
        pos++
        value += (byte and 0x7f) shl shift
        if (byte and 0x80 == 0) return value to pos
        shift += 7
    }
    return null
}

private fun decodeBase64(input: String): ByteArray? = runCatching {
    val normalized = input.replace('-', '+').replace('_', '/')
    val pad = (4 - normalized.length % 4) % 4
    Base64.getDecoder().decode(normalized + "=".repeat(pad))
}.getOrNull()

private fun queryParams(query: String): Map<String, String> =
    query.split('&').mapNotNull { part ->
        val index = part.indexOf('=')
        if (index < 0) {
            null
        } else {
            part.substring(0, index) to java.net.URLDecoder.decode(
            part.substring(index + 1),
            Charsets.UTF_8,
        )
        }
    }.toMap()

private fun kindOr(value: String?, fallback: AuthenticatorKind): AuthenticatorKind = when (value) {
    "hotp" -> AuthenticatorKind.HOTP
    "steam" -> AuthenticatorKind.STEAM
    else -> fallback
}

private fun emptyResult() = AuthenticatorImportResult("empty", emptyList(), false)

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull == "true"

private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.long(key: String): Long? =
    this[key]?.jsonPrimitive?.longOrNull ?: this[key]?.jsonPrimitive?.intOrNull?.toLong()
