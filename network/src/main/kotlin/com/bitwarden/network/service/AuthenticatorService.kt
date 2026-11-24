package com.bitwarden.network.service

import com.bitwarden.network.model.AuthenticatorEntryJson
import com.bitwarden.network.model.AuthenticatorEntryRequestJson
import com.bitwarden.network.model.McpEntryRequestJson
import com.bitwarden.network.model.McpEntrySummaryJson
import com.bitwarden.network.model.McpIssuedTokenJson
import com.bitwarden.network.model.McpStatusJson

/**
 * Authenticator vault and MCP opt-in calls for the zh-authenticator server.
 */
@Suppress("TooManyFunctions")
interface AuthenticatorService {
    /**
     * Lists ciphertext authenticator entries.
     */
    suspend fun listEntries(): Result<List<AuthenticatorEntryJson>>

    /**
     * Creates an authenticator entry and returns the stored row.
     */
    suspend fun createEntry(
        body: AuthenticatorEntryRequestJson,
    ): Result<AuthenticatorEntryJson>

    /**
     * Replaces an authenticator entry.
     */
    suspend fun updateEntry(
        id: String,
        body: AuthenticatorEntryRequestJson,
    ): Result<AuthenticatorEntryJson>

    /**
     * Permanently deletes an authenticator entry.
     */
    suspend fun deleteEntry(id: String): Result<Unit>

    /**
     * Reads MCP status. Failure with HTTP 404 means MCP is turned off.
     */
    suspend fun mcpStatus(): Result<McpStatusJson>

    /**
     * Creates an MCP access token. The plaintext is returned once.
     */
    suspend fun createMcpToken(label: String): Result<McpIssuedTokenJson>

    /**
     * Stores the user-key wrap so another device can unwrap the same token.
     */
    suspend fun putMcpTokenEnc(tokenEnc: String): Result<Unit>

    /**
     * Rotates the MCP access token.
     */
    suspend fun rotateMcpToken(label: String): Result<McpIssuedTokenJson>

    /**
     * Revokes the MCP access token.
     */
    suspend fun revokeMcpToken(): Result<Unit>

    /**
     * Lists MCP entry summaries for the current user.
     */
    suspend fun listMcpEntries(): Result<List<McpEntrySummaryJson>>

    /**
     * Opts [sourceId] into MCP.
     */
    suspend fun putMcpEntry(
        source: String,
        sourceId: String,
        body: McpEntryRequestJson,
    ): Result<McpEntrySummaryJson>

    /**
     * Removes [sourceId] from MCP.
     */
    suspend fun deleteMcpEntry(source: String, sourceId: String): Result<Unit>
}
