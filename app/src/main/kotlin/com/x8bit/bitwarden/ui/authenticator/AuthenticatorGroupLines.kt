package com.x8bit.bitwarden.ui.authenticator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme

/**
 * Group title above a block of codes. Tap collapses the block on this device.
 */
@Composable
internal fun AuthenticatorGroupHeader(
    title: String,
    count: Int,
    collapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("AuthenticatorGroupHeader"),
    ) {
        Text(
            text = if (collapsed) "▸" else "▾",
            color = BitwardenTheme.colorScheme.icon.secondary,
            style = BitwardenTheme.typography.labelLarge,
        )
        Text(
            text = title,
            color = BitwardenTheme.colorScheme.text.primary,
            style = BitwardenTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            text = count.toString(),
            color = BitwardenTheme.colorScheme.text.secondary,
            style = BitwardenTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * Picks 未分组 or a named group for one code.
 */
@Composable
internal fun AssignGroupDialog(
    name: String,
    groups: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onAssign: (String?) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BitwardenTheme.colorScheme.background.secondary)
                .padding(8.dp),
        ) {
            Text(
                text = name,
                style = BitwardenTheme.typography.titleSmall,
                color = BitwardenTheme.colorScheme.text.primary,
                modifier = Modifier.padding(12.dp),
            )
            GroupChoice(stringResource(BitwardenString.zh_authenticator_ungrouped)) {
                onAssign(null)
                onDismiss()
            }
            groups.forEach { (id, label) ->
                GroupChoice(label) {
                    onAssign(id)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun GroupChoice(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = BitwardenTheme.colorScheme.text.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
    )
}

/**
 * Moves a code toward [targetIndex]. Landing on a header puts the code under it.
 */
internal fun shiftAuthenticatorLine(
    lines: List<AuthenticatorLine>,
    movedId: String,
    targetIndex: Int,
): List<AuthenticatorLine> {
    val from = lines.indexOfFirst { it is AuthenticatorLine.Code && it.row.id == movedId }
    if (from < 0 || targetIndex !in lines.indices || targetIndex == from) return lines
    val moving = lines[from]
    val rest = lines.toMutableList()
    rest.removeAt(from)
    var insert = if (targetIndex > from) targetIndex - 1 else targetIndex
    if (rest.getOrNull(insert) is AuthenticatorLine.Header) insert += 1
    rest.add(insert.coerceAtMost(rest.size), moving)
    return rest
}

/**
 * Where a dragged code should be saved.
 */
internal sealed class GroupDrop {
    /**
     * Same group, new index among that group's codes.
     */
    data class Reorder(val toIndex: Int) : GroupDrop()

    /**
     * Another group. Null [groupId] is 未分组.
     */
    data class Move(val groupId: String?, val toIndex: Int) : GroupDrop()
}

/**
 * Reads the section above [movedId] after a drag.
 */
internal fun groupDrop(
    lines: List<AuthenticatorLine>,
    movedId: String,
    originGroupId: String?,
): GroupDrop? {
    val walked = lines.fold(GroupWalk()) { walk, line ->
        if (walk.drop != null) {
            walk
        } else {
            walk.step(line, movedId, originGroupId)
        }
    }
    return walked.drop
}

private data class GroupWalk(
    val seenHeader: Boolean = false,
    val section: String? = null,
    val index: Int = 0,
    val drop: GroupDrop? = null,
) {
    fun step(line: AuthenticatorLine, movedId: String, originGroupId: String?): GroupWalk {
        if (line is AuthenticatorLine.Header) {
            return copy(seenHeader = true, section = line.sectionId.ifEmpty { null }, index = 0)
        }
        val code = line as AuthenticatorLine.Code
        if (code.row.id != movedId) return copy(index = index + 1)
        val group = if (seenHeader) section else originGroupId
        val placed = if (group == originGroupId) {
            GroupDrop.Reorder(index)
        } else {
            GroupDrop.Move(group, index)
        }
        return copy(drop = placed)
    }
}
