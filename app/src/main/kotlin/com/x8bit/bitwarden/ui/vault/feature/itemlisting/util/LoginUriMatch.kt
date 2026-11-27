package com.x8bit.bitwarden.ui.vault.feature.itemlisting.util

import com.bitwarden.vault.CipherView
import com.bitwarden.vault.LoginUriView
import com.bitwarden.vault.UriMatchType
import com.x8bit.bitwarden.ui.vault.feature.addedit.model.UriMatchDisplayType
import com.x8bit.bitwarden.ui.vault.feature.addedit.util.toDisplayMatchType
import com.x8bit.bitwarden.ui.vault.feature.addedit.util.toUriMatchType

/**
 * Writes [match] onto every URI of this login. A null match means "use the global default".
 * Ciphers that are not logins are returned unchanged.
 */
fun CipherView.withLoginUriMatch(match: UriMatchType?): CipherView {
    val loginView = login ?: return this
    val uriViews = loginView.uris ?: return this
    return copy(
        login = loginView.copy(
            uris = uriViews.map { uriView -> uriView.copy(match = match) },
        ),
    )
}

/**
 * The match shown in the shortcut. When every URI uses the same rule, that rule is returned.
 * Mixed rules fall back to the first URI so the dialog still has a selection.
 */
fun List<LoginUriView>?.currentUriMatch(): UriMatchDisplayType {
    val matches = this.orEmpty().map { it.match }
    if (matches.isEmpty()) return UriMatchDisplayType.DEFAULT
    return (matches.distinct().singleOrNull() ?: matches.first()).toDisplayMatchType()
}

/**
 * Parses a dialog selection back into the value stored on a cipher URI.
 */
fun uriMatchFromName(name: String): UriMatchType? =
    UriMatchDisplayType.entries.firstOrNull { it.name == name }?.toUriMatchType()
