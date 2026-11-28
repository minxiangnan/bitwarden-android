package com.bitwarden.ui.platform.theme.shape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * The default [BitwardenShapes] for the app.
 */
val bitwardenShapes: BitwardenShapes = BitwardenShapes(
    actionCard = RoundedCornerShape(size = 16.dp),
    badge = RoundedCornerShape(size = 12.dp),
    bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    button = CircleShape,
    coachmark = RoundedCornerShape(size = 8.dp),
    content = RoundedCornerShape(size = 16.dp),
    contentBottom = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
    contentMiddle = RectangleShape,
    contentTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    dialog = RoundedCornerShape(size = 28.dp),
    fab = CircleShape,
    fabItem = RoundedCornerShape(size = 16.dp),
    infoCallout = RoundedCornerShape(size = 12.dp),
    menu = RoundedCornerShape(size = 12.dp),
    progressIndicator = CircleShape,
    row = RectangleShape,
    segmentedControl = RoundedCornerShape(size = 12.dp),
    snackbar = RoundedCornerShape(size = 16.dp),
    favicon = CircleShape,
)
