package com.x8bit.bitwarden.ui.authenticator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitwarden.ui.platform.base.util.standardHorizontalMargin
import com.bitwarden.ui.platform.components.button.BitwardenFilledButton
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme

/**
 * MCP access token settings.
 */
@Composable
@Suppress("LongMethod")
fun AuthenticatorMcpScreen(
    onBack: () -> Unit,
    viewModel: AuthenticatorMcpViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val confirming = state.message == CONFIRM_ROTATE || state.message == CONFIRM_REVOKE
    if (confirming && state.message != null) {
        BitwardenTwoButtonDialog(
            title = if (state.message == CONFIRM_ROTATE) {
                stringResource(BitwardenString.zh_authenticator_rotate_token)
            } else {
                stringResource(BitwardenString.zh_authenticator_revoke_token)
            },
            message = state.message.orEmpty(),
            confirmButtonText = stringResource(BitwardenString.okay),
            dismissButtonText = stringResource(BitwardenString.cancel),
            confirmTextColor = BitwardenTheme.colorScheme.status.error,
            onConfirmClick = { viewModel.trySendAction(AuthenticatorMcpAction.Confirm) },
            onDismissClick = { viewModel.trySendAction(AuthenticatorMcpAction.Dismiss) },
            onDismissRequest = { viewModel.trySendAction(AuthenticatorMcpAction.Dismiss) },
        )
    }
    BitwardenScaffold(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            BitwardenTextButton(label = stringResource(BitwardenString.cancel), onClick = onBack)
            Text(
                text = stringResource(BitwardenString.zh_authenticator_mcp),
                style = BitwardenTheme.typography.titleLarge,
                modifier = Modifier.standardHorizontalMargin(),
            )
            Text(
                text = when {
                    !state.supported -> stringResource(BitwardenString.zh_authenticator_unsupported)
                    state.configured -> {
                        state.label ?: stringResource(BitwardenString.zh_authenticator_mcp)
                    }
                    else -> stringResource(BitwardenString.zh_authenticator_mcp_help)
                },
                color = BitwardenTheme.colorScheme.text.secondary,
                modifier = Modifier
                    .standardHorizontalMargin()
                    .padding(vertical = 12.dp),
            )
            state.revealedToken?.let { token ->
                Text(
                    text = stringResource(BitwardenString.zh_authenticator_token_once),
                    color = BitwardenTheme.colorScheme.status.error,
                    modifier = Modifier.standardHorizontalMargin(),
                )
                Text(
                    text = token,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .standardHorizontalMargin()
                        .padding(vertical = 8.dp),
                )
                BitwardenTextButton(
                    label = stringResource(BitwardenString.copy),
                    onClick = { viewModel.trySendAction(AuthenticatorMcpAction.CopyToken) },
                )
            }
            if (!state.configured) {
                BitwardenFilledButton(
                    label = stringResource(BitwardenString.zh_authenticator_create_token),
                    onClick = { viewModel.trySendAction(AuthenticatorMcpAction.CreateClick) },
                    modifier = Modifier
                        .standardHorizontalMargin()
                        .fillMaxWidth(),
                )
            } else {
                BitwardenTextButton(
                    label = stringResource(BitwardenString.zh_authenticator_rotate_token),
                    onClick = { viewModel.trySendAction(AuthenticatorMcpAction.RotateClick) },
                )
                BitwardenTextButton(
                    label = stringResource(BitwardenString.zh_authenticator_revoke_token),
                    onClick = { viewModel.trySendAction(AuthenticatorMcpAction.RevokeClick) },
                )
            }
            if (!confirming) {
                state.message?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.standardHorizontalMargin(),
                        color = BitwardenTheme.colorScheme.text.secondary,
                    )
                }
            }
        }
    }
}
