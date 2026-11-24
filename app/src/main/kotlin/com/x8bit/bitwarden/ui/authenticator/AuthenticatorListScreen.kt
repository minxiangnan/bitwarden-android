package com.x8bit.bitwarden.ui.authenticator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bitwarden.ui.platform.base.util.EventsEffect
import com.bitwarden.ui.platform.base.util.standardHorizontalMargin
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.dialog.BitwardenBasicDialog
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.components.field.BitwardenTextField
import com.bitwarden.ui.platform.components.model.CardStyle
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.composition.LocalIntentManager
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import com.x8bit.bitwarden.data.authenticator.importing.decodeQrImage
import com.x8bit.bitwarden.data.authenticator.match.AuthenticatorSort
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorSyncStatus

/**
 * Authenticator list or trash.
 */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun AuthenticatorListScreen(
    trash: Boolean,
    onScan: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onTrash: () -> Unit,
    onMcp: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthenticatorListViewModel = hiltViewModel<
        AuthenticatorListViewModel,
        AuthenticatorListViewModel.Factory,
        >(
        creationCallback = { it.create(trash) },
    ),
) {
    val state by viewModel.stateFlow.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.liveEntries.collect { viewModel.applySnapshot(it) }
    }
    val context = LocalContext.current
    val intentManager = LocalIntentManager.current
    var menu by rememberSaveable { mutableStateOf(false) }
    var paste by rememberSaveable { mutableStateOf(false) }
    var pasteText by rememberSaveable { mutableStateOf("") }
    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.readText(context)?.let {
            viewModel.trySendAction(AuthenticatorListAction.ImportText(it))
        }
    }
    val importImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val bytes = uri?.readBytes(context) ?: return@rememberLauncherForActivityResult
        val payload = decodeQrImage(bytes)
        if (payload == null) {
            viewModel.trySendAction(AuthenticatorListAction.ShowMessage("图片里没有二维码"))
        } else {
            viewModel.trySendAction(AuthenticatorListAction.ImportText(payload))
        }
    }
    EventsEffect(viewModel) { event ->
        when (event) {
            AuthenticatorListEvent.Copied -> Unit
        }
    }
    AuthenticatorListDialogs(
        dialog = state.dialog,
        onDismiss = { viewModel.trySendAction(AuthenticatorListAction.DismissDialog) },
        onConfirmTrash = { viewModel.trySendAction(AuthenticatorListAction.ConfirmTrash(it)) },
        onConfirmDelete = { viewModel.trySendAction(AuthenticatorListAction.ConfirmDelete(it)) },
    )
    if (paste) {
        PasteDialog(
            text = pasteText,
            onText = { pasteText = it },
            onSubmit = {
                viewModel.trySendAction(AuthenticatorListAction.ImportText(pasteText))
                paste = false
                pasteText = ""
            },
            onDismiss = { paste = false },
        )
    }
    BitwardenScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (trash) {
                    BitwardenTextButton(
                        label = stringResource(BitwardenString.cancel),
                        onClick = onBack,
                    )
                }
                Text(
                    text = stringResource(
                        if (trash) {
                            BitwardenString.zh_authenticator_trash
                        } else {
                            BitwardenString.zh_authenticator_title
                        },
                    ),
                    style = BitwardenTheme.typography.titleLarge,
                    color = BitwardenTheme.colorScheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                if (!trash) {
                    BitwardenTextButton(
                        label = stringResource(BitwardenString.zh_authenticator_scan),
                        onClick = onScan,
                    )
                    BitwardenTextButton(
                        label = stringResource(BitwardenString.zh_authenticator_add),
                        onClick = onAdd,
                    )
                    BitwardenTextButton(
                        label = stringResource(BitwardenString.zh_authenticator_more),
                        onClick = { menu = true },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(BitwardenString.zh_authenticator_paste)) },
                            onClick = {
                                menu = false
                                paste = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(BitwardenString.zh_authenticator_import))
                            },
                            onClick = {
                                menu = false
                                importFile.launch("*/*")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(BitwardenString.zh_authenticator_image)) },
                            onClick = {
                                menu = false
                                importImage.launch("image/*")
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(BitwardenString.zh_authenticator_export))
                            },
                            onClick = {
                                menu = false
                                intentManager.shareText(viewModel.exportDocument())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(BitwardenString.zh_authenticator_trash)) },
                            onClick = {
                                menu = false
                                onTrash()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(BitwardenString.zh_authenticator_mcp)) },
                            onClick = {
                                menu = false
                                onMcp()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(BitwardenString.zh_authenticator_sort_name))
                            },
                            onClick = {
                                menu = false
                                viewModel.trySendAction(
                                    AuthenticatorListAction.SortSelected(AuthenticatorSort.NAME),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(BitwardenString.zh_authenticator_sort_issuer))
                            },
                            onClick = {
                                menu = false
                                viewModel.trySendAction(
                                    AuthenticatorListAction.SortSelected(AuthenticatorSort.ISSUER),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(BitwardenString.zh_authenticator_favorites))
                            },
                            onClick = {
                                menu = false
                                viewModel.trySendAction(AuthenticatorListAction.FavoritesToggled)
                            },
                        )
                    }
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!trash) {
                BitwardenTextField(
                    value = state.query,
                    onValueChange = {
                        viewModel.trySendAction(AuthenticatorListAction.SearchChanged(it))
                    },
                    label = stringResource(BitwardenString.zh_authenticator_search),
                    cardStyle = CardStyle.Full,
                    modifier = Modifier
                        .standardHorizontalMargin()
                        .fillMaxWidth(),
                )
                if (state.groups.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GroupChip(
                            label = stringResource(BitwardenString.zh_authenticator_all_groups),
                            selected = state.group == null,
                            onClick = {
                                viewModel.trySendAction(
                                    AuthenticatorListAction.GroupSelected(null),
                                )
                            },
                        )
                        state.groups.forEach { group ->
                            GroupChip(
                                label = group,
                                selected = state.group == group,
                                onClick = {
                                    viewModel.trySendAction(
                                        AuthenticatorListAction.GroupSelected(group),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    color = BitwardenTheme.colorScheme.text.secondary,
                    modifier = Modifier
                        .standardHorizontalMargin()
                        .padding(vertical = 8.dp),
                )
            }
            if (state.status == AuthenticatorSyncStatus.LOADING && state.rows.isEmpty()) {
                Text(
                    text = stringResource(BitwardenString.loading),
                    modifier = Modifier.padding(24.dp),
                    color = BitwardenTheme.colorScheme.text.secondary,
                )
            }
            if (state.rows.isEmpty() && state.status != AuthenticatorSyncStatus.LOADING) {
                Text(
                    text = stringResource(
                        if (trash) {
                            BitwardenString.zh_authenticator_trash_empty
                        } else {
                            BitwardenString.zh_authenticator_empty
                        },
                    ),
                    color = BitwardenTheme.colorScheme.text.secondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.rows, key = { it.id }) { row ->
                    AuthenticatorCard(
                        row = row,
                        trash = trash,
                        onCopy = {
                            viewModel.trySendAction(AuthenticatorListAction.CopyClick(row.id))
                        },
                        onEdit = { onEdit(row.id) },
                        onTrash = {
                            viewModel.trySendAction(
                                AuthenticatorListAction.TrashClick(row.id, row.title),
                            )
                        },
                        onRestore = {
                            viewModel.trySendAction(AuthenticatorListAction.RestoreClick(row.id))
                        },
                        onDelete = {
                            viewModel.trySendAction(
                                AuthenticatorListAction.PermanentDeleteClick(row.id, row.title),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun AuthenticatorCard(
    row: AuthenticatorRow,
    trash: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (trash) onEdit else onCopy)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.title, style = BitwardenTheme.typography.titleMedium)
                if (row.subtitle.isNotBlank()) {
                    Text(
                        text = row.subtitle,
                        style = BitwardenTheme.typography.bodySmall,
                        color = BitwardenTheme.colorScheme.text.secondary,
                    )
                }
            }
            if (row.favorite) {
                Text(text = "★", color = BitwardenTheme.colorScheme.text.interaction)
            }
        }
        Text(
            text = row.code.ifBlank { "—" },
            style = BitwardenTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            color = BitwardenTheme.colorScheme.text.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!trash && row.nextCode != null) {
                Text(
                    text = stringResource(BitwardenString.zh_authenticator_next) +
                        " " +
                        row.nextCode,
                    color = BitwardenTheme.colorScheme.text.secondary,
                )
                Text(
                    text = "${row.seconds}s",
                    color = BitwardenTheme.colorScheme.text.interaction,
                )
            }
            if (row.kindLabel.isNotBlank()) {
                Text(text = row.kindLabel, color = BitwardenTheme.colorScheme.text.secondary)
            }
            if (row.alias != null) {
                Text(text = row.alias, color = BitwardenTheme.colorScheme.text.interaction)
            }
        }
        Row {
            if (trash) {
                BitwardenTextButton(
                    label = stringResource(BitwardenString.zh_authenticator_restore),
                    onClick = onRestore,
                )
                BitwardenTextButton(
                    label = stringResource(BitwardenString.delete),
                    onClick = onDelete,
                )
            } else {
                BitwardenTextButton(label = stringResource(BitwardenString.edit), onClick = onEdit)
                BitwardenTextButton(
                    label = stringResource(BitwardenString.delete),
                    onClick = onTrash,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatorListDialogs(
    dialog: AuthenticatorListState.Dialog?,
    onDismiss: () -> Unit,
    onConfirmTrash: (String) -> Unit,
    onConfirmDelete: (String) -> Unit,
) {
    when (dialog) {
        is AuthenticatorListState.Dialog.ConfirmTrash -> {
            BitwardenTwoButtonDialog(
                title = dialog.name,
                message = stringResource(BitwardenString.zh_authenticator_delete_body),
                confirmButtonText = stringResource(BitwardenString.delete),
                dismissButtonText = stringResource(BitwardenString.cancel),
                confirmTextColor = BitwardenTheme.colorScheme.status.error,
                onConfirmClick = { onConfirmTrash(dialog.id) },
                onDismissClick = onDismiss,
                onDismissRequest = onDismiss,
            )
        }
        is AuthenticatorListState.Dialog.ConfirmDelete -> {
            BitwardenTwoButtonDialog(
                title = dialog.name,
                message = stringResource(BitwardenString.zh_authenticator_permanent_body),
                confirmButtonText = stringResource(BitwardenString.delete),
                dismissButtonText = stringResource(BitwardenString.cancel),
                confirmTextColor = BitwardenTheme.colorScheme.status.error,
                onConfirmClick = { onConfirmDelete(dialog.id) },
                onDismissClick = onDismiss,
                onDismissRequest = onDismiss,
            )
        }
        is AuthenticatorListState.Dialog.Message -> {
            BitwardenBasicDialog(
                title = null,
                message = dialog.text,
                onDismissRequest = onDismiss,
            )
        }
        null -> Unit
    }
}

@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        BitwardenTheme.colorScheme.icon.secondary
    } else {
        BitwardenTheme.colorScheme.background.secondary
    }
    val foreground = if (selected) {
        BitwardenTheme.colorScheme.text.reversed
    } else {
        BitwardenTheme.colorScheme.text.primary
    }
    Text(
        text = label,
        color = foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PasteDialog(
    text: String,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
        BitwardenTextField(
            value = text,
            onValueChange = onText,
            label = stringResource(BitwardenString.zh_authenticator_paste),
            cardStyle = CardStyle.Full,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            BitwardenTextButton(label = stringResource(BitwardenString.cancel), onClick = onDismiss)
            BitwardenTextButton(
                label = stringResource(BitwardenString.zh_authenticator_import),
                onClick = onSubmit,
            )
        }
        }
    }
}

private fun Uri.readText(context: android.content.Context): String? =
    context.contentResolver.openInputStream(this)?.use { it.readBytes().decodeToString() }

private fun Uri.readBytes(context: android.content.Context): ByteArray? =
    context.contentResolver.openInputStream(this)?.use { it.readBytes() }
