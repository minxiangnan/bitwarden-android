package com.x8bit.bitwarden.ui.authenticator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitwarden.ui.platform.components.button.BitwardenFilledButton
import com.x8bit.bitwarden.data.authenticator.group.AuthenticatorGroupRecord
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.dialog.BitwardenBasicDialog
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.components.field.BitwardenTextField
import com.bitwarden.ui.platform.components.model.CardStyle
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme

/**
 * Manage authenticator groups. Deleting a group does not delete its codes.
 */
@Composable
fun AuthenticatorGroupsScreen(
    onBack: () -> Unit,
    viewModel: AuthenticatorGroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    GroupDeleteDialogs(
        state = state,
        onAction = viewModel::trySendAction,
    )
    BitwardenScaffold(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            BitwardenTextButton(
                label = stringResource(BitwardenString.cancel),
                onClick = onBack,
            )
            Text(
                text = stringResource(BitwardenString.zh_authenticator_groups),
                style = BitwardenTheme.typography.titleLarge,
                color = BitwardenTheme.colorScheme.text.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            GroupCreateField(
                name = state.name,
                empty = state.groups.isEmpty(),
                onAction = viewModel::trySendAction,
            )
            state.groups.forEachIndexed { index, group ->
                GroupEditorRow(
                    group = group,
                    index = index,
                    lastIndex = state.groups.lastIndex,
                    editing = state.editingId == group.id,
                    editingName = state.editingName,
                    onAction = viewModel::trySendAction,
                )
            }
            Text(
                text = stringResource(BitwardenString.zh_authenticator_group_reorder_hint),
                color = BitwardenTheme.colorScheme.text.secondary,
                style = BitwardenTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun GroupCreateField(
    name: String,
    empty: Boolean,
    onAction: (AuthenticatorGroupsAction) -> Unit,
) {
    BitwardenTextField(
        value = name,
        onValueChange = { onAction(AuthenticatorGroupsAction.NameChanged(it)) },
        label = stringResource(BitwardenString.zh_authenticator_group_new),
        cardStyle = CardStyle.Full,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    )
    BitwardenFilledButton(
        label = stringResource(BitwardenString.zh_authenticator_group_create),
        onClick = { onAction(AuthenticatorGroupsAction.Create) },
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
    )
    if (empty) {
        Text(
            text = stringResource(BitwardenString.zh_authenticator_group_empty),
            color = BitwardenTheme.colorScheme.text.secondary,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun GroupDeleteDialogs(
    state: AuthenticatorGroupsState,
    onAction: (AuthenticatorGroupsAction) -> Unit,
) {
    val pending = state.groups.firstOrNull { it.id == state.pendingDeleteId }
    if (pending != null && !state.confirmAgain) {
        BitwardenTwoButtonDialog(
            title = pending.name,
            message = stringResource(BitwardenString.zh_authenticator_group_delete_body),
            confirmButtonText = stringResource(
                BitwardenString.zh_authenticator_group_delete_continue,
            ),
            dismissButtonText = stringResource(BitwardenString.cancel),
            confirmTextColor = BitwardenTheme.colorScheme.status.error,
            onConfirmClick = { onAction(AuthenticatorGroupsAction.ConfirmDeleteOnce) },
            onDismissClick = { onAction(AuthenticatorGroupsAction.Dismiss) },
            onDismissRequest = { onAction(AuthenticatorGroupsAction.Dismiss) },
        )
    }
    if (pending != null && state.confirmAgain) {
        BitwardenTwoButtonDialog(
            title = pending.name,
            message = stringResource(
                BitwardenString.zh_authenticator_group_delete_again,
                pending.name,
            ),
            confirmButtonText = stringResource(BitwardenString.zh_authenticator_group_delete),
            dismissButtonText = stringResource(BitwardenString.cancel),
            confirmTextColor = BitwardenTheme.colorScheme.status.error,
            onConfirmClick = { onAction(AuthenticatorGroupsAction.ConfirmDelete) },
            onDismissClick = { onAction(AuthenticatorGroupsAction.Dismiss) },
            onDismissRequest = { onAction(AuthenticatorGroupsAction.Dismiss) },
        )
    }
    state.message?.let { message ->
        BitwardenBasicDialog(
            title = null,
            message = message,
            onDismissRequest = { onAction(AuthenticatorGroupsAction.Dismiss) },
        )
    }
}

@Composable
private fun GroupEditorRow(
    group: AuthenticatorGroupRecord,
    index: Int,
    lastIndex: Int,
    editing: Boolean,
    editingName: String,
    onAction: (AuthenticatorGroupsAction) -> Unit,
) {
    if (editing) {
        GroupRenameField(group.id, editingName, onAction)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = group.name,
            color = BitwardenTheme.colorScheme.text.primary,
            style = BitwardenTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (index > 0) {
            BitwardenTextButton(
                label = stringResource(BitwardenString.zh_authenticator_move_up),
                onClick = { onAction(AuthenticatorGroupsAction.Move(group.id, index - 1)) },
            )
        }
        if (index < lastIndex) {
            BitwardenTextButton(
                label = stringResource(BitwardenString.zh_authenticator_move_down),
                onClick = { onAction(AuthenticatorGroupsAction.Move(group.id, index + 1)) },
            )
        }
        BitwardenTextButton(
            label = stringResource(BitwardenString.zh_authenticator_group_rename),
            onClick = {
                onAction(AuthenticatorGroupsAction.StartRename(group.id, group.name))
            },
        )
        BitwardenTextButton(
            label = stringResource(BitwardenString.zh_authenticator_group_delete),
            onClick = { onAction(AuthenticatorGroupsAction.AskDelete(group.id)) },
        )
    }
}

@Composable
private fun GroupRenameField(
    id: String,
    editingName: String,
    onAction: (AuthenticatorGroupsAction) -> Unit,
) {
    BitwardenTextField(
        value = editingName,
        onValueChange = { onAction(AuthenticatorGroupsAction.RenameChanged(it)) },
        label = stringResource(BitwardenString.zh_authenticator_group_rename),
        cardStyle = CardStyle.Full,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
    )
    BitwardenTextButton(
        label = stringResource(BitwardenString.save),
        onClick = { onAction(AuthenticatorGroupsAction.SaveRename(id)) },
    )
}
