package com.x8bit.bitwarden.ui.authenticator

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bitwarden.ui.platform.base.util.EventsEffect
import com.bitwarden.ui.platform.base.util.standardHorizontalMargin
import com.bitwarden.ui.platform.components.button.BitwardenFilledButton
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.snackbar.BitwardenSnackbarHost
import com.bitwarden.ui.platform.components.snackbar.model.BitwardenSnackbarData
import com.bitwarden.ui.platform.components.snackbar.model.rememberBitwardenSnackbarHostState
import com.bitwarden.ui.platform.components.scaffold.model.rememberBitwardenPullToRefreshState
import com.bitwarden.ui.util.asText
import com.bitwarden.ui.platform.components.dialog.BitwardenBasicDialog
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.components.field.BitwardenTextField
import com.bitwarden.ui.platform.components.model.CardStyle
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.composition.LocalIntentManager
import com.x8bit.bitwarden.ui.platform.composition.LocalPermissionsManager
import com.bitwarden.ui.platform.manager.util.startAppSettingsActivity
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
    val snackbarHostState = rememberBitwardenSnackbarHostState()
    val view = LocalView.current
    LaunchedEffect(viewModel) {
        viewModel.liveEntries.collect { viewModel.applySnapshot(it) }
    }
    val context = LocalContext.current
    val intentManager = LocalIntentManager.current
    val permissionsManager = LocalPermissionsManager.current
    var menu by rememberSaveable { mutableStateOf(false) }
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    val cameraLauncher = permissionsManager.getLauncher { granted ->
        if (granted) {
            onScan()
        } else {
            cameraDenied = true
        }
    }
    val requestScan = {
        if (permissionsManager.checkPermission(Manifest.permission.CAMERA)) {
            onScan()
        } else {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }
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
    LaunchedEffect(state.copiedNonce) {
        if (state.copiedId != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
    EventsEffect(viewModel) { event ->
        when (event) {
            is AuthenticatorListEvent.Notice -> {
                snackbarHostState.showSnackbar(
                    BitwardenSnackbarData(message = event.text.asText()),
                )
            }
        }
    }
    if (cameraDenied) {
        BitwardenTwoButtonDialog(
            title = stringResource(BitwardenString.zh_scan_permission_title),
            message = stringResource(BitwardenString.zh_scan_permission_body),
            confirmButtonText = stringResource(BitwardenString.zh_scan_open_settings),
            dismissButtonText = stringResource(BitwardenString.cancel),
            onConfirmClick = {
                cameraDenied = false
                intentManager.startAppSettingsActivity()
            },
            onDismissClick = { cameraDenied = false },
            onDismissRequest = { cameraDenied = false },
        )
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
        snackbarHost = { BitwardenSnackbarHost(bitwardenHostState = snackbarHostState) },
        pullToRefreshState = rememberBitwardenPullToRefreshState(
            isEnabled = !trash,
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.trySendAction(AuthenticatorListAction.Refresh) },
        ),
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
                        onClick = requestScan,
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
                    imeAction = ImeAction.Search,
                    keyboardActions = KeyboardActions(
                        onSearch = { view.clearFocus() },
                    ),
                    cardStyle = CardStyle.Full,
                    modifier = Modifier
                        .standardHorizontalMargin()
                        .fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GroupChip(
                        label = stringResource(BitwardenString.zh_authenticator_sort_custom),
                        selected = state.sort == AuthenticatorSort.MANUAL,
                        onClick = {
                            viewModel.trySendAction(
                                AuthenticatorListAction.SortSelected(AuthenticatorSort.MANUAL),
                            )
                        },
                    )
                    GroupChip(
                        label = stringResource(BitwardenString.zh_authenticator_sort_name_az),
                        selected = state.sort == AuthenticatorSort.NAME,
                        onClick = {
                            viewModel.trySendAction(
                                AuthenticatorListAction.SortSelected(AuthenticatorSort.NAME),
                            )
                        },
                    )
                }
                state.syncedAtMillis?.let { syncedAt ->
                    val syncedLabel = java.time.Instant.ofEpochMilli(syncedAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                    Text(
                        text = stringResource(
                            BitwardenString.zh_authenticator_synced_at,
                            syncedLabel,
                        ),
                        style = BitwardenTheme.typography.bodySmall,
                        color = BitwardenTheme.colorScheme.text.secondary,
                        modifier = Modifier
                            .standardHorizontalMargin()
                            .padding(top = 4.dp),
                    )
                }
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
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = BitwardenTheme.colorScheme.icon.secondary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(BitwardenString.zh_authenticator_syncing),
                        color = BitwardenTheme.colorScheme.text.secondary,
                    )
                }
            }
            if (state.rows.isEmpty() && state.status != AuthenticatorSyncStatus.LOADING) {
                AuthenticatorEmpty(
                    trash = trash,
                    onScan = requestScan,
                    onAdd = onAdd,
                )
            }
            val reorderEnabled = !trash &&
                state.sort == AuthenticatorSort.MANUAL &&
                state.query.isBlank()
            val listState = rememberLazyListState()
            var dragRows by remember { mutableStateOf<List<AuthenticatorRow>?>(null) }
            val shownRows = dragRows ?: state.rows
            LaunchedEffect(state.rows) {
                val dragging = dragRows ?: return@LaunchedEffect
                if (state.rows.map { it.id } == dragging.map { it.id }) {
                    dragRows = null
                } else {
                    val byId = state.rows.associateBy { it.id }
                    val refreshed = dragging.mapNotNull { byId[it.id] }
                    if (refreshed.size == dragging.size) dragRows = refreshed
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(shownRows, key = { _, row -> row.id }) { index, row ->
                    AuthenticatorCard(
                        row = row,
                        query = state.query,
                        trash = trash,
                        copied = state.copiedId == row.id,
                        reorder = if (!reorderEnabled) {
                            null
                        } else {
                            AuthenticatorReorder(
                                index = index,
                                lastIndex = shownRows.lastIndex,
                                listState = listState,
                                onDragStart = { if (dragRows == null) dragRows = state.rows },
                                onShift = { target ->
                                    val current = (dragRows ?: state.rows).toMutableList()
                                    val from = current.indexOfFirst { it.id == row.id }
                                    if (from >= 0 && target in current.indices && target != from) {
                                        dragRows = current.apply { add(target, removeAt(from)) }
                                    }
                                },
                                onDrop = { start ->
                                    if (start < 0) {
                                        dragRows = null
                                    } else {
                                        val finalIndex = (dragRows ?: shownRows)
                                            .indexOfFirst { it.id == row.id }
                                        if (finalIndex >= 0 && finalIndex != start) {
                                            viewModel.trySendAction(
                                                AuthenticatorListAction.MoveTo(row.id, finalIndex),
                                            )
                                        } else {
                                            dragRows = null
                                        }
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CONTEXT_CLICK,
                                        )
                                    }
                                },
                            )
                        },
                        onCopy = {
                            viewModel.trySendAction(AuthenticatorListAction.CopyClick(row.id))
                        },
                        onEdit = { onEdit(row.id) },
                        onMoveUp = {
                            viewModel.trySendAction(
                                AuthenticatorListAction.MoveTo(row.id, index - 1),
                            )
                        },
                        onMoveDown = {
                            viewModel.trySendAction(
                                AuthenticatorListAction.MoveTo(row.id, index + 1),
                            )
                        },
                        onMoveTop = {
                            viewModel.trySendAction(AuthenticatorListAction.MoveTo(row.id, 0))
                        },
                        onTrash = {
                            viewModel.trySendAction(
                                AuthenticatorListAction.TrashClick(row.id, row.title),
                            )
                        },
                        onRestore = {
                            viewModel.trySendAction(AuthenticatorListAction.RestoreClick(row.id))
                        },
                        onFavorite = {
                            viewModel.trySendAction(
                                AuthenticatorListAction.FavoriteClick(row.id, row.favorite),
                            )
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
internal fun AuthenticatorCard(
    row: AuthenticatorRow,
    query: String,
    trash: Boolean,
    onCopy: () -> Unit,
    copied: Boolean = false,
    reorder: AuthenticatorReorder? = null,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onMoveTop: () -> Unit = {},
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by rememberSaveable(row.id) { mutableStateOf(false) }
    val urgent = !trash && row.seconds in 1..AUTHENTICATOR_URGENT_SECONDS
    Column(modifier = modifier.fillMaxWidth().testTag("AuthenticatorRow")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = if (trash) onEdit else onCopy,
                        onLongClick = { menu = true },
                    ),
            ) {
                AuthenticatorIdentity(row = row, query = query, modifier = Modifier.weight(1f))
                AuthenticatorCode(
                    row = row,
                    trash = trash,
                    urgent = urgent,
                    copied = copied,
                )
            }
            Box {
                AuthenticatorOverflowButton(onClick = { menu = true })
                AuthenticatorCardMenu(
                    expanded = menu,
                    trash = trash,
                    favorite = row.favorite,
                    canMoveUp = reorder != null && reorder.index > 0,
                    canMoveDown = reorder != null && reorder.index < reorder.lastIndex,
                    onDismiss = { menu = false },
                    onCopy = onCopy,
                    onEdit = onEdit,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onMoveTop = onMoveTop,
                    onFavorite = onFavorite,
                    onTrash = onTrash,
                    onRestore = onRestore,
                    onDelete = onDelete,
                )
            }
            if (reorder != null) {
                DragHandle(reorder)
            }
        }
        HorizontalDivider(color = BitwardenTheme.colorScheme.stroke.divider)
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun AuthenticatorCardMenu(
    expanded: Boolean,
    trash: Boolean,
    favorite: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveTop: () -> Unit,
    onFavorite: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (!trash) {
            DropdownMenuItem(
                text = { Text(stringResource(BitwardenString.copy)) },
                onClick = {
                    onDismiss()
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(BitwardenString.edit)) },
                onClick = {
                    onDismiss()
                    onEdit()
                },
            )
            if (canMoveUp) {
                DropdownMenuItem(
                    text = { Text(stringResource(BitwardenString.zh_authenticator_move_up)) },
                    onClick = {
                        onDismiss()
                        onMoveUp()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(BitwardenString.zh_authenticator_move_top)) },
                    onClick = {
                        onDismiss()
                        onMoveTop()
                    },
                )
            }
            if (canMoveDown) {
                DropdownMenuItem(
                    text = { Text(stringResource(BitwardenString.zh_authenticator_move_down)) },
                    onClick = {
                        onDismiss()
                        onMoveDown()
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (favorite) {
                                BitwardenString.unfavorite
                            } else {
                                BitwardenString.favorite
                            },
                        ),
                    )
                },
                onClick = {
                    onDismiss()
                    onFavorite()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(BitwardenString.delete)) },
                onClick = {
                    onDismiss()
                    onTrash()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(BitwardenString.zh_authenticator_restore)) },
                onClick = {
                    onDismiss()
                    onRestore()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(BitwardenString.delete)) },
                onClick = {
                    onDismiss()
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun AuthenticatorEmpty(
    trash: Boolean,
    onScan: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (trash) {
                    BitwardenString.zh_authenticator_trash
                } else {
                    BitwardenString.zh_authenticator_empty_title
                },
            ),
            style = BitwardenTheme.typography.titleMedium,
            color = BitwardenTheme.colorScheme.text.primary,
        )
        Text(
            text = stringResource(
                if (trash) {
                    BitwardenString.zh_authenticator_trash_empty
                } else {
                    BitwardenString.zh_authenticator_empty
                },
            ),
            style = BitwardenTheme.typography.bodyMedium,
            color = BitwardenTheme.colorScheme.text.secondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        if (!trash) {
            BitwardenFilledButton(
                label = stringResource(BitwardenString.zh_authenticator_scan),
                onClick = onScan,
                modifier = Modifier.fillMaxWidth(),
            )
            BitwardenTextButton(
                label = stringResource(BitwardenString.zh_authenticator_add),
                onClick = onAdd,
                modifier = Modifier.padding(top = 4.dp),
            )
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

/**
 * Drag state for one authenticator row. Null while search or name order is on.
 */
internal data class AuthenticatorReorder(
    val index: Int,
    val lastIndex: Int,
    val listState: LazyListState,
    val onDragStart: () -> Unit,
    val onShift: (Int) -> Unit,
    val onDrop: (Int) -> Unit,
)

@Composable
@Suppress("MagicNumber")
private fun DragHandle(reorder: AuthenticatorReorder) {
    val latest = rememberUpdatedState(reorder)
    val color = BitwardenTheme.colorScheme.icon.secondary
    val label = stringResource(BitwardenString.zh_authenticator_drag_handle)
    Canvas(
        Modifier
            .size(40.dp)
            .semantics { contentDescription = label }
            .testTag("AuthenticatorDragHandle")
            .pointerInput(Unit) {
                var startIndex = 0
                var accumulated = 0f
                detectDragGestures(
                    onDragStart = {
                        startIndex = latest.value.index
                        accumulated = 0f
                        latest.value.onDragStart()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val current = latest.value
                        accumulated += amount.y
                        val rows = current.listState.layoutInfo.visibleItemsInfo
                        val item = rows.find { it.index == current.index }
                        val middle = item?.let { it.offset + it.size / 2f + accumulated }
                        val target = if (item == null || middle == null) {
                            null
                        } else {
                            rows.minByOrNull { row ->
                                kotlin.math.abs((row.offset + row.size / 2f) - middle)
                            }
                        }
                        if (target != null && target.index != current.index) {
                            current.onShift(target.index)
                            accumulated = 0f
                        }
                    },
                    onDragEnd = { latest.value.onDrop(startIndex) },
                    onDragCancel = { latest.value.onDrop(-1) },
                )
            },
    ) {
        val bar = 2.dp.toPx()
        val gap = 4.dp.toPx()
        val width = size.width * 0.55f
        val left = (size.width - width) / 2f
        var top = (size.height - (bar * 3f + gap * 2f)) / 2f
        repeat(3) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, bar),
                cornerRadius = CornerRadius(bar, bar),
            )
            top += bar + gap
        }
    }
}
