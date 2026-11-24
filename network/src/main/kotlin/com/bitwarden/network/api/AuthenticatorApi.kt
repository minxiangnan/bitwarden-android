package com.bitwarden.network.api

import com.bitwarden.network.model.AuthenticatorEntryJson
import com.bitwarden.network.model.AuthenticatorEntryRequestJson
import com.bitwarden.network.model.AuthenticatorListJson
import com.bitwarden.network.model.McpEntryListJson
import com.bitwarden.network.model.McpEntryRequestJson
import com.bitwarden.network.model.McpEntrySummaryJson
import com.bitwarden.network.model.McpIssuedTokenJson
import com.bitwarden.network.model.McpStatusJson
import com.bitwarden.network.model.McpTokenRequestJson
import com.bitwarden.network.model.NetworkResult
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Authenticator entries and the MCP opt-in API used by the zh-authenticator server.
 * Paths are relative to the authenticated `/api/` base URL.
 */
@Suppress("TooManyFunctions")
internal interface AuthenticatorApi {

    /**
     * Lists ciphertext authenticator entries.
     */
    @GET("authenticator")
    suspend fun listEntries(): NetworkResult<AuthenticatorListJson>

    /**
     * Creates an authenticator entry.
     */
    @POST("authenticator")
    suspend fun createEntry(
        @Body body: AuthenticatorEntryRequestJson,
    ): NetworkResult<AuthenticatorEntryJson>

    /**
     * Replaces an authenticator entry.
     */
    @PUT("authenticator/{id}")
    suspend fun updateEntry(
        @Path("id") id: String,
        @Body body: AuthenticatorEntryRequestJson,
    ): NetworkResult<AuthenticatorEntryJson>

    /**
     * Permanently deletes an authenticator entry.
     */
    @DELETE("authenticator/{id}")
    suspend fun deleteEntry(@Path("id") id: String): NetworkResult<Unit>

    /**
     * Reads MCP status. 404 means the server has MCP turned off.
     */
    @GET("mcp/status")
    suspend fun mcpStatus(): NetworkResult<McpStatusJson>

    /**
     * Creates an MCP access token. The plaintext token is returned once.
     */
    @POST("mcp/token")
    suspend fun createMcpToken(
        @Body body: McpTokenRequestJson,
    ): NetworkResult<McpIssuedTokenJson>

    /**
     * Stores the user-key wrap of the current MCP token.
     */
    @PUT("mcp/token/enc")
    suspend fun putMcpTokenEnc(@Body body: McpTokenRequestJson): NetworkResult<Unit>

    /**
     * Rotates the MCP access token. Previous wraps stop working.
     */
    @POST("mcp/token/rotate")
    suspend fun rotateMcpToken(
        @Body body: McpTokenRequestJson,
    ): NetworkResult<McpIssuedTokenJson>

    /**
     * Revokes the MCP access token and its entry wraps.
     */
    @DELETE("mcp/token")
    suspend fun revokeMcpToken(): NetworkResult<Unit>

    /**
     * Lists MCP entry summaries.
     */
    @GET("mcp/entries")
    suspend fun listMcpEntries(): NetworkResult<McpEntryListJson>

    /**
     * Opts an entry into MCP, or replaces its wrap.
     */
    @PUT("mcp/entries/{source}/{sourceId}")
    suspend fun putMcpEntry(
        @Path("source") source: String,
        @Path("sourceId") sourceId: String,
        @Body body: McpEntryRequestJson,
    ): NetworkResult<McpEntrySummaryJson>

    /**
     * Removes an entry from MCP.
     */
    @DELETE("mcp/entries/{source}/{sourceId}")
    suspend fun deleteMcpEntry(
        @Path("source") source: String,
        @Path("sourceId") sourceId: String,
    ): NetworkResult<Unit>
}
