@file:Suppress("TooManyFunctions")

package com.x8bit.bitwarden.data.authenticator.group

import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.order.TotpOrderSlot
import com.x8bit.bitwarden.data.authenticator.order.formatTotpOrder
import com.x8bit.bitwarden.data.authenticator.order.ordersAfterMove
import com.x8bit.bitwarden.data.authenticator.order.parseTotpOrder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/** Shared with the extension. See docs/zh-authenticator-groups.md. */
const val GROUP_CATALOG_KEY: String = "zh.totp.catalog"
const val GROUP_CATALOG_VALUE: String = "groups"
const val GROUP_FIELD: String = "zh.totp.group"
const val GROUP_LIST_KEY: String = "groups"
const val GROUP_CATALOG_SECRET: String = "ZHGROUPCATALOG"
const val GROUP_CATALOG_NAME: String = "分组目录"
const val UNGROUPED_TITLE: String = "未分组"
const val GROUP_NAME_MAX: Int = 40
private const val GROUP_ID_MAX: Int = 64
private const val ORDER_STEP: Double = 1000.0

private val GROUP_OWN_KEYS = setOf("id", "name", "order")

/**
 * One named group. [extras] are unknown keys on the group object and are written back.
 */
data class AuthenticatorGroupRecord(
    val id: String,
    val name: String,
    val order: Double,
    val extras: JsonObject = JsonObject(emptyMap()),
)

/**
 * One section of the list. An empty [id] is 未分组. [title] is empty when the list is flat.
 */
data class AuthenticatorSection(
    val id: String,
    val title: String,
    val entries: List<AuthenticatorEntryView>,
    val collapsed: Boolean,
    val ungrouped: Boolean,
)

/**
 * True when this entry is the hidden group directory, not a code.
 */
fun isGroupCatalog(entry: AuthenticatorEntryView): Boolean {
    if (entry.undecryptable) return false
    return entry.extras.string(GROUP_CATALOG_KEY) == GROUP_CATALOG_VALUE
}

/**
 * Newest catalog wins. Older catalogs stay hidden and are not deleted.
 */
fun readGroupCatalog(
    entries: List<AuthenticatorEntryView>,
): Pair<AuthenticatorEntryView?, List<AuthenticatorGroupRecord>> {
    val catalogs = entries.filter(::isGroupCatalog).sortedWith(
        compareByDescending<AuthenticatorEntryView> { it.revisionDate }.thenBy { it.id },
    )
    val catalog = catalogs.firstOrNull()
    val groups = catalog?.extras?.get(GROUP_LIST_KEY)?.let { parseGroupRecords(it) }.orEmpty()
    return catalog to groups
}

/**
 * Group id to store, or null for 未分组. An unknown id is 未分组.
 */
fun groupIdOf(entry: AuthenticatorEntryView, groups: List<AuthenticatorGroupRecord>): String? {
    val raw = entry.extras.string(GROUP_FIELD)?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return raw.takeIf { id -> groups.any { it.id == id } }
}

/**
 * Copies trailer keys and sets or clears [GROUP_FIELD].
 */
fun notesExtrasWithGroup(extras: JsonObject, groupId: String?): JsonObject = buildJsonObject {
    extras.forEach { (key, value) ->
        if (key != GROUP_FIELD && key != "__proto__" && key != "constructor") put(key, value)
    }
    if (!groupId.isNullOrBlank()) put(GROUP_FIELD, JsonPrimitive(groupId))
}

/**
 * Catalog trailer. Unknown keys on the previous trailer and on each group stay.
 */
fun catalogExtras(
    groups: List<AuthenticatorGroupRecord>,
    previous: JsonObject = JsonObject(emptyMap()),
): JsonObject = buildJsonObject {
    previous.forEach { (key, value) ->
        val kept = key != GROUP_CATALOG_KEY && key != GROUP_LIST_KEY &&
            key != "__proto__" && key != "constructor"
        if (kept) {
            put(key, value)
        }
    }
    put(GROUP_CATALOG_KEY, JsonPrimitive(GROUP_CATALOG_VALUE))
    put(GROUP_LIST_KEY, buildJsonArray { groups.forEach { add(serializeGroup(it)) } })
}

/**
 * Trimmed name, or null when it is empty or longer than 40 characters.
 */
fun normalizeGroupName(value: String): String? {
    val name = value.trim().replace(Regex("\\s+"), " ")
    if (name.isEmpty() || name.length > GROUP_NAME_MAX) return null
    return name
}

/**
 * True when another group already uses [name], ignoring case.
 */
