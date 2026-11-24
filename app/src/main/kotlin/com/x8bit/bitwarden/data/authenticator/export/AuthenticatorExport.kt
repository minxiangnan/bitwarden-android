package com.x8bit.bitwarden.data.authenticator.export

import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.otp.entryKind
import com.x8bit.bitwarden.data.authenticator.otp.toOtpAuthUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Plaintext migration file. The encrypted copy stays on the server.
 */
const val AUTHENTICATOR_EXPORT_WARNING: String =
    "此文件包含明文密钥，只适合迁移。加密副本在你的服务器上。"

/**
 * One exported authenticator item.
 */
@Serializable
data class AuthenticatorExportItem(
    val otpauth: String,
    val kind: String,
    val host: String,
    val tags: List<String>,
    val counter: Long,
    val notes: String,
    val favorite: Boolean,
    val name: String,
    val issuer: String,
)

/**
 * Document written by [buildAuthenticatorExport].
 */
@Serializable
data class AuthenticatorExportDocument(
    val encrypted: Boolean = false,
    val format: String = "zh-authenticator",
    val warning: String = AUTHENTICATOR_EXPORT_WARNING,
    val items: List<AuthenticatorExportItem>,
)

private val exportJson = Json { encodeDefaults = true }

/**
 * Builds the same `zh-authenticator` document the browser extension exports.
 */
fun buildAuthenticatorExport(entries: List<AuthenticatorEntryView>): String {
    val document = AuthenticatorExportDocument(
        items = entries
            .filter { !it.undecryptable && it.secret.isNotBlank() && it.deletedAt == null }
            .map { entry ->
                AuthenticatorExportItem(
                    otpauth = toOtpAuthUri(entry),
                    kind = entryKind(entry.kind, entry.digits).name.lowercase(),
                    host = entry.host,
                    tags = entry.tags,
                    counter = entry.counter,
                    notes = entry.notes,
                    favorite = entry.favorite,
                    name = entry.name,
                    issuer = entry.issuer,
                )
            },
    )
    return exportJson.encodeToString(document)
}
