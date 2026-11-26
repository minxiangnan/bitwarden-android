package com.x8bit.bitwarden.ui.platform.update

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitwarden.ui.platform.base.util.EventsEffect
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.dialog.BitwardenBasicDialog
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.components.dialog.util.maxDialogHeight
import com.bitwarden.ui.platform.components.dialog.util.maxDialogWidth
import com.bitwarden.ui.platform.components.divider.BitwardenHorizontalDivider
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme

/**
 * Update dialogs hosted by the root activity so they show on any screen.
 */
@Composable
fun AppUpdateHost(
    viewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    EventsEffect(viewModel = viewModel) { event ->
        when (event) {
            is AppUpdateEvent.Launch -> context.startUpdateIntent(event)
        }
    }
    AppUpdateDialogs(
        state = state,
        onAction = viewModel::trySendAction,
    )
}

@Composable
private fun AppUpdateDialogs(
    state: AppUpdateState,
    onAction: (AppUpdateAction) -> Unit,
) {
    val offer = state.offer
    if (state.showPrompt && offer != null) {
        UpdatePromptDialog(
            version = offer.version,
            notes = offer.notes.ifBlank {
                stringResource(BitwardenString.zh_update_no_notes)
            },
            onUpdate = { onAction(AppUpdateAction.UpdateNow) },
            onLater = { onAction(AppUpdateAction.Later) },
            onIgnore = { onAction(AppUpdateAction.Ignore) },
        )
    }
    if (state.downloading) {
        DownloadDialog(
            progress = state.progress,
            onCancel = { onAction(AppUpdateAction.CancelDownload) },
        )
    }
    if (state.askInstallPermission) {
        BitwardenTwoButtonDialog(
            title = stringResource(BitwardenString.zh_update_permission_title),
            message = stringResource(BitwardenString.zh_update_permission_body),
            confirmButtonText = stringResource(BitwardenString.zh_update_open_settings),
            dismissButtonText = stringResource(BitwardenString.cancel),
            onConfirmClick = { onAction(AppUpdateAction.OpenInstallSettings) },
            onDismissClick = { onAction(AppUpdateAction.DismissPermission) },
            onDismissRequest = { onAction(AppUpdateAction.DismissPermission) },
        )
    }
    val message = state.message
    if (message != null) {
        BitwardenBasicDialog(
            title = null,
            message = stringResource(message.stringRes()),
            onDismissRequest = { onAction(AppUpdateAction.DismissMessage) },
        )
    }
}

@Composable
private fun UpdatePromptDialog(
    version: String,
    notes: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
) {
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val configuration = LocalConfiguration.current
        Column(
            modifier = Modifier
                .requiredHeightIn(max = configuration.maxDialogHeight)
                .requiredWidthIn(max = configuration.maxDialogWidth)
                .padding(horizontal = 16.dp)
                .background(
                    color = BitwardenTheme.colorScheme.background.primary,
                    shape = BitwardenTheme.shapes.dialog,
                ),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(BitwardenString.zh_update_available, version),
                    style = BitwardenTheme.typography.headlineSmall,
                    color = BitwardenTheme.colorScheme.text.primary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = notes,
                    style = BitwardenTheme.typography.bodyMedium,
                    color = BitwardenTheme.colorScheme.text.primary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            BitwardenHorizontalDivider()
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                BitwardenTextButton(
                    label = stringResource(BitwardenString.zh_update_ignore),
                    onClick = onIgnore,
                )
                BitwardenTextButton(
                    label = stringResource(BitwardenString.zh_update_later),
                    onClick = onLater,
                )
                BitwardenTextButton(
                    label = stringResource(BitwardenString.zh_update_now),
                    onClick = onUpdate,
                )
            }
        }
    }
}

@Composable
private fun DownloadDialog(
    progress: Float?,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val configuration = LocalConfiguration.current
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .requiredWidthIn(max = configuration.maxDialogWidth)
                .padding(horizontal = 16.dp)
                .background(
                    color = BitwardenTheme.colorScheme.background.primary,
                    shape = BitwardenTheme.shapes.dialog,
                ),
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            DownloadProgress(progress = progress)
            Spacer(modifier = Modifier.height(8.dp))
            BitwardenTextButton(
                label = stringResource(BitwardenString.cancel),
                onClick = onCancel,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun DownloadProgress(progress: Float?) {
    Column {
        Text(
            text = if (progress == null) {
                stringResource(BitwardenString.zh_update_downloading)
            } else {
                stringResource(
                    BitwardenString.zh_update_downloading_percent,
                    (progress * PERCENT).toInt(),
                )
            },
            style = BitwardenTheme.typography.bodyMedium,
            color = BitwardenTheme.colorScheme.text.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (progress == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }
    }
}

private fun AppUpdateMessage.stringRes(): Int = when (this) {
    AppUpdateMessage.UP_TO_DATE -> BitwardenString.zh_update_latest
    AppUpdateMessage.FAILED -> BitwardenString.zh_update_failed
    AppUpdateMessage.BAD_HASH -> BitwardenString.zh_update_bad_hash
}

private fun Context.startUpdateIntent(event: AppUpdateEvent.Launch) {
    startActivity(event.intent)
}

private const val PERCENT: Float = 100f
