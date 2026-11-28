package com.x8bit.bitwarden.ui.authenticator

import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.data.authenticator.group.AuthenticatorSection
import com.x8bit.bitwarden.data.authenticator.group.authenticatorSections
import com.x8bit.bitwarden.data.authenticator.group.isGroupCatalog
import com.x8bit.bitwarden.data.authenticator.group.notesExtrasWithGroup
import com.x8bit.bitwarden.data.authenticator.group.readGroupCatalog
import com.x8bit.bitwarden.data.authenticator.match.AuthenticatorSort
import com.x8bit.bitwarden.data.authenticator.match.filterAuthenticatorEntries
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.order.TotpOrderSlot
import com.x8bit.bitwarden.data.authenticator.order.ordersAfterMove
import com.x8bit.bitwarden.data.authenticator.order.ordersForInsert
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
@Suppress("TooManyFunctions")
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
        collapsed = repository.readCollapsed()
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
            is AuthenticatorListAction.MoveTo -> moveWithinGroup(action.id, action.toIndex)
            is AuthenticatorListAction.PlaceInGroup -> {
                placeInGroup(action.id, action.groupId, action.toIndex)
            }
            is AuthenticatorListAction.ToggleCollapsed -> toggleCollapsed(action.sectionId)
            is AuthenticatorListAction.ShowAssign -> {
                mutableStateFlow.update {
                    it.copy(
                        dialog = AuthenticatorListState.Dialog.AssignGroup(action.id, action.name),
                    )
                }
            }
            is AuthenticatorListAction.AssignGroup -> {
                viewModelScope.launch {
                    repository.setEntryGroup(action.id, action.groupId).onFailure { error ->
                        sendAction(
                            AuthenticatorListAction.ShowMessage(error.message ?: "没有保存分组"),
                        )
                    }
                }
            }
            is AuthenticatorListAction.GroupSelected -> {
                mutableStateFlow.update { it.copy(group = action.group) }
                rebuild()
            }
            is AuthenticatorListAction.CopyClick -> copy(action.id)
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

    private var collapsed: Set<String> = emptySet()

    private fun rebuild(finishedRefresh: Boolean = false) {
        val now = clock.millis()
        val named = readGroupCatalog(snapshot.entries).second
        val source = if (trash) {
            snapshot.entries.filter { it.deletedAt != null && !isGroupCatalog(it) }
        } else {
            filterAuthenticatorEntries(
                entries = snapshot.entries.filter { !isGroupCatalog(it) },
                query = state.query,
                favoritesOnly = false,
                sort = AuthenticatorSort.MANUAL,
                group = state.group,
            )
        }
        val sections = if (trash) {
            authenticatorSections(source, emptyList(), emptySet(), searching = false)
        } else {
            authenticatorSections(
                entries = source,
                groups = named,
                collapsedIds = collapsed,
                searching = state.query.isNotBlank(),
            )
        }
        val rows = sections.flatMap { section ->
            section.entries.mapNotNull { entry ->
                runCatching {
                    entry.toRow(now, snapshot.mcpAliases[entry.id], section.id.ifEmpty { null })
                }.getOrNull()
            }
        }
        mutableStateFlow.update {
            it.copy(
                status = snapshot.status,
                message = snapshot.message,
                editable = snapshot.editable,
                rows = visibleRows(sections, rows),
                lines = sectionLines(sections, rows),
                groupChoices = named.map { group -> group.id to group.name },
                recentIds = snapshot.recentIds,
                refreshing = if (finishedRefresh) false else it.refreshing,
                syncedAtMillis = snapshot.syncedAtMillis ?: it.syncedAtMillis,
                sort = AuthenticatorSort.MANUAL,
            )
        }
    }

    private fun sectionLines(
        sections: List<AuthenticatorSection>,
        rows: List<AuthenticatorRow>,
    ): List<AuthenticatorLine> = sections.flatMap { section ->
        val sectionRows = rows.filter { row -> section.entries.any { it.id == row.id } }
        val header = if (section.title.isNotEmpty()) {
            listOf(
                AuthenticatorLine.Header(
                    sectionId = section.id,
                    title = section.title,
                    count = section.entries.size,
                    collapsed = section.collapsed,
                ),
            )
        } else {
            emptyList()
        }
        val visible = if (section.collapsed) emptyList() else sectionRows
        header + visible.map { AuthenticatorLine.Code(it) }
    }

    private fun visibleRows(
        sections: List<AuthenticatorSection>,
        rows: List<AuthenticatorRow>,
    ): List<AuthenticatorRow> = rows.filter { row ->
        sections.any { section ->
            !section.collapsed && section.entries.any { entry -> entry.id == row.id }
        }
    }

    private fun moveWithinGroup(id: String, toIndex: Int) {
        if (trash || state.query.isNotBlank()) return
        val section = liveSections().firstOrNull { section ->
            section.entries.any { it.id == id }
        } ?: return
        val slots = section.entries.map { TotpOrderSlot(it.id, it.order, it.name) }
        val orders = ordersAfterMove(slots, id, toIndex)
        if (orders.isEmpty()) return
        applyLocalOrders(orders)
        viewModelScope.launch {
            repository.applyOrders(orders).onFailure { error ->
                sendAction(AuthenticatorListAction.ShowMessage(error.message ?: "没有保存顺序"))
            }
        }
    }

    private fun placeInGroup(id: String, groupId: String?, toIndex: Int) {
        if (trash || state.query.isNotBlank()) return
        val moved = snapshot.entries.firstOrNull { it.id == id } ?: return
        val destination = liveSections()
            .firstOrNull { it.id == groupId.orEmpty() }
            ?.entries
            .orEmpty()
            .filter { it.id != id }
        val orders = ordersForInsert(
            destination.map { TotpOrderSlot(it.id, it.order, it.name) },
            TotpOrderSlot(moved.id, moved.order, moved.name),
            toIndex,
        )
        snapshot = snapshot.copy(
            entries = snapshot.entries.map { entry ->
                when (entry.id) {
                    id -> entry.copy(
                        order = orders[id] ?: entry.order,
                        extras = notesExtrasWithGroup(entry.extras, groupId),
                    )
                    else -> orders[entry.id]?.let { entry.copy(order = it) } ?: entry
                }
            },
        )
        rebuild()
        viewModelScope.launch {
            repository.placeInGroup(id, groupId, orders).onFailure { error ->
                sendAction(AuthenticatorListAction.ShowMessage(error.message ?: "没有保存分组"))
            }
        }
    }

    private fun liveSections() = authenticatorSections(
        entries = filterAuthenticatorEntries(
            entries = snapshot.entries.filter { !isGroupCatalog(it) },
            query = "",
            favoritesOnly = false,
            sort = AuthenticatorSort.MANUAL,
            group = null,
        ),
        groups = readGroupCatalog(snapshot.entries).second,
        collapsedIds = emptySet(),
        searching = false,
    )

    private fun applyLocalOrders(orders: Map<String, String>) {
        snapshot = snapshot.copy(
            entries = snapshot.entries.map { entry ->
                orders[entry.id]?.let { entry.copy(order = it) } ?: entry
            },
        )
        rebuild()
    }

    private fun toggleCollapsed(sectionId: String) {
        collapsed = if (sectionId in collapsed) collapsed - sectionId else collapsed + sectionId
        repository.writeCollapsed(collapsed)
        rebuild()
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

    private fun AuthenticatorEntryView.toRow(
        now: Long,
        alias: String?,
        groupId: String?,
    ): AuthenticatorRow {
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
            groupId = groupId,
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
    val groupId: String? = null,
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
    val status: AuthenticatorSyncStatus = AuthenticatorSyncStatus.LOADING,
    val message: String? = null,
    val editable: Boolean = false,
    val rows: List<AuthenticatorRow> = emptyList(),
    val lines: List<AuthenticatorLine> = emptyList(),
    val groupChoices: List<Pair<String, String>> = emptyList(),
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

        /**
         * Pick a group for [name]. Null in the result means 未分组.
         */
        data class AssignGroup(val id: String, val name: String) : Dialog()
    }
}

