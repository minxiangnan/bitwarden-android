package com.x8bit.bitwarden.data.authenticator.repository

import com.bitwarden.network.BitwardenServiceClient
import com.bitwarden.network.model.AuthenticatorEntryJson
import com.bitwarden.network.model.AuthenticatorEntryRequestJson
import com.bitwarden.network.model.McpEntryRequestJson
import com.bitwarden.network.model.McpEntrySummaryJson
import com.bitwarden.network.model.McpIssuedTokenJson
import com.bitwarden.network.util.httpStatusCode
import com.bitwarden.vault.Folder
import com.bitwarden.vault.FolderView
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.authenticator.datasource.AuthenticatorDiskSource
import com.x8bit.bitwarden.data.authenticator.export.buildAuthenticatorExport
import com.x8bit.bitwarden.data.authenticator.importing.parseAuthenticatorImport
import com.x8bit.bitwarden.data.authenticator.match.bestAuthenticatorForUrl
import com.x8bit.bitwarden.data.authenticator.match.rememberRecentId
import com.x8bit.bitwarden.data.authenticator.mcp.deriveMcpKey
import com.x8bit.bitwarden.data.authenticator.mcp.mcpAad
import com.x8bit.bitwarden.data.authenticator.mcp.normalizeAlias
import com.x8bit.bitwarden.data.authenticator.mcp.sealField
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorMeta
import com.x8bit.bitwarden.data.authenticator.meta.joinAuthenticatorNotes
import com.x8bit.bitwarden.data.authenticator.meta.kindFromStored
import com.x8bit.bitwarden.data.authenticator.meta.splitAuthenticatorNotes
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorDraft
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.model.STEAM_DIGITS
import com.x8bit.bitwarden.data.authenticator.model.normalizeAlgorithm
import com.x8bit.bitwarden.data.authenticator.otp.currentCode
import com.x8bit.bitwarden.data.authenticator.otp.entryKind
import com.x8bit.bitwarden.data.authenticator.otp.normalizeTotpSecret
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import com.x8bit.bitwarden.data.vault.manager.VaultLockManager
import com.x8bit.bitwarden.data.vault.repository.model.VaultUnlockData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How fresh the authenticator list is.
 */
enum class AuthenticatorSyncStatus {
    LOADING,
    READY,
    OFFLINE,
    UNSUPPORTED,
    ERROR,
    LOCKED,
}

/**
 * Decrypted authenticator list plus sync status.
 */
data class AuthenticatorSnapshot(
    val status: AuthenticatorSyncStatus = AuthenticatorSyncStatus.LOADING,
    val entries: List<AuthenticatorEntryView> = emptyList(),
    val recentIds: List<String> = emptyList(),
    val mcpAliases: Map<String, String> = emptyMap(),
    val message: String? = null,
    val editable: Boolean = false,
    val syncedAtMillis: Long? = null,
)

/**
 * MCP access token state. [revealedToken] is set only immediately after create or rotate.
 */
