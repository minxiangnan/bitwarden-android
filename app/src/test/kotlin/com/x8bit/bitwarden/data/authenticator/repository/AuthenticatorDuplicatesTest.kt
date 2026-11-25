package com.x8bit.bitwarden.data.authenticator.repository

import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorAlgorithm
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthenticatorDuplicatesTest {
    @Test
    fun keepsTheNewerRevisionAndTheSmallerIdWhenTied() {
        val older = entry(id = "b", revision = "2026-11-24T00:00:00.000000Z")
        val newer = entry(id = "a", revision = "2026-11-24T00:00:01.000000Z")
        val tiedLow = entry(id = "a", revision = "2026-11-24T00:00:02.000000Z")
        val tiedHigh = entry(id = "b", revision = "2026-11-24T00:00:02.000000Z")
        val trashed = entry(
            id = "c",
            revision = "2026-11-24T00:00:03.000000Z",
            deletedAt = "2026-11-24T00:00:03Z",
        )

        assertEquals(listOf("b"), duplicateAuthenticatorIds(listOf(older, newer)))
        assertEquals(listOf("b"), duplicateAuthenticatorIds(listOf(tiedHigh, tiedLow)))
        assertEquals(emptyList<String>(), duplicateAuthenticatorIds(listOf(newer, trashed)))
    }

    private fun entry(
        id: String,
        revision: String,
        deletedAt: String? = null,
    ) = AuthenticatorEntryView(
        id = id,
        name = "Ada",
        issuer = "Example",
        secret = "JBSWY3DPEHPK3PXP",
        algorithm = AuthenticatorAlgorithm.SHA1,
        digits = 6,
        period = 30,
        notes = "",
        favorite = false,
        sortOrder = 0,
        revisionDate = revision,
        creationDate = revision,
        deletedAt = deletedAt,
    )
}
