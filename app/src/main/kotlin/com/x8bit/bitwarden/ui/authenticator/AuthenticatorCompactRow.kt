package com.x8bit.bitwarden.ui.authenticator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import com.x8bit.bitwarden.data.authenticator.match.matchRanges
import kotlin.math.cos
import kotlin.math.sin

internal const val AUTHENTICATOR_URGENT_SECONDS = 5
private const val FULL_CIRCLE_DEGREES = 360f
private const val STAR_POINTS = 10
private const val STAR_START_DEGREES = -90.0
private const val STAR_STEP_DEGREES = 36.0
private const val STAR_INNER_RATIO = 0.42f
private const val OVERFLOW_DOT_RADIUS_DP = 1.6f
private const val OVERFLOW_DOT_TOP = 0.22f
private const val OVERFLOW_DOT_MIDDLE = 0.5f
private const val OVERFLOW_DOT_BOTTOM = 0.78f

/**
 * Name, optional one-line notes, and a small favorite mark. Blank notes take no space.
 */
@Composable
internal fun AuthenticatorIdentity(
    row: AuthenticatorRow,
    query: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.favorite) {
                FavoriteMark()
            }
            HighlightedLine(
                text = row.title,
                query = query,
                style = BitwardenTheme.typography.titleSmall,
                color = BitwardenTheme.colorScheme.text.primary,
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
                style = BitwardenTheme.typography.labelSmall,
                color = BitwardenTheme.colorScheme.text.secondary,
            )
        }
    }
}

/**
 * Code, a small countdown, and the next code only while the current window is almost over.
 */
@Composable
internal fun AuthenticatorCode(
    row: AuthenticatorRow,
    trash: Boolean,
    urgent: Boolean,
    copied: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.code.ifBlank { "—" },
                style = BitwardenTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                color = if (urgent) {
                    BitwardenTheme.colorScheme.status.error
                } else {
                    BitwardenTheme.colorScheme.text.primary
                },
                maxLines = 1,
            )
            if (!trash && row.seconds > 0) {
                CountdownRing(
                    seconds = row.seconds,
                    period = row.period,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (copied) {
            Text(
                text = stringResource(BitwardenString.zh_authenticator_copied),
                style = BitwardenTheme.typography.labelSmall,
                color = BitwardenTheme.colorScheme.text.interaction,
                maxLines = 1,
                modifier = Modifier.testTag("AuthenticatorCopied"),
            )
        } else if (urgent && row.nextCode != null) {
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

@Composable
private fun HighlightedLine(
    text: String,
    query: String,
    style: TextStyle,
    color: Color,
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
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun FavoriteMark() {
    val description = stringResource(BitwardenString.favorite)
    val color = BitwardenTheme.colorScheme.text.interaction
    Canvas(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(12.dp)
            .semantics { contentDescription = description },
    ) {
        drawStar(color)
    }
}

@Composable
internal fun AuthenticatorMoveTopButton(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(BitwardenString.zh_authenticator_move_top)
    val color = if (enabled) {
        BitwardenTheme.colorScheme.icon.secondary
    } else {
        BitwardenTheme.colorScheme.text.secondary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .testTag("AuthenticatorMoveTop")
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = "↑",
            color = color,
            style = BitwardenTheme.typography.labelLarge,
        )
    }
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

private fun DrawScope.drawStar(color: Color) {
    val path = Path()
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val outer = size.minDimension / 2f
    val inner = outer * STAR_INNER_RATIO
    for (index in 0 until STAR_POINTS) {
        val radius = if (index % 2 == 0) outer else inner
        val angle = Math.toRadians(STAR_START_DEGREES + index * STAR_STEP_DEGREES)
        val x = centerX + (radius * cos(angle)).toFloat()
        val y = centerY + (radius * sin(angle)).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

@Composable
private fun CountdownRing(
    seconds: Int,
    period: Int,
    modifier: Modifier = Modifier,
) {
    val urgent = seconds <= AUTHENTICATOR_URGENT_SECONDS
    val color = if (urgent) {
        BitwardenTheme.colorScheme.status.error
    } else {
        BitwardenTheme.colorScheme.icon.secondary
    }
    val track = color.copy(alpha = 0.22f)
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(26.dp)) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = FULL_CIRCLE_DEGREES,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            val sweep = FULL_CIRCLE_DEGREES * seconds / period.coerceAtLeast(1)
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        Text(
            text = seconds.toString(),
            style = BitwardenTheme.typography.labelSmall,
            color = color,
        )
    }
}
