package com.x8bit.bitwarden.ui.auth.feature.invitedregistration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvitedRegistrationRulesTest {
    @Test
    fun `short password is rejected before the network call`() {
        assertEquals(
            InvitePasswordProblem.TOO_SHORT,
            checkInvitePassword("short-pass", "short-pass"),
        )
    }

    @Test
    fun `mismatched passwords are rejected`() {
        assertEquals(
            InvitePasswordProblem.MISMATCH,
            checkInvitePassword("correct-horse-battery", "correct-horse-staple"),
        )
    }

    @Test
    fun `a matching password of twelve characters is accepted`() {
        assertEquals(null, checkInvitePassword("correct-horse", "correct-horse"))
    }

    @Test
    fun `the closed-signup error is the invite rejection`() {
        assertTrue(isInviteRejected("Registration not allowed or user already exists"))
        assertTrue(isInviteRejected("User already exists"))
        assertFalse(isInviteRejected("Invalid master password"))
        assertFalse(isInviteRejected(null))
    }
}
