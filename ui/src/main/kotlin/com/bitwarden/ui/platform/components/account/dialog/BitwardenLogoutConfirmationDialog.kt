package com.bitwarden.ui.platform.components.account.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bitwarden.ui.platform.components.account.model.AccountSummary
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.resource.BitwardenString

/**
 * A reusable dialog for confirming whether the user wants to log out.
 *
 * @param onDismissRequest A callback for when the dialog is requesting dismissal.
 * @param onConfirmClick A callback for when the log out confirmation button is clicked.
 * @param accountSummary Optional account information that may be used to provide additional
 * information.
 */
@Composable
fun BitwardenLogoutConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit,
    accountSummary: AccountSummary? = null,
) {
    val baseConfirmationMessage = stringResource(id = BitwardenString.logout_confirmation)
    val unsynced = stringResource(id = BitwardenString.zh_logout_unsynced)
    val message = accountSummary
        ?.let { "$baseConfirmationMessage\n\n${it.email}\n${it.environmentLabel}\n\n$unsynced" }
        ?: "$baseConfirmationMessage\n\n$unsynced"
    BitwardenTwoButtonDialog(
        title = accountSummary?.email ?: stringResource(id = BitwardenString.log_out),
        message = message,
        confirmButtonText = stringResource(id = BitwardenString.log_out),
        confirmTextColor = com.bitwarden.ui.platform.theme.BitwardenTheme.colorScheme.status.error,
        onConfirmClick = onConfirmClick,
        dismissButtonText = stringResource(id = BitwardenString.cancel),
        onDismissClick = onDismissRequest,
        onDismissRequest = onDismissRequest,
    )
}
