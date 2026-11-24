package com.x8bit.bitwarden.data.authenticator.match

import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.otp.entryKind
import com.x8bit.bitwarden.data.authenticator.otp.toOtpAuthUri
import java.net.URI

private val SIMPLE_SUFFIXES = setOf("com", "org", "net", "cn", "io")

/**
 * List order shared with the extension. Favorites stay a filter, not a sort key.
 */
enum class AuthenticatorSort {
    MANUAL,
    NAME,
    ISSUER,
}

/**
 * Hostname of a page or login URI, without the scheme.
 */
fun pageHostname(url: String): String? {
    val candidate = if (url.contains("://")) url else "https://$url"
    return runCatching { URI(candidate).host?.lowercase() }.getOrNull()?.takeIf { it.isNotEmpty() }
}

/**
 * Exact hostname when the entry stores one. Otherwise the issuer or name must be that
 * hostname, or a label plus one common suffix such as github.com.
 */
@Suppress("LoopWithTooManyJumpStatements", "MagicNumber")
fun entryMatchesHost(entry: AuthenticatorEntryView, hostname: String): Boolean {
    val host = entry.host.trim().lowercase()
    if (host.isNotEmpty()) return host == hostname
    val bare = if (hostname.startsWith("www.")) hostname.removePrefix("www.") else hostname
    for (raw in listOf(entry.issuer, entry.name)) {
        val label = raw.trim().lowercase().replace(Regex("\\s+"), "")
        if (label.length < 3) continue
        if (label == hostname || label == bare) return true
        if (label.contains('.')) continue
        val suffix = bare.removePrefix("$label.")
        if (bare.startsWith("$label.") && suffix in SIMPLE_SUFFIXES) return true
    }
    return false
}

/**
 * One matching code. Several matches only resolve when exactly one is a favorite.
 */
fun bestAuthenticatorForUrl(
    entries: List<AuthenticatorEntryView>,
    url: String,
): AuthenticatorEntryView? {
    val hostname = pageHostname(url) ?: return null
    val matches = entries.filter { entry ->
        !entry.undecryptable &&
            entry.deletedAt == null &&
            entryKind(entry.kind, entry.digits) != AuthenticatorKind.HOTP &&
            entryMatchesHost(entry, hostname)
    }
    if (matches.size == 1) return matches.first()
    if (matches.size > 1) {
        val favorites = matches.filter { it.favorite }
        if (favorites.size == 1) return favorites.first()
    }
    return null
}

/**
 * Seed the vault TOTP service can use. HOTP is omitted so a fill does not burn the counter.
 */
fun totpSeedForEntry(entry: AuthenticatorEntryView): String? {
    if (entry.undecryptable || entry.secret.isBlank()) return null
    return when (entryKind(entry.kind, entry.digits)) {
        AuthenticatorKind.HOTP -> null
        AuthenticatorKind.STEAM -> "steam://${entry.secret.replace(Regex("\\s+"), "").uppercase()}"
        AuthenticatorKind.TOTP -> toOtpAuthUri(entry)
    }
}

/**
 * Filters and sorts the live list. Trash entries are excluded.
 */
fun filterAuthenticatorEntries(
    entries: List<AuthenticatorEntryView>,
    query: String,
    favoritesOnly: Boolean,
    sort: AuthenticatorSort,
    group: String?,
): List<AuthenticatorEntryView> {
    val term = query.trim().lowercase()
    return entries
        .filter { it.deletedAt == null }
        .filter { !favoritesOnly || it.favorite }
        .filter { group == null || it.issuer.equals(group, ignoreCase = true) || group in it.tags }
        .filter { entry ->
            term.isEmpty() ||
                entry.name.lowercase().contains(term) ||
                entry.issuer.lowercase().contains(term) ||
                entry.notes.lowercase().contains(term) ||
                entry.host.lowercase().contains(term) ||
                entry.tags.any { it.lowercase().contains(term) }
        }
        .sortedWith { left, right -> compareBySort(left, right, sort) }
}

private fun compareBySort(
    left: AuthenticatorEntryView,
    right: AuthenticatorEntryView,
    sort: AuthenticatorSort,
): Int = when (sort) {
    AuthenticatorSort.NAME ->
        left.name.compareTo(right.name, ignoreCase = true)
            .takeIf { it != 0 }
            ?: left.issuer.compareTo(right.issuer, ignoreCase = true)
    AuthenticatorSort.ISSUER ->
        left.issuer.compareTo(right.issuer, ignoreCase = true)
            .takeIf { it != 0 }
            ?: left.name.compareTo(right.name, ignoreCase = true)
    AuthenticatorSort.MANUAL ->
        left.sortOrder.compareTo(right.sortOrder).takeIf { it != 0 }
            ?: left.name.compareTo(right.name, ignoreCase = true)
}

/**
 * Remembers a recently copied id, newest first.
 */
fun rememberRecentId(ids: List<String>, id: String, limit: Int = 5): List<String> =
    listOf(id) + ids.filter { it != id }.take(limit - 1)