fun groupNameTaken(
    groups: List<AuthenticatorGroupRecord>,
    name: String,
    exceptId: String? = null,
): Boolean {
    val key = name.lowercase(Locale.getDefault())
    return groups.any { it.id != exceptId && it.name.lowercase(Locale.getDefault()) == key }
}

/**
 * Order for a group appended at the end.
 */
fun nextGroupOrder(groups: List<AuthenticatorGroupRecord>): Double =
    groups.maxOfOrNull { it.order }?.plus(ORDER_STEP) ?: ORDER_STEP

/**
 * New decimal orders after moving a group. Null when [movedId] is missing.
 */
fun reorderGroupRecords(
    groups: List<AuthenticatorGroupRecord>,
    movedId: String,
    destinationIndex: Int,
): List<AuthenticatorGroupRecord>? {
    if (groups.none { it.id == movedId }) return null
    val slots = groups.map { TotpOrderSlot(it.id, formatTotpOrder(it.order), it.name) }
    val writes = ordersAfterMove(slots, movedId, destinationIndex)
    if (writes.isEmpty()) return groups
    return groups.map { group ->
        val order = writes[group.id]?.let(::parseTotpOrder)
        if (order == null) group else group.copy(order = order)
    }.sortedWith(groupComparator())
}

/**
 * Named groups in catalog order, then 未分组. No named groups means one flat list.
 * Search expands everything and drops empty sections.
 */
fun authenticatorSections(
    entries: List<AuthenticatorEntryView>,
    groups: List<AuthenticatorGroupRecord>,
    collapsedIds: Set<String>,
    searching: Boolean,
): List<AuthenticatorSection> {
    val visible = entries.filter { !isGroupCatalog(it) }
    if (groups.isEmpty()) {
        return listOf(
            AuthenticatorSection(
                id = "",
                title = "",
                entries = visible,
                collapsed = false,
                ungrouped = true,
            ),
        )
    }
    val buckets = groups.associate { it.id to ArrayList<AuthenticatorEntryView>() }
    val loose = ArrayList<AuthenticatorEntryView>()
    for (entry in visible) {
        val id = groupIdOf(entry, groups)
        val bucket = id?.let { buckets[it] }
        if (bucket != null) bucket.add(entry) else loose.add(entry)
    }
    val sections = groups.map { group ->
        AuthenticatorSection(
            id = group.id,
            title = group.name,
            entries = buckets[group.id].orEmpty(),
            collapsed = !searching && group.id in collapsedIds,
            ungrouped = false,
        )
    } + AuthenticatorSection(
        id = "",
        title = UNGROUPED_TITLE,
        entries = loose,
        collapsed = !searching && "" in collapsedIds,
        ungrouped = true,
    )
    return if (searching) sections.filter { it.entries.isNotEmpty() } else sections
}

private fun parseGroupRecords(
    value: kotlinx.serialization.json.JsonElement,
): List<AuthenticatorGroupRecord> {
    val array = value as? JsonArray ?: return emptyList()
    val seen = HashSet<String>()
    val groups = ArrayList<AuthenticatorGroupRecord>()
    array.forEachIndexed { index, item ->
        val record = item as? JsonObject ?: return@forEachIndexed
        val id = record.string("id")?.trim().orEmpty()
        val name = record.string("name")?.let(::normalizeGroupName)
        val invalid = id.isEmpty() || id.length > GROUP_ID_MAX || name == null
        if (invalid || !seen.add(id)) return@forEachIndexed
        val extras = buildJsonObject {
            record.forEach { (key, extra) ->
                if (key !in GROUP_OWN_KEYS && key != "__proto__" && key != "constructor") {
                    put(key, extra)
                }
            }
        }
        groups.add(
            AuthenticatorGroupRecord(
                id = id,
                name = name,
                order = groupOrder(record["order"], index),
                extras = extras,
            ),
        )
    }
    return groups.sortedWith(groupComparator())
}

private fun serializeGroup(group: AuthenticatorGroupRecord): JsonObject = buildJsonObject {
    group.extras.forEach { (key, value) ->
        if (key !in GROUP_OWN_KEYS) put(key, value)
    }
    put("id", JsonPrimitive(group.id))
    put("name", JsonPrimitive(group.name))
    put("order", JsonPrimitive(formatTotpOrder(group.order)))
}

private fun groupOrder(value: kotlinx.serialization.json.JsonElement?, index: Int): Double {
    val raw = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    return parseTotpOrder(raw) ?: (index + 1) * ORDER_STEP
}

private fun groupComparator(): Comparator<AuthenticatorGroupRecord> =
    compareBy<AuthenticatorGroupRecord> { it.order }.thenBy { it.name }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
