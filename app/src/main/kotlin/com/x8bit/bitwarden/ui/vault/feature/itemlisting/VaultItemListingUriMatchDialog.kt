package com.x8bit.bitwarden.ui.vault.feature.itemlisting

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bitwarden.ui.platform.components.dialog.BitwardenSelectionDialog
import com.bitwarden.ui.platform.components.dialog.row.BitwardenSelectionRow
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.util.Text
import com.x8bit.bitwarden.ui.vault.feature.addedit.model.UriMatchDisplayType
import com.x8bit.bitwarden.ui.vault.feature.addedit.util.displayLabel

/**
 * Shortcut for the URI match rule of one login. The labels match the edit-page options.
 */
@Composable
fun LoginUriMatchDialog(
    cipherId: String,
    selectedName: String,
    defaultOptionLabel: Text,
    onDismissRequest: () -> Unit,
    onMatchSelected: (cipherId: String, matchName: String) -> Unit,
) {
    val defaultOption = defaultOptionLabel()
    BitwardenSelectionDialog(
        title = stringResource(id = BitwardenString.zh_uri_match_rule),
        onDismissRequest = onDismissRequest,
    ) {
        UriMatchDisplayType.entries.forEach { matchType ->
            BitwardenSelectionRow(
                text = matchType.displayLabel(defaultUriOption = defaultOption),
                isSelected = matchType.name == selectedName,
                onClick = { onMatchSelected(cipherId, matchType.name) },
            )
        }
    }
}
