package com.x8bit.bitwarden.ui.authenticator

import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.data.authenticator.match.AuthenticatorSort
import com.x8bit.bitwarden.data.authenticator.match.filterAuthenticatorEntries
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.order.TotpOrderSlot
import com.x8bit.bitwarden.data.authenticator.order.ordersAfterMove
import com.x8bit.bitwarden.data.authenticator.otp.currentCode
import com.x8bit.bitwarden.data.authenticator.otp.formatAuthenticatorCode
import com.x8bit.bitwarden.data.authenticator.otp.entryKind
import com.x8bit.bitwarden.data.authenticator.otp.nextTotpCode
import com.x8bit.bitwarden.data.authenticator.otp.secondsRemaining
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorSnapshot
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorSyncStatus
import com.x8bit.bitwarden.data.platform.manager.clipboard.BitwardenClipboardManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Authenticator list, including trash when [trash] is true.
 */
@HiltViewModel(assistedFactory = AuthenticatorListViewModel.Factory::class)
class AuthenticatorListViewModel @AssistedInject constructor(
    @Assisted private val trash: Boolean,
    private val repository: AuthenticatorRepository,
    private val clipboardManager: BitwardenClipboardManager,
    private val clock: Clock,
) : BaseViewModel<AuthenticatorListState, AuthenticatorListEvent, AuthenticatorListAction>(
    initialState = AuthenticatorListState(isTrash = trash),
) {
    /**
     * Creates a list or trash view model.
     */
    @AssistedFactory
    interface Factory {
        /**
         * Creates the view model for the list or the trash.
         */
        fun create(trash: Boolean): AuthenticatorListViewModel
    }

    /**
     * Live repository snapshot. The screen applies it while composed, because the
     * tab's first composition can outlive the view model's startup collector.
     */
    val liveEntries: StateFlow<AuthenticatorSnapshot> = repository.entries

    /**
     * Copies [incoming] into the list state.
     */
    fun applySnapshot(incoming: AuthenticatorSnapshot) {
        snapshot = incoming
        rebuild(finishedRefresh = true)
    }

    init {
        viewModelScope.launch {
            repository.scanNotice.collect { message ->
                if (message != null) {
                    repository.consumeScanNotice()
                    sendAction(AuthenticatorListAction.ShowMessage(message))
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                sendAction(AuthenticatorListAction.Internal.Tick)
            }
        }
    }

    /**
     * Plaintext migration document for the current list.
     */
    fun exportDocument(): String = repository.exportJson()

    @Suppress("LongMethod")
    override fun handleAction(action: AuthenticatorListAction) {
        when (action) {
            is AuthenticatorListAction.SearchChanged -> {
                mutableStateFlow.update { it.copy(query = action.query) }
                rebuild()
            }
            is AuthenticatorListAction.SortSelected -> {
                if (state.sort != AuthenticatorSort.MANUAL) {
                    mutableStateFlow.update { it.copy(sort = AuthenticatorSort.MANUAL) }
                    rebuild()
                }
            }
            is AuthenticatorListAction.MoveTo -> {
                val canMove = !trash && state.query.isBlank()
                val slots = if (canMove) {
                    state.rows.map { row ->
                        val entry = snapshot.entries.firstOrNull { it.id == row.id }
                        TotpOrderSlot(
                            id = row.id,
                            order = entry?.order,
                            name = entry?.name ?: row.title,
                        )
                    }
                } else {
                    emptyList()
                }
                val orders = if (canMove) {
                    ordersAfterMove(slots, action.id, action.toIndex)
                } else {
                    emptyMap()
                }
                if (orders.isNotEmpty()) {
                    snapshot = snapshot.copy(
                        entries = snapshot.entries.map { entry ->
                            orders[entry.id]?.let { entry.copy(order = it) } ?: entry
                        },
                    )
                    rebuild()
                    viewModelScope.launch {
                        repository.applyOrders(orders).onFailure { error ->
                            sendAction(
                                AuthenticatorListAction.ShowMessage(
                                    error.message ?: "没有保存顺序",
                                ),
                            )
                        }
                    }
                }
            }
            is AuthenticatorListAction.GroupSelected -> {
                mutableStateFlow.update { it.copy(group = action.group) }
                rebuild()
            }
            AuthenticatorListAction.FavoritesToggled -> {
                mutableStateFlow.update { it.copy(favoritesOnly = !it.favoritesOnly) }
                rebuild()
            }
            is AuthenticatorListAction.CopyClick -> copy(action.id)
            is AuthenticatorListAction.FavoriteClick -> {
                viewModelScope.launch {
                    repository.setFavorite(action.id, !action.favorite).onFailure { error ->
                        sendAction(
                            AuthenticatorListAction.ShowMessage(error.message ?: "没有更新收藏"),
                        )
                    }
                }
            }
            is AuthenticatorListAction.TrashClick -> {
                mutableStateFlow.update {
                    it.copy(
                        dialog = AuthenticatorListState.Dialog.ConfirmTrash(
                            action.id,
                            action.name,
                        ),
                    )
                }
            }
            is AuthenticatorListAction.PermanentDeleteClick -> {
                mutableStateFlow.update {
                    it.copy(
                        dialog = AuthenticatorListState.Dialog.ConfirmDelete(
                            action.id,
                            action.name,
                        ),
                    )
                }
            }
            is AuthenticatorListAction.ConfirmTrash -> confirmTrash(action.id)
            is AuthenticatorListAction.ConfirmDelete -> confirmDelete(action.id)
            is AuthenticatorListAction.RestoreClick -> restore(action.id)
            is AuthenticatorListAction.ImportText -> import(action.text)
            AuthenticatorListAction.DismissDialog -> {
                mutableStateFlow.update { it.copy(dialog = null) }
            }
            is AuthenticatorListAction.ShowMessage -> {
                sendEvent(AuthenticatorListEvent.Notice(action.text))
            }
            AuthenticatorListAction.Refresh -> {
                mutableStateFlow.update { it.copy(refreshing = true) }
                viewModelScope.launch { repository.refresh() }
            }
            is AuthenticatorListAction.Internal.Snapshot -> {
                snapshot = action.snapshot
                rebuild()
            }
            AuthenticatorListAction.Internal.Tick -> rebuild()
            is AuthenticatorListAction.Internal.CodeCopied -> {
                val nonce = state.copiedNonce + 1
                mutableStateFlow.update { it.copy(copiedId = action.id, copiedNonce = nonce) }
                clearCopiedJob?.cancel()
                clearCopiedJob = viewModelScope.launch {
                    delay(COPY_FEEDBACK_MS)
                    sendAction(AuthenticatorListAction.Internal.ClearCopied(nonce))
                }
            }
            is AuthenticatorListAction.Internal.ClearCopied -> {
                mutableStateFlow.update { current ->
                    if (current.copiedNonce == action.nonce) {
                        current.copy(copiedId = null)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private var snapshot: AuthenticatorSnapshot = AuthenticatorSnapshot()

    private fun rebuild(finishedRefresh: Boolean = false) {
        val now = clock.millis()
        val source = if (trash) {
            snapshot.entries.filter { it.deletedAt != null }
        } else {
            filterAuthenticatorEntries(
                entries = snapshot.entries,
                query = state.query,
                favoritesOnly = state.favoritesOnly,
                sort = AuthenticatorSort.MANUAL,
                group = state.group,
            )
        }
        val rows = source.mapNotNull { entry ->
            runCatching { entry.toRow(now, snapshot.mcpAliases[entry.id]) }.getOrNull()
        }
        val groups = snapshot.entries
            .filter { it.deletedAt == null }
            .flatMap { entry -> listOf(entry.issuer).filter { it.isNotBlank() } + entry.tags }
            .distinct()
            .sorted()
        mutableStateFlow.update {
            it.copy(
                status = snapshot.status,
                message = snapshot.message,
                editable = snapshot.editable,
                rows = rows,
                groups = groups,
                recentIds = snapshot.recentIds,
                refreshing = if (finishedRefresh) false else it.refreshing,
                syncedAtMillis = snapshot.syncedAtMillis ?: it.syncedAtMillis,
                sort = AuthenticatorSort.MANUAL,
            )
        }
    }

    private var clearCopiedJob: Job? = null

    private fun copy(id: String) {
        viewModelScope.launch {
            val code = repository.consumeCode(id, clock.millis()) ?: return@launch
            clipboardManager.setText(
                text = code,
                toastDescriptorOverride = "验证码",
                showToast = false,
            )
            sendAction(AuthenticatorListAction.Internal.CodeCopied(id))
        }
    }

    private fun confirmTrash(id: String) {
        mutableStateFlow.update { it.copy(dialog = null) }
        viewModelScope.launch {
            repository.moveToTrash(id).onFailure { error ->
                sendAction(AuthenticatorListAction.ShowMessage(error.message ?: "没有删除"))
            }
        }
    }

    private fun confirmDelete(id: String) {
        mutableStateFlow.update { it.copy(dialog = null) }
        viewModelScope.launch {
            repository.deleteForever(id).onFailure { error ->
                sendAction(AuthenticatorListAction.ShowMessage(error.message ?: "没有删除"))
            }
        }
    }

    private fun restore(id: String) {
        viewModelScope.launch { repository.restore(id) }
    }

    private fun import(text: String) {
        viewModelScope.launch {
            repository.importText(text).fold(
                onSuccess = { count ->
                    sendAction(AuthenticatorListAction.ShowMessage("已导入 $count 条"))
                },
                onFailure = { error ->
                    sendAction(AuthenticatorListAction.ShowMessage(error.message ?: "没有导入"))
                },
            )
        }
    }

    private fun AuthenticatorEntryView.toRow(now: Long, alias: String?): AuthenticatorRow {
        val kind = entryKind(this.kind, digits)
        val code = currentCode(this, now)
        return AuthenticatorRow(
            id = id,
            title = name.ifBlank { issuer.ifBlank { "验证码" } },
            notes = notes.trim(),
            code = code?.let(::formatAuthenticatorCode).orEmpty(),
            nextCode = if (kind == AuthenticatorKind.HOTP) {
                null
            } else {
                nextTotpCode(this, now)?.let(::formatAuthenticatorCode)
            },
            seconds = if (kind == AuthenticatorKind.HOTP) 0 else secondsRemaining(now, period),
            period = period.coerceAtLeast(1),
            favorite = favorite,
            alias = alias,
            kindLabel = when (kind) {
                AuthenticatorKind.STEAM -> "Steam"
                AuthenticatorKind.HOTP -> "HOTP"
                AuthenticatorKind.TOTP -> ""
            },
        )
    }

    private companion object {
        const val TICK_MS: Long = 1_000
        const val COPY_FEEDBACK_MS: Long = 1_500
    }
}

/**
 * One visible authenticator card.
 */
data class AuthenticatorRow(
    val id: String,
    val title: String,
    val notes: String,
    val code: String,
    val nextCode: String?,
    val seconds: Int,
    val period: Int,
    val favorite: Boolean,
    val alias: String?,
    val kindLabel: String,
)

/**
 * List screen state.
 */
data class AuthenticatorListState(
    val isTrash: Boolean = false,
    val query: String = "",
    val sort: AuthenticatorSort = AuthenticatorSort.MANUAL,
    val group: String? = null,
    val favoritesOnly: Boolean = false,
    val status: AuthenticatorSyncStatus = AuthenticatorSyncStatus.LOADING,
    val message: String? = null,
    val editable: Boolean = false,
    val rows: List<AuthenticatorRow> = emptyList(),
    val groups: List<String> = emptyList(),
    val recentIds: List<String> = emptyList(),
    val refreshing: Boolean = false,
    val syncedAtMillis: Long? = null,
    val copiedId: String? = null,
    val copiedNonce: Int = 0,
    val dialog: Dialog? = null,
) {
    /**
     * Dialogs owned by the list.
     */
    sealed class Dialog {
        /**
         * Confirm moving [name] to the trash.
         */
        data class ConfirmTrash(val id: String, val name: String) : Dialog()

        /**
         * Confirm permanent deletion of [name].
         */
        data class ConfirmDelete(val id: String, val name: String) : Dialog()

        /**
         * A short result message.
         */
        data class Message(val text: String) : Dialog()
    }
}

/**
 * List events.
 */
sealed class AuthenticatorListEvent {
    /**
     * A short result that should not block the list.
     */
    data class Notice(val text: String) : AuthenticatorListEvent()
}

/**
 * List actions.
 */
sealed class AuthenticatorListAction {
    /**
     * Search text changed.
     */
    data class SearchChanged(val query: String) : AuthenticatorListAction()

    /**
     * Sort changed.
     */
    data class SortSelected(val sort: AuthenticatorSort) : AuthenticatorListAction()

    /**
     * Moves [id] to [toIndex] in the custom order. Ignored while searching.
     */
    data class MoveTo(val id: String, val toIndex: Int) : AuthenticatorListAction()

    /**
     * Group filter changed. Null shows every group.
     */
    data class GroupSelected(val group: String?) : AuthenticatorListAction()

    /**
     * Toggles the favorites filter.
     */
    data object FavoritesToggled : AuthenticatorListAction()

    /**
     * Copy the code for [id].
     */
    data class CopyClick(val id: String) : AuthenticatorListAction()

    /**
     * Toggle favorite for [id]. [favorite] is the current value.
     */
    data class FavoriteClick(val id: String, val favorite: Boolean) : AuthenticatorListAction()

    /**
     * Ask to trash [name].
     */
    data class TrashClick(val id: String, val name: String) : AuthenticatorListAction()

    /**
     * Ask to permanently delete [name].
     */
    data class PermanentDeleteClick(val id: String, val name: String) : AuthenticatorListAction()

    /**
     * Confirmed trash.
     */
    data class ConfirmTrash(val id: String) : AuthenticatorListAction()

    /**
     * Confirmed permanent delete.
     */
    data class ConfirmDelete(val id: String) : AuthenticatorListAction()

    /**
     * Restore from trash.
     */
    data class RestoreClick(val id: String) : AuthenticatorListAction()

    /**
     * Import pasted or file text.
     */
    data class ImportText(val text: String) : AuthenticatorListAction()

    /**
     * Dismiss the dialog.
     */
    data object DismissDialog : AuthenticatorListAction()

    /**
     * Show a short message.
     */
    data class ShowMessage(val text: String) : AuthenticatorListAction()

    /**
     * Pull the latest entries.
     */
    data object Refresh : AuthenticatorListAction()

    /**
     * Actions produced inside the view model.
     */
    sealed class Internal : AuthenticatorListAction() {
        /**
         * Repository snapshot arrived.
         */
        data class Snapshot(val snapshot: AuthenticatorSnapshot) : Internal()

        /**
         * One second passed.
         */
        data object Tick : Internal()

        /**
         * [id] was copied. The list shows the top bubble and resets its timer.
         */
        data class CodeCopied(val id: String) : Internal()

        /**
         * Clears the copy bubble when it still belongs to [nonce].
         */
        data class ClearCopied(val nonce: Int) : Internal()
    }
}
