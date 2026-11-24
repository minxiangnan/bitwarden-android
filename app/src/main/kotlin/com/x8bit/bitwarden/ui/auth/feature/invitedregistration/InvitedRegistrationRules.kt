package com.x8bit.bitwarden.ui.auth.feature.invitedregistration

internal const val MIN_INVITE_PASSWORD_LENGTH = 12

/**
 * Why an invited-account password cannot be submitted.
 */
enum class InvitePasswordProblem {
    TOO_SHORT,
    MISMATCH,
}

/**
 * Checks the master password pair before any network call.
 */
fun checkInvitePassword(password: String, confirm: String): InvitePasswordProblem? = when {
    password.length < MIN_INVITE_PASSWORD_LENGTH -> InvitePasswordProblem.TOO_SHORT
    password != confirm -> InvitePasswordProblem.MISMATCH
    else -> null
}

/**
 * The server rejects emails that were not invited, and emails that already exist,
 * with the same English message. Both should be explained in Chinese.
 */
fun isInviteRejected(serverMessage: String?): Boolean {
    val message = serverMessage.orEmpty()
    return message.contains("not allowed", ignoreCase = true) ||
        message.contains("already exists", ignoreCase = true)
}
