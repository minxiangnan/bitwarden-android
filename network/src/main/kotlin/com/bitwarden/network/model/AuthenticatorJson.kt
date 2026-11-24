package com.bitwarden.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Ciphertext authenticator entry stored by the zh-authenticator server.
 */
@Serializable
data class AuthenticatorEntryJson(
    @SerialName("id")
    val id: String = "",

    @SerialName("name")
    val name: String = "",

    @SerialName("issuer")
    val issuer: String? = null,

    @SerialName("secret")
    val secret: String = "",

    @SerialName("algorithm")
    val algorithm: String = "SHA1",

    @SerialName("digits")
    val digits: Int = 6,

    @SerialName("period")
    val period: Int = 30,

    @SerialName("notes")
    val notes: String? = null,

    @SerialName("favorite")
    val favorite: Boolean = false,

    @SerialName("sortOrder")
    val sortOrder: Int = 0,

    @SerialName("revisionDate")
    val revisionDate: String = "",

    @SerialName("creationDate")
    val creationDate: String = "",
)

/**
 * Body for creating or replacing an authenticator entry.
 * Name, secret, and any present issuer or notes must already be EncStrings.
 */
@Serializable
data class AuthenticatorEntryRequestJson(
    @SerialName("name")
    val name: String,

    @SerialName("issuer")
    val issuer: String? = null,

    @SerialName("secret")
    val secret: String,

    @SerialName("algorithm")
    val algorithm: String = "SHA1",

    @SerialName("digits")
    val digits: Int = 6,

    @SerialName("period")
    val period: Int = 30,

    @SerialName("notes")
    val notes: String? = null,

    @SerialName("favorite")
    val favorite: Boolean = false,

    @SerialName("sortOrder")
    val sortOrder: Int = 0,
)

/**
 * `GET /authenticator` list.
 */
@Serializable
data class AuthenticatorListJson(
    @SerialName("data")
    val data: List<AuthenticatorEntryJson> = emptyList(),
)

/**
 * MCP session status. A 404 means the server has MCP turned off.
 */
@Serializable
data class McpStatusJson(
    @SerialName("enabled")
    val enabled: Boolean = false,

    @SerialName("configured")
    val configured: Boolean = false,

    @SerialName("revoked")
    val revoked: Boolean = false,

    @SerialName("tokenId")
    val tokenId: String? = null,

    @SerialName("label")
    val label: String? = null,

    @SerialName("keySalt")
    val keySalt: String? = null,

    @SerialName("tokenEnc")
    val tokenEnc: String? = null,

    @SerialName("createdAt")
    val createdAt: String? = null,

    @SerialName("entryCount")
    val entryCount: Int = 0,
)

/**
 * Token returned once by create or rotate.
 */
@Serializable
data class McpIssuedTokenJson(
    @SerialName("token")
    val token: String = "",

    @SerialName("tokenId")
    val tokenId: String = "",

    @SerialName("keySalt")
    val keySalt: String = "",

    /**
     * Server-defined. Vaultwarden 1.2.2 sends an array of entry ids; older responses used a
     * boolean. The client does not branch on it.
     */
    @SerialName("cleared")
    val cleared: JsonElement? = null,
)

/**
 * Body for creating, rotating, or storing the user-key wrap of the MCP token.
 */
@Serializable
data class McpTokenRequestJson(
    @SerialName("label")
    val label: String? = null,

    @SerialName("tokenEnc")
    val tokenEnc: String? = null,
)

/**
 * One opted-in entry. Ciphertext fields are sealed with the MCP token, not the user key.
 */
@Serializable
data class McpEntryRequestJson(
    @SerialName("alias")
    val alias: String,

    @SerialName("secretCt")
    val secretCt: String,

    @SerialName("issuerCt")
    val issuerCt: String? = null,

    @SerialName("accountCt")
    val accountCt: String? = null,

    @SerialName("hostCt")
    val hostCt: String? = null,

    @SerialName("algorithm")
    val algorithm: String = "SHA1",

    @SerialName("digits")
    val digits: Int = 6,

    @SerialName("period")
    val period: Int = 30,

    @SerialName("steam")
    val steam: Boolean = false,
)

/**
 * Summary of an MCP entry. The server never returns the secret.
 */
@Serializable
data class McpEntrySummaryJson(
    @SerialName("id")
    val id: String = "",

    @SerialName("source")
    val source: String = "",

    @SerialName("sourceId")
    val sourceId: String = "",

    @SerialName("alias")
    val alias: String = "",

    @SerialName("algorithm")
    val algorithm: String = "SHA1",

    @SerialName("digits")
    val digits: Int = 6,

    @SerialName("period")
    val period: Int = 30,

    @SerialName("steam")
    val steam: Boolean = false,

    @SerialName("updatedAt")
    val updatedAt: String = "",
)

/**
 * `GET /mcp/entries` list.
 */
@Serializable
data class McpEntryListJson(
    @SerialName("data")
    val data: List<McpEntrySummaryJson> = emptyList(),
)
