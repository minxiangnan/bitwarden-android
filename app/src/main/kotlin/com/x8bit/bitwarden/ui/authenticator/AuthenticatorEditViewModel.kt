package com.x8bit.bitwarden.ui.authenticator

import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.data.authenticator.meta.parseTagList
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorAlgorithm
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorDraft
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.otp.isValidTotpSecret
import com.x8bit.bitwarden.data.authenticator.otp.parseOtpAuthUri
import com.x8bit.bitwarden.data.authenticator.otp.toDraft
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Add or edit one authenticator entry.
 */
@HiltViewModel(assistedFactory = AuthenticatorEditViewModel.Factory::class)
class AuthenticatorEditViewModel @AssistedInject constructor(
    @Assisted private val entryId: String,
    private val repository: AuthenticatorRepository,
) : BaseViewModel<AuthenticatorEditState, AuthenticatorEditEvent, AuthenticatorEditAction>(
    initialState = AuthenticatorEditState(entryId = entryId),
) {
    /**
     * Creates the edit view model. An empty [entryId] means add.
     */
    @AssistedFactory
    interface Factory {
        /**
         * Creates the view model for [entryId].
         */
        fun create(entryId: String): AuthenticatorEditViewModel
    }

    init {
        val stashed = if (entryId.isBlank()) repository.consumeStash() else null
        if (stashed != null) {
            mutableStateFlow.update { stashed.toState(entryId) }
        }
        repository.entries
            .onEach { snapshot ->
                sendAction(
                    AuthenticatorEditAction.Internal.Entries(
                        snapshot.entries,
                        snapshot.mcpAliases,
                    ),
                )
            }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: AuthenticatorEditAction) {
        when (action) {
            is AuthenticatorEditAction.SecretChanged -> update { it.copy(secret = action.value) }
            is AuthenticatorEditAction.NameChanged -> update { it.copy(name = action.value) }
            is AuthenticatorEditAction.IssuerChanged -> update { it.copy(issuer = action.value) }
            is AuthenticatorEditAction.HostChanged -> update { it.copy(host = action.value) }
            is AuthenticatorEditAction.TagsChanged -> update { it.copy(tags = action.value) }
            is AuthenticatorEditAction.NotesChanged -> update { it.copy(notes = action.value) }
            is AuthenticatorEditAction.DigitsChanged -> update { it.copy(digits = action.value) }
            is AuthenticatorEditAction.PeriodChanged -> update { it.copy(period = action.value) }
            is AuthenticatorEditAction.AlgorithmChanged -> {
                update { it.copy(algorithm = action.value) }
            }
            is AuthenticatorEditAction.SteamChanged -> update { it.copy(steam = action.value) }
            is AuthenticatorEditAction.HotpChanged -> update { it.copy(hotp = action.value) }
            is AuthenticatorEditAction.McpChanged -> update { it.copy(mcpEnabled = action.value) }
            is AuthenticatorEditAction.AliasChanged -> update { it.copy(alias = action.value) }
            is AuthenticatorEditAction.PasteChanged -> applyPaste(action.value)
            AuthenticatorEditAction.SaveClick -> save()
            AuthenticatorEditAction.DeleteClick -> {
                val name = state.name.ifBlank { state.issuer.ifBlank { "这条验证码" } }
                update { it.copy(confirmDelete = true, displayName = name) }
            }
            AuthenticatorEditAction.ConfirmDelete -> confirmDelete()
            AuthenticatorEditAction.DismissDialog -> {
                update { it.copy(confirmDelete = false, message = null) }
            }
            is AuthenticatorEditAction.ShowMessage -> update { it.copy(message = action.text) }
            is AuthenticatorEditAction.Internal.Entries -> applyEntries(action)
        }
    }

    private var loaded = false

    private fun applyEntries(action: AuthenticatorEditAction.Internal.Entries) {
        if (entryId.isBlank() || loaded) {
            val alias = action.aliases[entryId]
            if (alias != null) {
                update { it.copy(alias = it.alias.ifBlank { alias }, mcpEnabled = true) }
            }
            return
        }
        val entry = action.entries.firstOrNull { it.id == entryId } ?: return
        loaded = true
        update {
            it.copy(
                secret = entry.secret,
                name = entry.name,
                issuer = entry.issuer,
                host = entry.host,
                tags = entry.tags.joinToString(", "),
                notes = entry.notes,
                digits = entry.digits.toString(),
                period = entry.period.toString(),
                algorithm = entry.algorithm,
                steam = entry.kind == AuthenticatorKind.STEAM,
                hotp = entry.kind == AuthenticatorKind.HOTP,
                alias = action.aliases[entryId].orEmpty(),
                mcpEnabled = action.aliases.containsKey(entryId),
                editable = !entry.undecryptable,
                baseRevision = entry.revisionDate,
            )
        }
    }

    private fun applyPaste(value: String) {
        val parsed = parseOtpAuthUri(value.trim())
        if (parsed == null) {
            update { it.copy(message = "这不是 otpauth 链接") }
            return
        }
        val draft = parsed.toDraft()
        update {
            it.copy(
                secret = draft.secret,
                name = draft.name,
                issuer = draft.issuer,
                digits = draft.digits.toString(),
                period = draft.period.toString(),
                algorithm = draft.algorithm,
                steam = draft.kind == AuthenticatorKind.STEAM,
                hotp = draft.kind == AuthenticatorKind.HOTP,
                message = null,
            )
        }
    }

    private fun save() {
        val secret = state.secret
        if (!isValidTotpSecret(secret)) {
            update { it.copy(message = "密钥至少要有 8 位，并且是 Base32") }
            return
        }
        val kind = when {
            state.steam -> AuthenticatorKind.STEAM
            state.hotp -> AuthenticatorKind.HOTP
            else -> AuthenticatorKind.TOTP
        }
        val draft = AuthenticatorDraft(
            name = state.name.trim(),
            issuer = state.issuer.trim(),
            secret = secret,
            algorithm = state.algorithm,
            digits = state.digits.toIntOrNull() ?: 6,
            period = state.period.toIntOrNull() ?: 30,
            notes = state.notes,
            kind = kind,
            host = state.host.trim(),
            tags = parseTagList(state.tags),
            baseRevision = state.baseRevision.takeIf { it.isNotBlank() },
        )
        viewModelScope.launch {
            val saved = repository.save(entryId.takeIf { it.isNotBlank() }, draft)
            saved.onFailure { error ->
                sendAction(AuthenticatorEditAction.ShowMessage(error.message ?: "没有保存"))
                return@launch
            }
            val id = entryId.takeIf { it.isNotBlank() }
                ?: repository.entries.value.entries.maxByOrNull { it.creationDate }?.id
            if (id != null && (state.mcpEnabled || state.alias.isNotBlank())) {
                repository.setMcp(id, state.mcpEnabled, state.alias).onFailure { error ->
                    sendAction(AuthenticatorEditAction.ShowMessage(error.message ?: "MCP 没有更新"))
                    return@launch
                }
                // The create notification can arrive before the alias is stored. Touch the entry
                // afterwards so other devices reload and see the alias.
                repository.save(id, draft.copy(baseRevision = null)).onFailure { error ->
                    sendAction(AuthenticatorEditAction.ShowMessage(error.message ?: "没有保存"))
                    return@launch
                }
            }
            sendEvent(AuthenticatorEditEvent.Saved)
        }
    }

    private fun confirmDelete() {
        update { it.copy(confirmDelete = false) }
        val id = entryId.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            repository.moveToTrash(id).fold(
                onSuccess = { sendEvent(AuthenticatorEditEvent.Saved) },
                onFailure = { error ->
                    sendAction(AuthenticatorEditAction.ShowMessage(error.message ?: "没有删除"))
                },
            )
        }
    }

    private fun update(block: (AuthenticatorEditState) -> AuthenticatorEditState) {
        mutableStateFlow.update(block)
    }
}

