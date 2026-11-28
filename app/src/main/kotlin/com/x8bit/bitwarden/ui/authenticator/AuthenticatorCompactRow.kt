package com.x8bit.bitwarden.ui.authenticator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import com.x8bit.bitwarden.data.authenticator.match.matchRanges

internal const val AUTHENTICATOR_URGENT_SECONDS = 5
private const val COUNTDOWN_MILLIS = 1_000
private const val COUNTDOWN_BAR_DP = 84
private const val BUBBLE_ENTER_MILLIS = 220
private const val BUBBLE_EXIT_MILLIS = 180
private const val BUBBLE_CORNER_PERCENT = 50
private const val BUBBLE_SHADOW_ALPHA = 0.45f
private const val OVERFLOW_DOT_RADIUS_DP = 1.6f
private const val OVERFLOW_DOT_TOP = 0.22f
private const val OVERFLOW_DOT_MIDDLE = 0.5f
private const val OVERFLOW_DOT_BOTTOM = 0.78f

/**
 * Name and optional notes. Both wrap in full. Blank notes take no space.
 * A stored favorite flag is not shown.
 */
@Composable
internal fun AuthenticatorIdentity(
    row: AuthenticatorRow,
    query: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HighlightedLine(
                text = row.title,
                query = query,
                style = BitwardenTheme.typography.titleSmall,
                color = BitwardenTheme.colorScheme.text.primary,
                singleLine = false,
                modifier = Modifier.weight(1f),
            )
            if (row.kindLabel.isNotBlank()) {
                Text(
                    text = row.kindLabel,
                    style = BitwardenTheme.typography.labelSmall,
                    color = BitwardenTheme.colorScheme.text.secondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            row.alias?.takeIf { it.isNotBlank() }?.let { alias ->
                Text(
                    text = alias,
                    style = BitwardenTheme.typography.labelSmall,
                    color = BitwardenTheme.colorScheme.text.interaction,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (row.notes.isNotBlank()) {
            HighlightedLine(
                text = row.notes,
                query = query,
                style = BitwardenTheme.typography.bodySmall,
                color = BitwardenTheme.colorScheme.text.secondary,
                singleLine = false,
            )
        }
    }
}

/**
 * Code, a thin countdown under it, and the next code only while the window is almost over.
 */
@Composable
internal fun AuthenticatorCode(
    row: AuthenticatorRow,
    trash: Boolean,
    urgent: Boolean,
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = row.code.ifBlank { "—" },
            style = BitwardenTheme.typography.titleLarge.copy(
                fontFeatureSettings = "tnum",
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
            fontFamily = FontFamily.Monospace,
            color = if (urgent) {
                BitwardenTheme.colorScheme.status.error
            } else {
                BitwardenTheme.colorScheme.icon.secondary
            },
            maxLines = 1,
        )
        if (!trash && row.seconds > 0) {
            CountdownBar(
                seconds = row.seconds,
                period = row.period,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (urgent && row.nextCode != null) {
            Text(
                text = stringResource(BitwardenString.zh_authenticator_next) + " " + row.nextCode,
                style = BitwardenTheme.typography.labelSmall,
                color = BitwardenTheme.colorScheme.text.secondary,
                maxLines = 1,
                modifier = Modifier.testTag("AuthenticatorNextCode"),
            )
        }
    }
}

/**
 * Pill shown under the status bar after a code is copied. Enter and exit follow the
 * system animator scale, so reduced motion snaps instead of sliding.
 */
@Composable
internal fun AuthenticatorCopyBubble(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(bubbleTween(BUBBLE_ENTER_MILLIS)) +
            slideInVertically(bubbleTween(BUBBLE_ENTER_MILLIS)) { fullHeight -> -fullHeight },
        exit = fadeOut(bubbleTween(BUBBLE_EXIT_MILLIS)) +
            slideOutVertically(bubbleTween(BUBBLE_EXIT_MILLIS)) { fullHeight -> -fullHeight },
        modifier = modifier,
    ) {
        val shadow = BitwardenTheme.colorScheme.icon.secondary
        Text(
            text = stringResource(BitwardenString.zh_authenticator_copied),
            style = BitwardenTheme.typography.labelLarge,
            color = BitwardenTheme.colorScheme.filledButton.foreground,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(percent = BUBBLE_CORNER_PERCENT),
                    ambientColor = shadow.copy(alpha = BUBBLE_SHADOW_ALPHA),
                    spotColor = shadow,
                )
                .clip(RoundedCornerShape(percent = BUBBLE_CORNER_PERCENT))
                .background(BitwardenTheme.colorScheme.filledButton.background)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("AuthenticatorCopyBubble"),
        )
    }
}

private fun <T> bubbleTween(durationMillis: Int) = tween<T>(
    durationMillis = durationMillis,
    easing = FastOutSlowInEasing,
)

@Composable
private fun HighlightedLine(
    text: String,
    query: String,
    style: TextStyle,
    color: Color,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val highlight = SpanStyle(
        color = BitwardenTheme.colorScheme.text.interaction,
        background = BitwardenTheme.colorScheme.background.alert,
    )
    val annotated = buildAnnotatedString {
        append(text)
        matchRanges(text, query).forEach { range ->
            addStyle(highlight, range.first, range.last + 1)
        }
    }
    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier,
    )
}

@Composable
internal fun AuthenticatorOverflowButton(onClick: () -> Unit) {
    val description = stringResource(BitwardenString.zh_authenticator_more)
    val color = BitwardenTheme.colorScheme.icon.secondary
    Canvas(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .size(18.dp)
            .semantics { contentDescription = description }
            .testTag("AuthenticatorOverflow"),
    ) {
        val radius = OVERFLOW_DOT_RADIUS_DP.dp.toPx()
        val centerX = size.width / 2f
        listOf(OVERFLOW_DOT_TOP, OVERFLOW_DOT_MIDDLE, OVERFLOW_DOT_BOTTOM).forEach { fraction ->
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(centerX, size.height * fraction),
            )
        }
    }
}

