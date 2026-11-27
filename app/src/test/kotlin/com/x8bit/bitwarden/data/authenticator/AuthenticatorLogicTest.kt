package com.x8bit.bitwarden.data.authenticator

import com.x8bit.bitwarden.data.authenticator.importing.decodeMigrationPayload
import com.x8bit.bitwarden.data.authenticator.importing.decodeMigrationUri
import com.x8bit.bitwarden.data.authenticator.importing.parseAuthenticatorImport
import com.x8bit.bitwarden.data.authenticator.match.bestAuthenticatorForUrl
import com.x8bit.bitwarden.data.authenticator.match.entryMatchesHost
import com.x8bit.bitwarden.data.authenticator.mcp.deriveMcpKey
import com.x8bit.bitwarden.data.authenticator.mcp.mcpAad
import com.x8bit.bitwarden.data.authenticator.mcp.normalizeAlias
import com.x8bit.bitwarden.data.authenticator.mcp.openField
import com.x8bit.bitwarden.data.authenticator.mcp.sealField
import com.x8bit.bitwarden.data.authenticator.meta.joinAuthenticatorNotes
import com.x8bit.bitwarden.data.authenticator.meta.kindFromStored
import com.x8bit.bitwarden.data.authenticator.meta.splitAuthenticatorNotes
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorAlgorithm
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorMeta
import com.x8bit.bitwarden.data.authenticator.otp.currentCode
import com.x8bit.bitwarden.data.authenticator.otp.encodeBase32
import com.x8bit.bitwarden.data.authenticator.otp.nextTotpCode
import com.x8bit.bitwarden.data.authenticator.otp.parseOtpAuthUri
import com.x8bit.bitwarden.data.authenticator.otp.steamCode
import com.x8bit.bitwarden.data.authenticator.otp.totpCode
import com.x8bit.bitwarden.data.authenticator.save.AutofillLoginDecision
import com.x8bit.bitwarden.data.authenticator.save.ExistingLoginSnapshot
import com.x8bit.bitwarden.data.authenticator.save.decideAutofillLoginSave
import com.x8bit.bitwarden.data.authenticator.server.ServerUrlResult
import com.x8bit.bitwarden.data.authenticator.server.normalizeServerUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthenticatorLogicTest {
    @Test
    fun `totp matches the RFC 6238 SHA1 vector`() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("287082", totpCode(secret, 59_000, 30, 6, AuthenticatorAlgorithm.SHA1))
        assertEquals(
            "94287082",
            totpCode(secret, 59_000, 30, 8, AuthenticatorAlgorithm.SHA1),
        )
    }

    @Test
    fun `steam code is five characters from the steam alphabet`() {
        val code = steamCode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 59_000)
        assertEquals(5, code?.length)
        assertTrue(code!!.all { it in "23456789BCDFGHJKMNPQRTVWXY" })
    }

    @Test
    fun `otpauth round trip keeps hotp counter and steam`() {
        val hotp = parseOtpAuthUri(
            "otpauth://hotp/GitHub:ada?secret=GEZDGNBVGY3TQOJQ&algorithm=SHA256&digits=8&counter=4",
        )
        assertEquals("ada", hotp?.name)
        assertEquals("GitHub", hotp?.issuer)
        assertEquals(AuthenticatorKind.HOTP, hotp?.kind)
        assertEquals(4, hotp?.counter)
        assertEquals(8, hotp?.digits)
        assertEquals(AuthenticatorAlgorithm.SHA256, hotp?.algorithm)

        val steam = parseOtpAuthUri(
            "otpauth://totp/Steam:player?secret=GEZDGNBVGY3TQOJQ&algorithm=steam&digits=5",
        )
        assertEquals(AuthenticatorKind.STEAM, steam?.kind)
    }

    @Test
    fun `notes trailer round trips host tags and trash`() {
        val meta = AuthenticatorMeta(
            kind = AuthenticatorKind.HOTP,
            host = "gitlab.com",
            tags = listOf("工作"),
            counter = 3,
            deletedAt = "2026-01-02T03:04:05Z",
        )
        val joined = joinAuthenticatorNotes("备注", meta, digits = 6)
        val split = splitAuthenticatorNotes(joined)
        assertEquals("备注", split.text)
        assertEquals(meta, split.meta)
        assertEquals(AuthenticatorKind.HOTP, kindFromStored(split.meta, split.hasTrailer, 6))
        assertEquals(AuthenticatorKind.STEAM, kindFromStored(AuthenticatorMeta(), false, 5))
    }

    @Test
    fun `import reads aegis 2fas bitwarden and rejects encrypted aegis`() {
        val aegis = """
            {"db":{"entries":[{"type":"totp","issuer":"GitHub","name":"ada","info":{"secret":"GEZDGNBVGY3TQOJQ","algo":"SHA1","digits":6,"period":30}}]}}
        """.trimIndent()
        val imported = parseAuthenticatorImport(aegis)
        assertEquals("aegis", imported.format)
        assertEquals("ada", imported.entries.single().name)
        assertEquals("github.com".let { "GitHub" }, imported.entries.single().issuer)

        val twoFas = """
            {"services":[{"name":"GitLab","secret":"GEZDGNBVGY3TQOJQ","otp":{"account":"ada","issuer":"GitLab","tokenType":"TOTP"}}]}
        """.trimIndent()
        assertEquals("2fas", parseAuthenticatorImport(twoFas).format)

        val encrypted = """{"header":{"slots":[]},"db":"ciphertext"}"""
        assertTrue(parseAuthenticatorImport(encrypted).encrypted)

        val bitwarden = """
            [{"name":"Example","login":{"totp":"otpauth://totp/Example:ada?secret=GEZDGNBVGY3TQOJQ&issuer=Example"}}]
        """.trimIndent()
        assertEquals("Example", parseAuthenticatorImport(bitwarden).entries.single().issuer)
    }

    @Test
    fun `google migration payload decodes one otp parameter`() {
        val secret = "Hello!".toByteArray()
        val name = "ada".toByteArray()
        val issuer = "GitHub".toByteArray()
        val otp = protoBytes(1, secret) +
            protoBytes(2, name) +
            protoBytes(3, issuer) +
            protoVarint(6, 2)
        val payload = protoBytes(1, otp)
        val entry = decodeMigrationPayload(payload).single()
        assertEquals(encodeBase32(secret), entry.secret)
        assertEquals("ada", entry.name)
        assertEquals("GitHub", entry.issuer)
        assertEquals(AuthenticatorKind.TOTP, entry.kind)
        val data = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        val fromUri = decodeMigrationUri("otpauth-migration://offline?data=$data").single()
        assertEquals(entry, fromUri)
    }

    @Test
    fun `host match prefers an exact host and a single favorite`() {
        val github = entry(id = "1", issuer = "GitHub", host = "github.com")
        val other = entry(id = "2", issuer = "GitHub", host = "gitlab.com")
        assertTrue(entryMatchesHost(github, "github.com"))
        assertEquals(
            github,
            bestAuthenticatorForUrl(listOf(github, other), "https://github.com/login"),
        )

        val loose = entry(id = "3", issuer = "github", host = "")
        val also = entry(id = "4", issuer = "GitHub", host = "github.com", favorite = true)
        assertEquals(also, bestAuthenticatorForUrl(listOf(loose, also), "https://github.com"))
    }

    @Test
    fun `save prompt updates only a changed password`() {
        val existing = listOf(
            ExistingLoginSnapshot("id", "ada", "old", "github.com"),
        )
        assertEquals(
            AutofillLoginDecision.SAVE_NEW,
            decideAutofillLoginSave("bob", "pw", "https://github.com", existing),
        )
        assertEquals(
            AutofillLoginDecision.UPDATE_PASSWORD,
            decideAutofillLoginSave("ada", "new", "https://github.com/login", existing),
        )
        assertEquals(
            AutofillLoginDecision.IGNORE,
            decideAutofillLoginSave("ada", "old", "https://github.com", existing),
        )
    }

    @Test
    fun `server url requires https and starts empty`() {
        assertEquals(
            "请填写服务器地址。",
            (normalizeServerUrl("  ") as ServerUrlResult.Rejected).message,
        )
        val http = normalizeServerUrl("http://vault.example.com") as ServerUrlResult.Rejected
        assertTrue(http.message.contains("HTTPS"))
        assertEquals(
            "https://vault.example.com:8443",
            (normalizeServerUrl("vault.example.com:8443/") as ServerUrlResult.Accepted).url,
        )
    }

    @Test
    fun `mcp seal opens with the same token salt and alias`() {
        assertNull(normalizeAlias("has space"))
        assertEquals("gitlab", normalizeAlias(" GitLab "))
        val key = deriveMcpKey(
            "vw_mcp_token",
            encodeBase64UrlForTest("salt-value-16b".toByteArray()),
        )
        val aad = mcpAad("user", "authenticator", "entry", "gitlab", "secret")
        val sealed = sealField(key, aad, "GEZDGNBVGY3TQOJQ", ByteArray(12) { it.toByte() })
        assertEquals("GEZDGNBVGY3TQOJQ", openField(key, aad, sealed))
    }

    @Test
    fun `next code is the following totp window`() {
        val entry = entry(id = "1", issuer = "GitHub", host = "github.com").copy(
            secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
            period = 30,
            digits = 6,
        )
        assertEquals("287082", currentCode(entry, 59_000))
        assertEquals(
            nextTotpCode(entry, 59_000),
            totpCode(entry.secret, 89_000, 30, 6, entry.algorithm),
        )
    }

    private fun entry(
        id: String,
        issuer: String,
        host: String,
        favorite: Boolean = false,
    ) = AuthenticatorEntryView(
        id = id,
        name = "ada",
        issuer = issuer,
        secret = "GEZDGNBVGY3TQOJQ",
        algorithm = AuthenticatorAlgorithm.SHA1,
        digits = 6,
        period = 30,
        notes = "",
        favorite = favorite,
        sortOrder = 0,
        revisionDate = "",
        creationDate = "",
        host = host,
    )

    private fun protoBytes(field: Int, value: ByteArray): ByteArray {
        val tag = (field shl 3) or 2
        return byteArrayOf(tag.toByte()) + protoVarintRaw(value.size) + value
    }

    private fun protoVarint(field: Int, value: Int): ByteArray {
        val tag = field shl 3
        return byteArrayOf(tag.toByte()) + protoVarintRaw(value)
    }

    private fun protoVarintRaw(value: Int): ByteArray = byteArrayOf(value.toByte())

    private fun encodeBase64UrlForTest(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
