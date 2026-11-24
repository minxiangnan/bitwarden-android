package com.bitwarden.network.service

import com.bitwarden.network.api.AuthenticatorApi
import com.bitwarden.network.model.AuthenticatorEntryJson
import com.bitwarden.network.model.AuthenticatorEntryRequestJson
import com.bitwarden.network.model.McpEntryRequestJson
import com.bitwarden.network.model.McpEntrySummaryJson
import com.bitwarden.network.model.McpIssuedTokenJson
import com.bitwarden.network.model.McpStatusJson
import com.bitwarden.network.model.McpTokenRequestJson
import com.bitwarden.network.util.toResult

@Suppress("TooManyFunctions")
internal class AuthenticatorServiceImpl(
    private val api: AuthenticatorApi,
) : AuthenticatorService {
    override suspend fun listEntries(): Result<List<AuthenticatorEntryJson>> =
        api.listEntries().toResult().map { it.data }

    override suspend fun createEntry(
        body: AuthenticatorEntryRequestJson,
    ): Result<AuthenticatorEntryJson> = api.createEntry(body).toResult()

    override suspend fun updateEntry(
        id: String,
        body: AuthenticatorEntryRequestJson,
    ): Result<AuthenticatorEntryJson> = api.updateEntry(id, body).toResult()

    override suspend fun deleteEntry(id: String): Result<Unit> =
        api.deleteEntry(id).toResult()

    override suspend fun mcpStatus(): Result<McpStatusJson> = api.mcpStatus().toResult()

    override suspend fun createMcpToken(label: String): Result<McpIssuedTokenJson> =
        api.createMcpToken(McpTokenRequestJson(label = label)).toResult()

    override suspend fun putMcpTokenEnc(tokenEnc: String): Result<Unit> =
        api.putMcpTokenEnc(McpTokenRequestJson(tokenEnc = tokenEnc)).toResult()

    override suspend fun rotateMcpToken(label: String): Result<McpIssuedTokenJson> =
        api.rotateMcpToken(McpTokenRequestJson(label = label)).toResult()

    override suspend fun revokeMcpToken(): Result<Unit> = api.revokeMcpToken().toResult()

    override suspend fun listMcpEntries(): Result<List<McpEntrySummaryJson>> =
        api.listMcpEntries().toResult().map { it.data }

    override suspend fun putMcpEntry(
        source: String,
        sourceId: String,
        body: McpEntryRequestJson,
    ): Result<McpEntrySummaryJson> = api.putMcpEntry(source, sourceId, body).toResult()

    override suspend fun deleteMcpEntry(source: String, sourceId: String): Result<Unit> =
        api.deleteMcpEntry(source, sourceId).toResult()
}
