package com.x8bit.bitwarden.ui.vault.feature.itemlisting.util

import com.bitwarden.vault.CipherType
import com.bitwarden.vault.UriMatchType
import com.x8bit.bitwarden.data.vault.datasource.sdk.model.createMockCipherView
import com.x8bit.bitwarden.data.vault.datasource.sdk.model.createMockUriView
import com.x8bit.bitwarden.ui.vault.feature.addedit.model.UriMatchDisplayType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoginUriMatchTest {

    @Test
    fun `withLoginUriMatch writes the same rule onto every uri`() {
        val cipher = createMockCipherView(number = 1).let { view ->
            val login = requireNotNull(view.login)
            view.copy(
                login = login.copy(
                    uris = listOf(
                        createMockUriView(number = 1).copy(match = null),
                        createMockUriView(number = 2).copy(match = UriMatchType.DOMAIN),
                    ),
                ),
            )
        }

        val updated = cipher.withLoginUriMatch(UriMatchType.EXACT)

        assertEquals(
            listOf(UriMatchType.EXACT, UriMatchType.EXACT),
            updated.login?.uris?.map { it.match },
        )
    }

    @Test
    fun `withLoginUriMatch clears every uri when the choice is the global default`() {
        val cipher = createMockCipherView(number = 1)
        val updated = cipher.withLoginUriMatch(match = null)

        assertEquals(
            listOf(null),
            updated.login?.uris?.map { it.match },
        )
    }

    @Test
    fun `withLoginUriMatch leaves a non login cipher unchanged`() {
        val cipher = createMockCipherView(number = 1, cipherType = CipherType.CARD)

        assertEquals(cipher, cipher.withLoginUriMatch(UriMatchType.NEVER))
    }

    @Test
    fun `currentUriMatch uses the shared rule and falls back to the first when they differ`() {
        val shared = listOf(
            createMockUriView(number = 1).copy(match = UriMatchType.STARTS_WITH),
            createMockUriView(number = 2).copy(match = UriMatchType.STARTS_WITH),
        )
        val mixed = listOf(
            createMockUriView(number = 1).copy(match = null),
            createMockUriView(number = 2).copy(match = UriMatchType.HOST),
        )

        assertEquals(UriMatchDisplayType.STARTS_WITH, shared.currentUriMatch())
        assertEquals(UriMatchDisplayType.DEFAULT, mixed.currentUriMatch())
        assertNull(uriMatchFromName("not-a-rule"))
        assertNull(uriMatchFromName(UriMatchDisplayType.DEFAULT.name))
        assertEquals(UriMatchType.HOST, uriMatchFromName(UriMatchDisplayType.HOST.name))
    }
}
