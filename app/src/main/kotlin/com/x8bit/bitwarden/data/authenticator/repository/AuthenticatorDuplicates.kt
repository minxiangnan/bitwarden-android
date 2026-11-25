package com.x8bit.bitwarden.data.authenticator.repository

import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView

/**
 * Ids to delete when two live entries decrypt to the same code.
 *
 * The newest [AuthenticatorEntryView.revisionDate] wins. An equal revision keeps the smaller id,
 * so both devices delete the same row.
 */
internal fun duplicateAuthenticatorIds(entries: List<AuthenticatorEntryView>): List<String> {
    val groups = LinkedHashMap<String, MutableList<AuthenticatorEntryView>>()
    for (entry in entries) {
        if (entry.undecryptable || entry.secret.isBlank() || entry.deletedAt != null) continue
        val key = listOf(
            entry.secret.trim().uppercase(),
            entry.issuer.trim().lowercase(),
            entry.name.trim(),
            entry.algorithm.name,
            entry.digits.toString(),
            entry.period.toString(),
        ).joinToString(separator = "|")
        groups.getOrPut(key) { mutableListOf() }.add(entry)
    }
    val drop = ArrayList<String>()
    for (group in groups.values) {
        if (group.size < 2) continue
        val keep = group.sortedWith(
            compareByDescending<AuthenticatorEntryView> { it.revisionDate }.thenBy { it.id },
        ).first()
        group.filter { it.id != keep.id }.mapTo(drop) { it.id }
    }
    return drop
}
