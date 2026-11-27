package com.x8bit.bitwarden.data.authenticator.order

import com.x8bit.bitwarden.data.vault.datasource.sdk.model.createMockCipherView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TotpOrderTest {
    @Test
    fun `a drag between numbered neighbors changes only that row`() {
        val slots = listOf(
            slot("a", "0", "A"),
            slot("b", "1024", "B"),
            slot("c", "2048", "C"),
        )
        assertEquals(mapOf("c" to "512"), ordersAfterMove(slots, "c", 1))
    }

    @Test
    fun `moving to the top uses one step before the next number`() {
        val slots = listOf(slot("a", "1024", "A"), slot("b", "2048", "B"))
        assertEquals(mapOf("b" to "0"), ordersAfterMove(slots, "b", 0))
    }

    @Test
    fun `a gap that is too small renumbers the whole list`() {
        val slots = listOf(
            slot("a", "1", "A"),
            slot("b", "1.0000001", "B"),
            slot("c", "3", "C"),
        )
        assertEquals(
            mapOf("a" to "0", "c" to "1024", "b" to "2048"),
            ordersAfterMove(slots, "c", 1),
        )
    }

    @Test
    fun `neighbors without numbers are numbered together`() {
        val slots = listOf(slot("a", null, "A"), slot("b", null, "B"), slot("c", null, "C"))
        assertEquals(
            mapOf("c" to "0", "a" to "1024", "b" to "2048"),
            ordersAfterMove(slots, "c", 0),
        )
    }

    @Test
    fun `unset and non numeric orders sort after numbered names`() {
        assertEquals(
            -1,
            compareTotpOrder("1", "Zulu", null, "Alpha"),
        )
        assertEquals(
            0,
            compareTotpOrder("nope", "Ada", null, "ada"),
        )
        assertEquals("1.5", formatTotpOrder(1.5))
        assertEquals("1024", formatTotpOrder(1024.0))
        assertNull(parseTotpOrder(" "))
        assertNull(parseTotpOrder("nan"))
    }

    @Test
    fun `login ciphers keep one hidden order field`() {
        val cipher = createMockCipherView(
            number = 1,
            totp = "otpauth://totp/Example:ada?secret=GEZDGNBVGY3TQOJQ&issuer=Example",
        )
        assertNull(cipher.totpOrderValue())
        assertEquals(
            "GEZDGNBVGY3TQOJQ",
            totpSecretKey(cipher.login?.totp),
        )
        val once = cipher.withTotpOrder("1.5")
        val twice = once.withTotpOrder("2")
        assertEquals("2", twice.totpOrderValue())
        assertEquals(1, twice.fields.orEmpty().count { it.name == TOTP_ORDER_FIELD })
        assertEquals(cipher.fields.orEmpty().size + 1, twice.fields.orEmpty().size)
    }
}

private fun slot(id: String, order: String?, name: String) =
    TotpOrderSlot(id = id, order = order, name = name)
