package com.x8bit.bitwarden.ui.authenticator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitwarden.ui.platform.base.util.EventsEffect
import com.bitwarden.ui.platform.base.util.standardHorizontalMargin
import com.bitwarden.ui.platform.components.button.BitwardenFilledButton
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.dialog.BitwardenBasicDialog
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.components.field.BitwardenTextField
import com.bitwarden.ui.platform.components.model.CardStyle
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.components.toggle.BitwardenSwitch
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorAlgorithm

/**
 * Add or edit an authenticator entry.
 */
@Composable
@Suppress("LongMethod")
fun AuthenticatorEditScreen(
    entryId: String,
    onBack: () -> Unit,
    viewModel: AuthenticatorEditViewModel = hiltViewModel<
        AuthenticatorEditViewModel,
        AuthenticatorEditViewModel.Factory,
        >(
        creationCallback = { it.create(entryId) },
    ),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    EventsEffect(viewModel) { event ->
        when (event) {
            AuthenticatorEditEvent.Saved -> onBack()
        }
    }
    if (state.confirmDelete) {
        BitwardenTwoButtonDialog(
            title = state.displayName,
            message = stringResource(BitwardenString.zh_authenticator_delete_body),
            confirmButtonText = stringResource(BitwardenString.delete),
            dismissButtonText = stringResource(BitwardenString.cancel),
            confirmTextColor = BitwardenTheme.colorScheme.status.error,
            onConfirmClick = { viewModel.trySendAction(AuthenticatorEditAction.ConfirmDelete) },
            onDismissClick = { viewModel.trySendAction(AuthenticatorEditAction.DismissDialog) },
            onDismissRequest = { viewModel.trySendAction(AuthenticatorEditAction.DismissDialog) },
        )
    }
    state.message?.let { message ->
        BitwardenBasicDialog(
            title = null,
            message = message,
            onDismissRequest = { viewModel.trySendAction(AuthenticatorEditAction.DismissDialog) },
        )
    }
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
                text = stringResource(
                    if (state.isExisting) {
                        BitwardenString.edit
                    } else {
                        BitwardenString.zh_authenticator_add
                    },
                ),
                style = BitwardenTheme.typography.titleLarge,
                modifier = Modifier.standardHorizontalMargin(),
            )
            Field(state.secret, BitwardenString.zh_authenticator_secret) {
                viewModel.trySendAction(AuthenticatorEditAction.SecretChanged(it))
            }
            Field(state.name, BitwardenString.zh_authenticator_name) {
                viewModel.trySendAction(AuthenticatorEditAction.NameChanged(it))
            }
            Field(state.issuer, BitwardenString.zh_authenticator_issuer) {
                viewModel.trySendAction(AuthenticatorEditAction.IssuerChanged(it))
            }
            Field(state.host, BitwardenString.zh_authenticator_host) {
                viewModel.trySendAction(AuthenticatorEditAction.HostChanged(it))
            }
            Field(state.tags, BitwardenString.zh_authenticator_tags) {
                viewModel.trySendAction(AuthenticatorEditAction.TagsChanged(it))
            }
            Field(state.notes, BitwardenString.zh_authenticator_notes) {
                viewModel.trySendAction(AuthenticatorEditAction.NotesChanged(it))
            }
            Text(
                text = stringResource(BitwardenString.zh_authenticator_advanced),
                modifier = Modifier
                    .standardHorizontalMargin()
                    .padding(top = 16.dp),
                color = BitwardenTheme.colorScheme.text.secondary,
            )
            Field(state.digits, BitwardenString.zh_authenticator_digits) {
                viewModel.trySendAction(AuthenticatorEditAction.DigitsChanged(it))
            }
            Field(state.period, BitwardenString.zh_authenticator_period) {
                viewModel.trySendAction(AuthenticatorEditAction.PeriodChanged(it))
            }
            AlgorithmRow(state.algorithm) {
                viewModel.trySendAction(AuthenticatorEditAction.AlgorithmChanged(it))
            }
            Switch(BitwardenString.zh_authenticator_steam, state.steam) {
                viewModel.trySendAction(AuthenticatorEditAction.SteamChanged(it))
            }
            Switch(BitwardenString.zh_authenticator_hotp, state.hotp) {
                viewModel.trySendAction(AuthenticatorEditAction.HotpChanged(it))
            }
            Switch(BitwardenString.zh_authenticator_allow_mcp, state.mcpEnabled) {
                viewModel.trySendAction(AuthenticatorEditAction.McpChanged(it))
            }
            if (state.mcpEnabled) {
                Field(state.alias, BitwardenString.zh_authenticator_alias) {
                    viewModel.trySendAction(AuthenticatorEditAction.AliasChanged(it))
                }
                Text(
                    text = stringResource(BitwardenString.zh_authenticator_mcp_help),
                    color = BitwardenTheme.colorScheme.text.secondary,
                    modifier = Modifier
                        .standardHorizontalMargin()
                        .padding(vertical = 8.dp),
                )
            }
            BitwardenFilledButton(
                label = stringResource(BitwardenString.zh_authenticator_save),
                onClick = { viewModel.trySendAction(AuthenticatorEditAction.SaveClick) },
                modifier = Modifier
                    .standardHorizontalMargin()
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
            if (state.isExisting) {
                BitwardenTextButton(
                    label = stringResource(BitwardenString.delete),
                    onClick = { viewModel.trySendAction(AuthenticatorEditAction.DeleteClick) },
                    modifier = Modifier.standardHorizontalMargin(),
                )
            }
        }
    }
}

@Composable
private fun Field(value: String, label: Int, onValue: (String) -> Unit) {
    BitwardenTextField(
        value = value,
        onValueChange = onValue,
        label = stringResource(label),
        cardStyle = CardStyle.Full,
        modifier = Modifier
            .standardHorizontalMargin()
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun Switch(label: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    BitwardenSwitch(
        label = stringResource(label),
        isChecked = checked,
        onCheckedChange = onChecked,
        cardStyle = CardStyle.Full,
        modifier = Modifier
            .standardHorizontalMargin()
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun AlgorithmRow(
    selected: AuthenticatorAlgorithm,
    onSelect: (AuthenticatorAlgorithm) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .standardHorizontalMargin()
            .padding(top = 8.dp),
    ) {
        AuthenticatorAlgorithm.entries.forEach { algorithm ->
            BitwardenTextButton(
                label = algorithm.name,
                onClick = { onSelect(algorithm) },
            )
            if (algorithm == selected) {
                Text(text = "·", color = BitwardenTheme.colorScheme.text.interaction)
            }
        }
    }
}
