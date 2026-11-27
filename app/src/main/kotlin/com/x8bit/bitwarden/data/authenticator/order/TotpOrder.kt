@file:Suppress("MagicNumber")

package com.x8bit.bitwarden.data.authenticator.order

/**
 * Hidden order shared with the browser extension.
 *
 * Authenticator entries store the decimal string in the encrypted notes trailer.
 * Login ciphers that carry the same TOTP secret also store it as a hidden custom
 * field of this name. Entries without a value sort after the numbered ones.
 */
const val TOTP_ORDER_FIELD: String = "zh.totp.order"

private const val MIN_GAP: Double = 1.0e-6
private const val STEP: Double = 1024.0

/**
 * One row the order math can see. [order] is the decimal string, or null when unset.
 */
data class TotpOrderSlot(
    val id: String,
    val order: String?,
    val name: String,
)

/**
 * Parses a decimal order. Blank, non-numeric, and non-finite values count as unset.
 */
fun parseTotpOrder(raw: String?): Double? {
    val value = raw?.trim()?.toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() }
}

/**
 * Writes a decimal without scientific notation or a trailing fraction of zero.
 */
fun formatTotpOrder(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/**
 * Numbered entries come first. Unset entries follow, ordered by [name].
 */
fun compareTotpOrder(
    leftOrder: String?,
    leftName: String,
    rightOrder: String?,
    rightName: String,
): Int {
    val left = parseTotpOrder(leftOrder)
    val right = parseTotpOrder(rightOrder)
    return when {
        left != null && right != null -> {
            val byNumber = left.compareTo(right)
            if (byNumber != 0) byNumber else leftName.compareTo(rightName, ignoreCase = true)
        }
        left != null -> -1
        right != null -> 1
        else -> leftName.compareTo(rightName, ignoreCase = true)
    }
}

/**
 * Order values to persist after moving [movedId] to [toIndex] in [slots].
 *
 * A normal move writes only the dragged row, using the midpoint of its new
 * neighbors. The whole list is numbered again when that gap is too small to
 * tell the rows apart, or when a neighbor has no number yet.
 */
fun ordersAfterMove(
    slots: List<TotpOrderSlot>,
    movedId: String,
    toIndex: Int,
): Map<String, String> {
    val from = slots.indexOfFirst { it.id == movedId }
    if (from < 0 || slots.size < 2) return emptyMap()
    val target = toIndex.coerceIn(0, slots.lastIndex)
    if (target == from) return emptyMap()
    val moved = slots[from]
    val arranged = slots.toMutableList()
    arranged.removeAt(from)
    arranged.add(target, moved)
    val index = arranged.indexOfFirst { it.id == movedId }
    val previous = parseTotpOrder(arranged.getOrNull(index - 1)?.order)
    val next = parseTotpOrder(arranged.getOrNull(index + 1)?.order)
    val midpoint = midpoint(previous, next)
    if (midpoint != null) return mapOf(movedId to formatTotpOrder(midpoint))
    return arranged.mapIndexed { position, slot ->
        slot.id to formatTotpOrder(position * STEP)
    }.toMap()
}

private fun midpoint(previous: Double?, next: Double?): Double? = when {
    previous != null && next != null -> {
        val gap = next - previous
        val middle = (previous + next) / 2.0
        middle.takeIf { gap > MIN_GAP && middle > previous && middle < next }
    }
    previous != null -> previous + STEP
    next != null -> next - STEP
    else -> null
}
