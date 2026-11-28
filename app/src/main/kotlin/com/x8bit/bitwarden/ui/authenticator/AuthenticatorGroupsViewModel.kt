package com.x8bit.bitwarden.ui.authenticator

import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.data.authenticator.group.AuthenticatorGroupRecord
import com.x8bit.bitwarden.data.authenticator.group.readGroupCatalog
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Create, rename, reorder, and delete authenticator groups.
 */
@HiltViewModel
class AuthenticatorGroupsViewModel @Inject constructor(
    private val repository: AuthenticatorRepository,
) : BaseViewModel<AuthenticatorGroupsState, AuthenticatorGroupsEvent, AuthenticatorGroupsAction>(
    initialState = AuthenticatorGroupsState(),
) {
    init {
        repository.entries
            .onEach { snapshot ->
                sendAction(AuthenticatorGroupsAction.Internal.Loaded(snapshot.entries))
            }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: AuthenticatorGroupsAction) {
        when (action) {
            is AuthenticatorGroupsAction.NameChanged -> {
                mutableStateFlow.update { it.copy(name = action.value) }
            }
            AuthenticatorGroupsAction.Create -> create()
            is AuthenticatorGroupsAction.StartRename -> {
                mutableStateFlow.update {
                    it.copy(editingId = action.id, editingName = action.name)
                }
            }
            is AuthenticatorGroupsAction.RenameChanged -> {
                mutableStateFlow.update { it.copy(editingName = action.value) }
            }
            is AuthenticatorGroupsAction.SaveRename -> saveRename(action.id)
            is AuthenticatorGroupsAction.Move -> move(action.id, action.toIndex)
            is AuthenticatorGroupsAction.AskDelete -> {
                mutableStateFlow.update { it.copy(pendingDeleteId = action.id) }
            }
            AuthenticatorGroupsAction.ConfirmDeleteOnce -> {
                mutableStateFlow.update { it.copy(confirmAgain = true) }
            }
            AuthenticatorGroupsAction.ConfirmDelete -> delete()
            AuthenticatorGroupsAction.Dismiss -> {
                mutableStateFlow.update {
                    it.copy(pendingDeleteId = null, confirmAgain = false, message = null)
                }
            }
            is AuthenticatorGroupsAction.Internal.Loaded -> {
                val groups = readGroupCatalog(action.entries).second
                mutableStateFlow.update { it.copy(groups = groups) }
            }
        }
    }

    private fun create() {
        val name = state.name
        viewModelScope.launch {
            repository.createGroup(name).fold(
                onSuccess = { mutableStateFlow.update { it.copy(name = "", message = null) } },
                onFailure = { error ->
                    mutableStateFlow.update { it.copy(message = error.message) }
                },
            )
        }
    }

    private fun saveRename(id: String) {
        val name = state.editingName
        mutableStateFlow.update { it.copy(editingId = null) }
        viewModelScope.launch {
            repository.renameGroup(id, name).onFailure { error ->
                mutableStateFlow.update { it.copy(message = error.message) }
            }
        }
    }

    private fun move(id: String, toIndex: Int) {
        viewModelScope.launch {
            repository.moveGroup(id, toIndex).onFailure { error ->
                mutableStateFlow.update { it.copy(message = error.message) }
            }
        }
    }

    private fun delete() {
        val id = state.pendingDeleteId ?: return
        mutableStateFlow.update { it.copy(pendingDeleteId = null, confirmAgain = false) }
        viewModelScope.launch {
            repository.deleteGroup(id).onFailure { error ->
                mutableStateFlow.update { it.copy(message = error.message) }
            }
        }
    }
}

/**
 * Group management state.
 */
data class AuthenticatorGroupsState(
    val groups: List<AuthenticatorGroupRecord> = emptyList(),
    val name: String = "",
    val editingId: String? = null,
    val editingName: String = "",
    val pendingDeleteId: String? = null,
    val confirmAgain: Boolean = false,
    val message: String? = null,
)

/**
 * Group management has no navigation events of its own.
 */
sealed class AuthenticatorGroupsEvent

/**
 * Group management actions.
 */
sealed class AuthenticatorGroupsAction {
    /**
     * The new-group field changed.
     */
    data class NameChanged(val value: String) : AuthenticatorGroupsAction()

    /**
     * Create the group named in the field.
     */
    data object Create : AuthenticatorGroupsAction()

    /**
     * Start renaming [id].
     */
    data class StartRename(val id: String, val name: String) : AuthenticatorGroupsAction()

    /**
     * The rename field changed.
     */
    data class RenameChanged(val value: String) : AuthenticatorGroupsAction()

    /**
     * Save the rename of [id].
     */
    data class SaveRename(val id: String) : AuthenticatorGroupsAction()

    /**
     * Move [id] to [toIndex] in the catalog.
     */
    data class Move(val id: String, val toIndex: Int) : AuthenticatorGroupsAction()

    /**
     * Ask to delete [id].
     */
    data class AskDelete(val id: String) : AuthenticatorGroupsAction()

    /**
     * The first delete confirmation.
     */
    data object ConfirmDeleteOnce : AuthenticatorGroupsAction()

    /**
     * The second delete confirmation. Entries are not deleted.
     */
    data object ConfirmDelete : AuthenticatorGroupsAction()

    /**
     * Close a dialog.
     */
    data object Dismiss : AuthenticatorGroupsAction()

    /**
     * Actions produced inside the view model.
     */
    sealed class Internal : AuthenticatorGroupsAction() {
        /**
         * The repository published entries.
         */
        data class Loaded(
            val entries: List<com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView>,
        ) : Internal()
    }
}