data class McpAccessState(
    val supported: Boolean = true,
    val configured: Boolean = false,
    val revoked: Boolean = false,
    val label: String? = null,
    val revealedToken: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

/**
 * Syncs authenticator entries with the zh-authenticator server and keeps an encrypted disk cache.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@Singleton
class AuthenticatorRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val vaultLockManager: VaultLockManager,
    private val vaultSdkSource: VaultSdkSource,
    private val diskSource: AuthenticatorDiskSource,
    private val json: Json,
    serviceClient: BitwardenServiceClient,
) {
    private val service = serviceClient.authenticatorService
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mutex = Mutex()
    private val mutableEntries = MutableStateFlow(AuthenticatorSnapshot())
    private val mutableMcp = MutableStateFlow(McpAccessState())
    private var mcpToken: String? = null
    private var mcpSalt: String? = null
    private var stashedDraft: AuthenticatorDraft? = null

    /**
     * Decrypted entries for the active unlocked user.
     */
    val entries: StateFlow<AuthenticatorSnapshot> = mutableEntries.asStateFlow()

    /**
     * MCP token status for the active user.
     */
    val mcp: StateFlow<McpAccessState> = mutableMcp.asStateFlow()

    init {
        combine(
            authRepository.userStateFlow,
            vaultLockManager.vaultUnlockDataStateFlow,
        ) { user, unlocks ->
            val userId = user?.activeUserId
            val unlocked = userId != null && unlocks.any {
                it.userId == userId && it.status == VaultUnlockData.Status.UNLOCKED
            }
            userId to unlocked
        }
            .onEach { (userId, unlocked) ->
                if (userId == null || !unlocked) {
                    clearSecrets()
                } else {
                    refresh()
                }
            }
            .launchIn(scope)
    }

    /**
     * Reloads entries and MCP status from the server, falling back to the ciphertext cache.
     */
    @Suppress("LongMethod", "TooGenericExceptionCaught")
    suspend fun refresh() {
        val userId = activeUnlockedUserId() ?: return
        val drop = mutex.withLock {
            if (mutableEntries.value.entries.isEmpty()) {
                mutableEntries.value = mutableEntries.value.copy(
                    status = AuthenticatorSyncStatus.LOADING,
                    editable = false,
                )
            }
            val listed = runCatching { service.listEntries() }.getOrElse { error ->
                mutableEntries.value = mutableEntries.value.copy(
                    status = AuthenticatorSyncStatus.ERROR,
                    message = error.message ?: "同步失败。",
                    editable = false,
                )
                return@withLock emptyList()
            }
            try {
                listed.fold(
                onSuccess = { rows ->
                    diskSource.writeCache(userId, json.encodeToString(rows))
                    publishRows(userId, rows, AuthenticatorSyncStatus.READY, null)
                },
                onFailure = { error ->
                    if (error.httpStatusCode() == NOT_FOUND) {
                        mutableEntries.value = AuthenticatorSnapshot(
                            status = AuthenticatorSyncStatus.UNSUPPORTED,
                            message = "这台服务器还没有验证器同步。",
                        )
                    } else {
                        val cached = diskSource.readCache(userId)
                        if (cached != null) {
                            val rows = runCatching {
                                json.decodeFromString<List<AuthenticatorEntryJson>>(cached)
                            }.getOrDefault(emptyList())
                            publishRows(
                                userId,
                                rows,
                                AuthenticatorSyncStatus.OFFLINE,
                                "当前离线，只能查看，不能修改。",
                            )
                        } else {
                            mutableEntries.value = AuthenticatorSnapshot(
                                status = AuthenticatorSyncStatus.ERROR,
                                message = "同步失败。",
                            )
                        }
                    }
                },
            )
            refreshMcpLocked(userId)
            val ids = if (mutableEntries.value.status == AuthenticatorSyncStatus.READY) {
                duplicateAuthenticatorIds(mutableEntries.value.entries)
            } else {
                emptyList()
            }
            if (ids.isNotEmpty()) {
                val kept = mutableEntries.value.entries.filter { it.id !in ids }
                mutableEntries.value = mutableEntries.value.copy(entries = kept)
            }
            ids
            } catch (error: Throwable) {
                mutableEntries.value = mutableEntries.value.copy(
                    status = AuthenticatorSyncStatus.ERROR,
                    message = error.message ?: "同步失败。",
                    editable = false,
                )
                emptyList()
            }
        }
        drop.forEach { id -> service.deleteEntry(id) }
    }

    /**
     * Creates or replaces an entry. Trash and restore go through the same path.
     */
    suspend fun save(entryId: String?, draft: AuthenticatorDraft): Result<Unit> =
        mutate(entryId, draft, draft.sortOrderOrZero(entryId))

    /**
     * Moves an entry to the trash by setting the notes trailer.
     */
    suspend fun moveToTrash(entryId: String): Result<Unit> {
        val entry = find(entryId) ?: return Result.failure(IllegalStateException("找不到这条验证码"))
        val saved = save(entryId, entry.toDraft(deletedAt = Instant.now().toString()))
        if (saved.isSuccess) {
            service.deleteMcpEntry(SOURCE, entryId)
        }
        return saved
    }

    /**
     * Restores an entry from the trash.
     */
    suspend fun restore(entryId: String): Result<Unit> {
        val entry = find(entryId) ?: return Result.failure(IllegalStateException("找不到这条验证码"))
        return save(entryId, entry.toDraft(deletedAt = null))
    }

    /**
     * Permanently deletes an entry.
     */
    suspend fun deleteForever(entryId: String): Result<Unit> {
        if (!mutableEntries.value.editable) {
            return Result.failure(IllegalStateException("离线时不能删除"))
        }
        return service.deleteEntry(entryId).onSuccess { refresh() }
    }

    /**
     * Toggles favorite.
     */
    suspend fun setFavorite(entryId: String, favorite: Boolean): Result<Unit> {
        val entry = find(entryId) ?: return Result.failure(IllegalStateException("找不到这条验证码"))
        return save(entryId, entry.toDraft().copy(favorite = favorite))
    }

    /**
     * Imports a supported export or otpauth text and saves each new entry.
     */
    suspend fun importText(text: String): Result<Int> {
        val parsed = parseAuthenticatorImport(text)
        if (parsed.encrypted) {
            return Result.failure(IllegalArgumentException("这份备份是加密的，请先导出未加密文件"))
        }
        if (parsed.entries.isEmpty()) {
            return Result.failure(IllegalArgumentException("没有可导入的验证码"))
        }
        parsed.entries.forEach { draft ->
            save(entryId = null, draft = draft).getOrThrow()
        }
        return Result.success(parsed.entries.size)
    }

    /**
     * Plaintext export of the live, non-trashed entries.
     */
    fun exportJson(): String = buildAuthenticatorExport(mutableEntries.value.entries)

    /**
     * Current code for the single authenticator that matches [url], if there is one.
     */
    fun codeForUrl(url: String, nowMs: Long): String? {
        val entry = bestAuthenticatorForUrl(mutableEntries.value.entries, url) ?: return null
        return currentCode(entry, nowMs)
    }

    /**
     * Copies the current code and, for HOTP, advances the counter when online.
     */
    suspend fun consumeCode(entryId: String, nowMs: Long): String? {
        val entry = find(entryId) ?: return null
        val code = currentCode(entry, nowMs) ?: return null
        val userId = activeUnlockedUserId()
        if (userId != null) {
            val recent = rememberRecentId(diskSource.readRecent(userId), entryId)
            diskSource.writeRecent(userId, recent)
            mutableEntries.value = mutableEntries.value.copy(recentIds = recent)
        }
        if (entryKind(entry.kind, entry.digits) == AuthenticatorKind.HOTP &&
            mutableEntries.value.editable
        ) {
            save(entryId, entry.toDraft().copy(counter = entry.counter + 1))
        }
        return code
    }

    /**
     * Holds a draft produced by the scanner until the edit screen reads it.
     */
    fun stashDraft(draft: AuthenticatorDraft) {
        stashedDraft = draft
    }

    /**
     * Returns and clears the stashed draft.
     */
    fun consumeStash(): AuthenticatorDraft? = stashedDraft.also { stashedDraft = null }

    /**
     * Creates an MCP token, wraps it with the user key, and returns the plaintext once.
     */
    suspend fun createMcpToken(): Result<String> = issueToken(rotate = false)

    /**
     * Rotates the MCP token and rewraps opted-in entries when the old token is still known.
     */
    suspend fun rotateMcpToken(): Result<String> = issueToken(rotate = true)

    /**
     * Revokes the MCP token.
     */
    suspend fun revokeMcpToken(): Result<Unit> {
        mutableMcp.value = mutableMcp.value.copy(busy = true, revealedToken = null)
        val result = service.revokeMcpToken()
        mcpToken = null
        mcpSalt = null
        refresh()
        return result
    }

    /**
     * Opts an authenticator entry into MCP, or removes it when [enabled] is false.
     */
    suspend fun setMcp(entryId: String, enabled: Boolean, alias: String): Result<Unit> {
        val userId = activeUnlockedUserId()
            ?: return Result.failure(IllegalStateException("请先解锁"))
        if (!enabled) {
            return service.deleteMcpEntry(SOURCE, entryId).onSuccess { refresh() }
        }
        val entry = find(entryId) ?: return Result.failure(IllegalStateException("找不到这条验证码"))
        if (entryKind(entry.kind, entry.digits) == AuthenticatorKind.HOTP) {
            return Result.failure(IllegalArgumentException("计数型验证码不能交给 MCP"))
        }
        val normalized = normalizeAlias(alias)
            ?: return Result.failure(IllegalArgumentException("别名使用小写字母、数字、点、下划线或短横线"))
        val token = mcpToken ?: return Result.failure(IllegalStateException("请先生成访问令牌"))
        val salt = mcpSalt ?: return Result.failure(IllegalStateException("请先生成访问令牌"))
        val key = deriveMcpKey(token, salt)
        val body = McpEntryRequestJson(
            alias = normalized,
            secretCt = seal(key, userId, entryId, normalized, "secret", entry.secret),
            issuerCt = entry.issuer.takeIf { it.isNotBlank() }?.let {
                seal(key, userId, entryId, normalized, "issuer", it)
            },
            accountCt = entry.name.takeIf { it.isNotBlank() }?.let {
                seal(key, userId, entryId, normalized, "account", it)
            },
            hostCt = entry.host.takeIf { it.isNotBlank() }?.let {
                seal(key, userId, entryId, normalized, "host", it)
            },
            algorithm = entry.algorithm.name,
            digits = entry.digits,
            period = entry.period,
            steam = entryKind(entry.kind, entry.digits) == AuthenticatorKind.STEAM,
        )
        return service.putMcpEntry(SOURCE, entryId, body).onSuccess { refresh() }.map { }
    }

    /**
     * Clears the one-time token from the screen state. The plaintext is not stored.
     */
    fun dismissRevealedToken() {
        mutableMcp.value = mutableMcp.value.copy(revealedToken = null)
    }

    private suspend fun issueToken(rotate: Boolean): Result<String> {
        val userId = activeUnlockedUserId()
            ?: return Result.failure(IllegalStateException("请先解锁"))
        mutableMcp.value = mutableMcp.value.copy(busy = true, message = null)
        val previousToken = mcpToken
        val previousSalt = mcpSalt
        val issued = if (rotate) {
            service.rotateMcpToken(LABEL)
        } else {
            service.createMcpToken(LABEL)
        }
        return issued.fold(
            onSuccess = { token ->
                storeIssuedToken(userId, token)
                if (rotate && previousToken != null && previousSalt != null) {
                    rewrap(userId, token)
                }
                refresh()
                Result.success(token.token)
            },
            onFailure = { error ->
                mutableMcp.value = mutableMcp.value.copy(
                    busy = false,
                    message = if (error.httpStatusCode() == NOT_FOUND) {
                        "服务器没有打开 MCP。"
                    } else {
                        "访问令牌没有生成。"
                    },
                )
                Result.failure(error)
            },
        )
    }

    private suspend fun storeIssuedToken(userId: String, issued: McpIssuedTokenJson) {
        mcpToken = issued.token
        mcpSalt = issued.keySalt
        val wrapped = encryptText(userId, issued.token)
        service.putMcpTokenEnc(wrapped.text)
        mutableMcp.value = mutableMcp.value.copy(
            configured = true,
            revealedToken = issued.token,
            busy = false,
            message = null,
        )
    }

    private suspend fun rewrap(
        userId: String,
        issued: McpIssuedTokenJson,
    ) {
        val summaries = service.listMcpEntries().getOrNull().orEmpty()
        if (summaries.isEmpty()) return
        val newKey = deriveMcpKey(issued.token, issued.keySalt)
        summaries.forEach { summary ->
            rewrapOne(userId, newKey, summary)
        }
    }

    private suspend fun rewrapOne(
        userId: String,
        newKey: ByteArray,
        summary: McpEntrySummaryJson,
    ) {
        val entry = find(summary.sourceId) ?: return
        val alias = summary.alias
        val body = McpEntryRequestJson(
            alias = alias,
            secretCt = seal(newKey, userId, summary.sourceId, alias, "secret", entry.secret),
            issuerCt = entry.issuer.takeIf { it.isNotBlank() }?.let {
                seal(newKey, userId, summary.sourceId, alias, "issuer", it)
            },
            accountCt = entry.name.takeIf { it.isNotBlank() }?.let {
                seal(newKey, userId, summary.sourceId, alias, "account", it)
            },
            hostCt = entry.host.takeIf { it.isNotBlank() }?.let {
                seal(newKey, userId, summary.sourceId, alias, "host", it)
            },
            algorithm = summary.algorithm,
            digits = summary.digits,
            period = summary.period,
            steam = summary.steam,
        )
        service.putMcpEntry(summary.source, summary.sourceId, body)
    }

    private fun seal(
        key: ByteArray,
        userId: String,
        sourceId: String,
        alias: String,
        field: String,
        value: String,
    ): String {
        val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        return sealField(
            key = key,
            aad = mcpAad(userId, SOURCE, sourceId, alias, field),
            plaintext = value,
            nonce = nonce,
        )
    }

    private suspend fun refreshMcpLocked(userId: String) {
        val status = service.mcpStatus()
        status.fold(
            onSuccess = { body ->
                mcpSalt = body.keySalt
                mcpToken = body.tokenEnc?.let {
                    decryptText(userId, it).text.takeIf { text -> text.isNotEmpty() }
                }
                val aliases = service.listMcpEntries().getOrNull().orEmpty()
                    .filter { it.source == SOURCE }
                    .associate { it.sourceId to it.alias }
                mutableMcp.value = mutableMcp.value.copy(
                    supported = true,
                    configured = body.configured,
                    revoked = body.revoked,
                    label = body.label,
                    busy = false,
                    message = null,
                )
                mutableEntries.value = mutableEntries.value.copy(mcpAliases = aliases)
            },
            onFailure = { error ->
                mutableMcp.value = if (error.httpStatusCode() == NOT_FOUND) {
                    McpAccessState(supported = false, message = "服务器没有打开 MCP。")
                } else {
                    mutableMcp.value.copy(busy = false, message = "MCP 状态没有读到。")
                }
            },
        )
    }

    private suspend fun mutate(
        entryId: String?,
        draft: AuthenticatorDraft,
        sortOrder: Int,
    ): Result<Unit> {
        val userId = activeUnlockedUserId()
            ?: return Result.failure(IllegalStateException("请先解锁"))
        if (!mutableEntries.value.editable && mutableEntries.value.entries.isNotEmpty()) {
            return Result.failure(IllegalStateException("离线时不能修改"))
        }
        val body = toRequest(
            userId = userId,
            draft = draft,
            sortOrder = sortOrder,
            clientId = if (entryId.isNullOrBlank()) {
                java.util.UUID.randomUUID().toString()
            } else {
                null
            },
            revisionDate = draft.baseRevision ?: entryId?.let { find(it)?.revisionDate },
        )
        val result = if (entryId.isNullOrBlank()) {
            service.createEntry(body)
        } else {
            service.updateEntry(entryId, body)
        }
        return result.fold(
            onSuccess = {
                refresh()
                Result.success(Unit)
            },
            onFailure = { error ->
                if (error.httpStatusCode() == CONFLICT) {
                    refresh()
                    Result.failure(IllegalStateException("这条验证码刚在别的设备上改过，已重新加载，没有覆盖。"))
                } else if (isOffline(error)) {
                    Result.failure(IllegalStateException("当前离线，没有保存。"))
                } else {
                    Result.failure(error)
                }
            },
        )
    }

    private suspend fun publishRows(
        userId: String,
        rows: List<AuthenticatorEntryJson>,
        status: AuthenticatorSyncStatus,
        message: String?,
    ) {
        val views = rows.map { decryptRow(userId, it) }
        val syncedAt = if (status == AuthenticatorSyncStatus.READY) {
            System.currentTimeMillis()
        } else {
            mutableEntries.value.syncedAtMillis
        }
        mutableEntries.value = AuthenticatorSnapshot(
            status = status,
            entries = views,
            recentIds = diskSource.readRecent(userId),
            mcpAliases = mutableEntries.value.mcpAliases,
            message = message,
            editable = status == AuthenticatorSyncStatus.READY,
            syncedAtMillis = syncedAt,
        )
    }

    private suspend fun decryptRow(
        userId: String,
        row: AuthenticatorEntryJson,
    ): AuthenticatorEntryView {
        val name = decryptText(userId, row.name)
        val secret = decryptText(userId, row.secret)
        val issuer = decryptText(userId, row.issuer)
        val notes = decryptText(userId, row.notes)
        val failed = name.failed || secret.failed
        val split = splitAuthenticatorNotes(notes.text)
        val kind = kindFromStored(split.meta, split.hasTrailer, row.digits)
        return AuthenticatorEntryView(
            id = row.id,
            name = if (failed) "无法解密" else name.text,
            issuer = issuer.text,
            secret = secret.text,
            algorithm = normalizeAlgorithm(row.algorithm),
            digits = row.digits,
            period = row.period,
            notes = split.text,
            favorite = row.favorite,
            sortOrder = row.sortOrder,
            revisionDate = row.revisionDate,
            creationDate = row.creationDate,
            undecryptable = failed,
            kind = kind,
            host = split.meta.host,
            tags = split.meta.tags,
            counter = split.meta.counter,
            deletedAt = split.meta.deletedAt,
        )
    }

    private suspend fun toRequest(
        userId: String,
        draft: AuthenticatorDraft,
        sortOrder: Int,
        clientId: String?,
        revisionDate: String?,
    ): AuthenticatorEntryRequestJson {
        val kind = entryKind(draft.kind, draft.digits)
        val digits = if (kind == AuthenticatorKind.STEAM) STEAM_DIGITS else draft.digits
        val meta = AuthenticatorMeta(
            kind = kind,
            host = draft.host.trim(),
            tags = draft.tags,
            counter = draft.counter,
            deletedAt = draft.deletedAt,
        )
        val notes = joinAuthenticatorNotes(draft.notes, meta, digits)
        val name = draft.name.ifBlank { draft.issuer.ifBlank { "验证码" } }
        return AuthenticatorEntryRequestJson(
            name = encryptText(userId, name).text,
            issuer = draft.issuer.trim().takeIf { it.isNotEmpty() }
                ?.let { encryptText(userId, it).text },
            secret = encryptText(userId, normalizeTotpSecret(draft.secret)).text,
            algorithm = draft.algorithm.name,
            digits = digits,
            period = draft.period.coerceAtLeast(1),
            notes = notes.takeIf { it.isNotEmpty() }?.let { encryptText(userId, it).text },
            favorite = draft.favorite,
            sortOrder = sortOrder,
            id = clientId,
            revisionDate = revisionDate?.takeIf { it.isNotBlank() },
        )
    }

    private fun isOffline(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is java.io.IOException) return true
            current = current.cause
        }
        return false
    }

    private suspend fun encryptText(userId: String, value: String): DecryptedText {
        if (value.isEmpty()) return DecryptedText("", false)
        val encrypted = vaultSdkSource.encryptFolder(
            userId = userId,
            folder = FolderView(id = null, name = value, revisionDate = Instant.EPOCH),
        )
        return encrypted.fold(
            onSuccess = { DecryptedText(it.name, false) },
            onFailure = { throw it },
        )
    }

    private suspend fun decryptText(userId: String, value: String?): DecryptedText {
        if (value.isNullOrBlank()) return DecryptedText("", false)
        return vaultSdkSource.decryptFolder(
            userId = userId,
            folder = Folder(id = null, name = value, revisionDate = Instant.EPOCH),
        ).fold(
            onSuccess = { DecryptedText(it.name, false) },
            onFailure = { DecryptedText("", true) },
        )
    }

    private fun find(entryId: String): AuthenticatorEntryView? =
        mutableEntries.value.entries.firstOrNull { it.id == entryId }

    private fun activeUnlockedUserId(): String? {
        val userId = authRepository.userStateFlow.value?.activeUserId ?: return null
        return userId.takeIf { vaultLockManager.isVaultUnlocked(userId) }
    }

    private fun clearSecrets() {
        mcpToken = null
        mcpSalt = null
        mutableEntries.value = AuthenticatorSnapshot(status = AuthenticatorSyncStatus.LOCKED)
        mutableMcp.value = McpAccessState()
    }

    private fun AuthenticatorDraft.sortOrderOrZero(entryId: String?): Int {
        if (entryId.isNullOrBlank()) return mutableEntries.value.entries.size
        return find(entryId)?.sortOrder ?: 0
    }

    private fun AuthenticatorEntryView.toDraft(
        deletedAt: String? = this.deletedAt,
    ): AuthenticatorDraft =
        AuthenticatorDraft(
            name = name,
            issuer = issuer,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            notes = notes,
            favorite = favorite,
            kind = kind,
            host = host,
            tags = tags,
            counter = counter,
            deletedAt = deletedAt,
        )

    private data class DecryptedText(val text: String, val failed: Boolean)

    private companion object {
        const val SOURCE: String = "authenticator"
        const val LABEL: String = "android"
        const val NOT_FOUND: Int = 404
        const val CONFLICT: Int = 409
        const val NONCE_SIZE: Int = 12
    }
}
