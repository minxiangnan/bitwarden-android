package com.x8bit.bitwarden.data.authenticator.group

import com.x8bit.bitwarden.data.authenticator.meta.joinAuthenticatorNotes
import com.x8bit.bitwarden.data.authenticator.meta.splitAuthenticatorNotes
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorAlgorithm
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorMeta
import com.x8bit.bitwarden.ui.authenticator.AuthenticatorLine
import com.x8bit.bitwarden.ui.authenticator.AuthenticatorRow
import com.x8bit.bitwarden.ui.authenticator.GroupDrop
import com.x8bit.bitwarden.ui.authenticator.groupDrop
import com.x8bit.bitwarden.ui.authenticator.shiftAuthenticatorLine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthenticatorGroupsTest {
    @Test
    fun `notes keep unknown keys and the group when the order changes`() {
        val extras = buildJsonObject {
            put("custom.flag", true)
            put("zh.totp.group", "work")
        }
        val joined = joinAuthenticatorNotes(
            text = "备注",
            meta = AuthenticatorMeta(order = "1500"),
            digits = 6,
            extras = extras,
        )
        val split = splitAuthenticatorNotes(joined)
        assertEquals("备注", split.text)
        assertEquals("1500", split.meta.order)
        assertEquals(JsonPrimitive(true), split.extras["custom.flag"])
        assertEquals(JsonPrimitive("work"), split.extras["zh.totp.group"])
        val again = joinAuthenticatorNotes("备注", split.meta.copy(order = "1750"), 6, split.extras)
        val round = splitAuthenticatorNotes(again)
        assertEquals("1750", round.meta.order)
        assertEquals(JsonPrimitive("work"), round.extras["zh.totp.group"])
        assertEquals(JsonPrimitive(true), round.extras["custom.flag"])
    }

    @Test
    fun `catalog round trip keeps extra keys and hides the directory`() {
        val group = AuthenticatorGroupRecord(
            id = "work",
            name = "工作",
            order = 1000.0,
            extras = buildJsonObject { put("tint", "warm") },
        )
        val previous = buildJsonObject { put("custom.flag", true) }
        val trailer = catalogExtras(listOf(group), previous)
        val entry = entry("catalog", "分组目录", trailer)
        assertTrue(isGroupCatalog(entry))
        val listed = listOf(entry, entry("code", "银行", JsonObject(emptyMap())))
        val (catalog, groups) = readGroupCatalog(listed)
        assertEquals("catalog", catalog?.id)
        assertEquals("工作", groups.single().name)
        assertEquals("1000", groups.single().let { formatOrder(it.order) })
        assertEquals(JsonPrimitive("warm"), groups.single().extras["tint"])
        val again = catalogExtras(groups, catalog!!.extras)
        assertEquals(JsonPrimitive(true), again["custom.flag"])
    }

    @Test
    fun `sections put ungrouped last and stay flat without groups`() {
        val work = AuthenticatorGroupRecord("work", "工作", 1000.0)
        val home = AuthenticatorGroupRecord("home", "生活", 2000.0)
        val entries = listOf(
            entry("a", "甲", group("work"), order = "2"),
            entry("b", "乙", group("missing")),
            entry("c", "丙", JsonObject(emptyMap()), order = "1"),
            entry("d", "丁", group("home"), order = "3"),
        )
        val sections = authenticatorSections(
            entries,
            listOf(work, home),
            setOf("home"),
            searching = false,
        )
        assertEquals(listOf("work", "home", ""), sections.map { it.id })
        assertEquals(listOf("甲"), sections[0].entries.map { it.name })
        assertTrue(sections[1].collapsed)
        assertEquals(UNGROUPED_TITLE, sections[2].title)
        assertEquals(listOf("乙", "丙"), sections[2].entries.map { it.name })
        val flat = authenticatorSections(entries, emptyList(), emptySet(), searching = false)
        assertEquals("", flat.single().title)
        assertEquals(4, flat.single().entries.size)
    }

    @Test
    fun `search spans groups and drops empty sections`() {
        val work = AuthenticatorGroupRecord("work", "工作", 1000.0)
        val entries = listOf(
            entry("a", "邮箱", group("work")),
            entry("b", "银行", JsonObject(emptyMap())),
        )
        val found = authenticatorSections(
            entries.filter { it.name.contains("银行") },
            listOf(work),
            setOf(""),
            searching = true,
        )
        assertEquals(listOf(""), found.map { it.id })
        assertEquals(false, found.single().collapsed)
    }

    @Test
    fun `dragging across a header changes the group`() {
        val lines = listOf(
            AuthenticatorLine.Header("work", "工作", 1, false),
            AuthenticatorLine.Code(row("a", "work")),
            AuthenticatorLine.Header("", "未分组", 1, false),
            AuthenticatorLine.Code(row("b", null)),
        )
        val moved = shiftAuthenticatorLine(lines, "b", 1)
        val drop = groupDrop(moved, "b", null)
        assertTrue(drop is GroupDrop.Move)
        assertEquals("work", (drop as GroupDrop.Move).groupId)
        val same = groupDrop(lines, "a", "work")
        assertEquals(GroupDrop.Reorder(0), same)
    }

    @Test
    fun `names are trimmed unique and at most forty characters`() {
        assertNull(normalizeGroupName("   "))
        assertNull(normalizeGroupName("a".repeat(41)))
        assertEquals("工作 组", normalizeGroupName("  工作   组 "))
        val groups = listOf(AuthenticatorGroupRecord("work", "工作", 1000.0))
        assertTrue(groupNameTaken(groups, "工作"))
        assertEquals(false, groupNameTaken(groups, "工作", exceptId = "work"))
        assertEquals(2000.0, nextGroupOrder(groups))
    }

    private fun formatOrder(value: Double): String =
        com.x8bit.bitwarden.data.authenticator.order.formatTotpOrder(value)

    private fun group(id: String): JsonObject = buildJsonObject { put("zh.totp.group", id) }

    private fun entry(
        id: String,
        name: String,
        extras: JsonObject,
        order: String? = null,
    ) = AuthenticatorEntryView(
        id = id,
        name = name,
        issuer = "",
        secret = "SECRET",
        algorithm = AuthenticatorAlgorithm.SHA1,
        digits = 6,
        period = 30,
        notes = "",
        favorite = false,
        sortOrder = 0,
        revisionDate = "2026-01-01T00:00:00.000Z",
        creationDate = "2026-01-01T00:00:00.000Z",
        order = order,
        extras = extras,
    )

    private fun row(id: String, groupId: String?) = AuthenticatorRow(
        id = id,
        title = id,
        notes = "",
        code = "123 456",
        nextCode = null,
        seconds = 20,
        period = 30,
        favorite = false,
        alias = null,
        groupId = groupId,
        kindLabel = "",
    )
}