private fun AuthenticatorDraft.toState(entryId: String): AuthenticatorEditState =
    AuthenticatorEditState(
        entryId = entryId,
        secret = secret,
        name = name,
        issuer = issuer,
        host = host,
        tags = tags.joinToString(", "),
        notes = notes,
        digits = digits.toString(),
        period = period.toString(),
        algorithm = algorithm,
        steam = kind == AuthenticatorKind.STEAM,
        hotp = kind == AuthenticatorKind.HOTP,
    )

/**
 * Edit form state.
 */
data class AuthenticatorEditState(
    val entryId: String = "",
    val secret: String = "",
    val name: String = "",
    val issuer: String = "",
    val host: String = "",
    val tags: String = "",
    val notes: String = "",
    val digits: String = "6",
    val period: String = "30",
    val algorithm: AuthenticatorAlgorithm = AuthenticatorAlgorithm.SHA1,
    val steam: Boolean = false,
    val hotp: Boolean = false,
    val mcpEnabled: Boolean = false,
    val baseRevision: String = "",
    val alias: String = "",
    val editable: Boolean = true,
    val confirmDelete: Boolean = false,
    val displayName: String = "",
    val message: String? = null,
) {
    /**
     * True when editing an existing entry.
     */
    val isExisting: Boolean get() = entryId.isNotBlank()
}