/**
 * Thin remaining-time bar under the code. The track is the divider line and the fill is
 * terracotta, or the urgent color in the last few seconds. A new time window snaps; otherwise
 * the fill eases across the second, and reduced motion shortens that to nothing.
 */
@Composable
private fun CountdownBar(
    seconds: Int,
    period: Int,
    modifier: Modifier = Modifier,
) {
    val safePeriod = period.coerceAtLeast(1)
    val target = (seconds.coerceAtLeast(0).toFloat() / safePeriod).coerceIn(0f, 1f)
    var previous by remember { mutableFloatStateOf(target) }
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = if (target > previous) {
            snap()
        } else {
            tween(durationMillis = COUNTDOWN_MILLIS, easing = LinearEasing)
        },
        label = "authenticatorCountdown",
    )
    SideEffect { previous = target }
    val urgent = seconds <= AUTHENTICATOR_URGENT_SECONDS
    val fill = if (urgent) {
        BitwardenTheme.colorScheme.status.error
    } else {
        BitwardenTheme.colorScheme.icon.secondary
    }
    val label = stringResource(BitwardenString.zh_authenticator_remaining, seconds)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics { contentDescription = label }
            .testTag("AuthenticatorCountdown"),
    ) {
        Box(
            modifier = Modifier
                .width(COUNTDOWN_BAR_DP.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(percent = BUBBLE_CORNER_PERCENT))
                .background(BitwardenTheme.colorScheme.stroke.divider)
                .semantics { invisibleToUser() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(fill),
            )
        }
        Text(
            text = seconds.toString(),
            style = BitwardenTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = if (urgent) fill else BitwardenTheme.colorScheme.text.secondary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 6.dp)
                .width(18.dp)
                .semantics { invisibleToUser() },
        )
    }
}
