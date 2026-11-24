package com.x8bit.bitwarden.data.authenticator.save

import com.x8bit.bitwarden.data.authenticator.match.pageHostname

/**
 * What the autofill save prompt should do for a submitted login.
 */
enum class AutofillLoginDecision {
    /** No login on this host uses this username. Offer to save a new login. */
    SAVE_NEW,

    /** The username exists and the password changed. Update the password only. */
    UPDATE_PASSWORD,

    /** Username and password already match. Do not prompt. */
    IGNORE,
}

/**
 * A login already in the vault, reduced to the fields the save prompt compares.
 */
data class ExistingLoginSnapshot(
    val id: String,
    val username: String,
    val password: String,
    val host: String,
)

/**
 * Chooses save, password-only update, or silence.
 *
 * Matching is by exact host and username. Notes and TOTP on the existing login are left alone
 * by the caller when the decision is [AutofillLoginDecision.UPDATE_PASSWORD].
 */
fun decideAutofillLoginSave(
    username: String?,
    password: String?,
    uri: String?,
    existing: List<ExistingLoginSnapshot>,
): AutofillLoginDecision {
    val host = uri?.let { pageHostname(it) }
    val submittedUser = username.orEmpty()
    val submittedPassword = password.orEmpty()
    val sameHost = existing.filter { host == null || it.host.equals(host, ignoreCase = true) }
    val match = sameHost.firstOrNull { it.username == submittedUser }
        ?: return AutofillLoginDecision.SAVE_NEW
    return if (match.password == submittedPassword) {
        AutofillLoginDecision.IGNORE
    } else {
        AutofillLoginDecision.UPDATE_PASSWORD
    }
}

/**
 * Id of the login whose password should be replaced, when the decision is an update.
 */
fun loginIdToUpdate(
    username: String?,
    uri: String?,
    existing: List<ExistingLoginSnapshot>,
): String? {
    val host = uri?.let { pageHostname(it) }
    return existing.firstOrNull { snapshot ->
        (host == null || snapshot.host.equals(host, ignoreCase = true)) &&
            snapshot.username == username.orEmpty()
    }?.id
}