/**
 * Edit events.
 */
sealed class AuthenticatorEditEvent {
    /**
     * The entry was saved or deleted.
     */
    data object Saved : AuthenticatorEditEvent()
}

/**
 * Edit actions.
 */
sealed class AuthenticatorEditAction {
    /** Secret field. */
    data class SecretChanged(val value: String) : AuthenticatorEditAction()
    /** Name field. */
    data class NameChanged(val value: String) : AuthenticatorEditAction()
    /** Issuer field. */
    data class IssuerChanged(val value: String) : AuthenticatorEditAction()
    /** Host field. */
    data class HostChanged(val value: String) : AuthenticatorEditAction()
    /** Tags field. */
    data class TagsChanged(val value: String) : AuthenticatorEditAction()
    /** Notes field. */
    data class NotesChanged(val value: String) : AuthenticatorEditAction()
    /** Digits field. */
    data class DigitsChanged(val value: String) : AuthenticatorEditAction()
    /** Period field. */
    data class PeriodChanged(val value: String) : AuthenticatorEditAction()
    /** Algorithm field. */
    data class AlgorithmChanged(val value: AuthenticatorAlgorithm) : AuthenticatorEditAction()
    /** Steam toggle. */
    data class SteamChanged(val value: Boolean) : AuthenticatorEditAction()
    /** HOTP toggle. */
    data class HotpChanged(val value: Boolean) : AuthenticatorEditAction()
    /** MCP opt-in toggle. */
    data class McpChanged(val value: Boolean) : AuthenticatorEditAction()
    /** MCP alias. */
    data class AliasChanged(val value: String) : AuthenticatorEditAction()
    /** Pasted otpauth URI. */
    data class PasteChanged(val value: String) : AuthenticatorEditAction()
    /** Save. */
    data object SaveClick : AuthenticatorEditAction()
    /** Ask to delete. */
    data object DeleteClick : AuthenticatorEditAction()
    /** Confirm delete. */
    data object ConfirmDelete : AuthenticatorEditAction()
    /** Dismiss a dialog. */
    data object DismissDialog : AuthenticatorEditAction()
    /** Show a message. */
    data class ShowMessage(val text: String) : AuthenticatorEditAction()

    /**
     * Internal updates.
     */
    sealed class Internal : AuthenticatorEditAction() {
        /**
         * Latest entries and MCP aliases.
         */
        data class Entries(
            val entries: List<com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView>,
            val aliases: Map<String, String>,
        ) : Internal()
    }
}
