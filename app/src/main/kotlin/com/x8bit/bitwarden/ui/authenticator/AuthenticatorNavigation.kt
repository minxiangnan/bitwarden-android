package com.x8bit.bitwarden.ui.authenticator

import androidx.navigation3.runtime.EntryProviderScope
import com.bitwarden.ui.platform.navigation.NavigationController
import com.bitwarden.ui.platform.navigation.util.rootPushEntry
import com.bitwarden.ui.platform.navigation.util.slideUpEntry

/**
 * Authenticator tab destinations.
 */
fun EntryProviderScope<AuthenticatorGraph>.authenticatorDestinations(
    navigationController: NavigationController<AuthenticatorGraph>,
) {
    rootPushEntry<AuthenticatorGraph.ListRoute> {
        AuthenticatorListScreen(
            trash = false,
            onScan = { navigationController.navigate(AuthenticatorGraph.ScanRoute) },
            onAdd = { navigationController.navigate(AuthenticatorGraph.EditRoute("")) },
            onEdit = { navigationController.navigate(AuthenticatorGraph.EditRoute(it)) },
            onTrash = { navigationController.navigate(AuthenticatorGraph.TrashRoute) },
            onMcp = { navigationController.navigate(AuthenticatorGraph.McpRoute) },
            onBack = {},
        )
    }
    slideUpEntry<AuthenticatorGraph.TrashRoute> {
        AuthenticatorListScreen(
            trash = true,
            onScan = {},
            onAdd = {},
            onEdit = { navigationController.navigate(AuthenticatorGraph.EditRoute(it)) },
            onTrash = {},
            onMcp = {},
            onBack = { navigationController.navigateBack() },
        )
    }
    slideUpEntry<AuthenticatorGraph.EditRoute> { route ->
        AuthenticatorEditScreen(
            entryId = route.entryId,
            onBack = { navigationController.navigateBack() },
        )
    }
    slideUpEntry<AuthenticatorGraph.ScanRoute> {
        AuthenticatorScanScreen(
            onClose = { navigationController.navigateBack() },
            onManual = {
                navigationController.navigateBack()
                navigationController.navigate(AuthenticatorGraph.EditRoute(""))
            },
        )
    }
    slideUpEntry<AuthenticatorGraph.McpRoute> {
        AuthenticatorMcpScreen(onBack = { navigationController.navigateBack() })
    }
}