/**
 * One row in the authenticator list, either a group header or a code.
 */
sealed class AuthenticatorLine {
    /**
     * A collapsible group. An empty [sectionId] is 未分组.
     */
    data class Header(
        val sectionId: String,
        val title: String,
        val count: Int,
        val collapsed: Boolean,
    ) : AuthenticatorLine()

    /**
     * A verification code.
     */
    data class Code(val row: AuthenticatorRow) : AuthenticatorLine()
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
     * Moves [id] into [groupId] (null is 未分组) at [toIndex] inside that group.
     */
    data class PlaceInGroup(
        val id: String,
        val groupId: String?,
        val toIndex: Int,
    ) : AuthenticatorListAction()

    /**
     * Collapses or expands one section on this device.
     */
    data class ToggleCollapsed(val sectionId: String) : AuthenticatorListAction()

    /**
     * Stores [groupId] on the entry. Null clears it.
     */
    data class AssignGroup(val id: String, val groupId: String?) : AuthenticatorListAction()

    /**
     * Opens the group picker for one entry.
     */
    data class ShowAssign(val id: String, val name: String) : AuthenticatorListAction()

    /**
     * Group filter changed. Null shows every group.
     */
    data class GroupSelected(val group: String?) : AuthenticatorListAction()

    /**
     * Copy the code for [id].
     */
    data class CopyClick(val id: String) : AuthenticatorListAction()

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
