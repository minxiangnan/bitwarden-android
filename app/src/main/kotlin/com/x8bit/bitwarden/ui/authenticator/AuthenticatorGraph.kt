package com.x8bit.bitwarden.ui.authenticator

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Authenticator tab routes.
 */
@Serializable
sealed class AuthenticatorGraph : NavKey {
    /**
     * The live code list.
     */
    @Serializable
    data object ListRoute : AuthenticatorGraph()

    /**
     * Trash.
     */
    @Serializable
    data object TrashRoute : AuthenticatorGraph()

    /**
     * Add when [entryId] is empty, otherwise edit.
     */
    @Serializable
    data class EditRoute(val entryId: String) : AuthenticatorGraph()

    /**
     * Camera scan.
     */
    @Serializable
    data object ScanRoute : AuthenticatorGraph()

    /**
     * MCP access token.
     */
    @Serializable
    data object McpRoute : AuthenticatorGraph()
}
