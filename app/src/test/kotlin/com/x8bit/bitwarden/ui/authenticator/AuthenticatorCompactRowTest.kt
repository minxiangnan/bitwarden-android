package com.x8bit.bitwarden.ui.authenticator

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import com.x8bit.bitwarden.ui.platform.base.BitwardenComposeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@Config(qualifiers = "w480dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthenticatorCompactRowTest : BitwardenComposeTest() {
    @Test
    fun `compact row is much shorter than the previous card`() {
        setContent {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .background(BitwardenTheme.colorScheme.background.primary)
                    .testTag("RowComparison"),
            ) {
                Text(
                    text = "1.0.6",
                    color = BitwardenTheme.colorScheme.text.secondary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                Box(modifier = Modifier.testTag("OldRow")) {
                    PreviousAuthenticatorCard()
                }
                Text(
                    text = "1.0.7",
                    color = BitwardenTheme.colorScheme.text.secondary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                Box(modifier = Modifier.testTag("NewRow")) {
                    AuthenticatorCard(
                        row = sampleRow(seconds = 20, notes = "公司 VPN"),
                        query = "VPN",
                        trash = false,
                        onCopy = {},
                        onEdit = {},
                        onTrash = {},
                        onRestore = {},
                        onDelete = {},
                    )
                }
                Box(modifier = Modifier.testTag("UrgentRow")) {
                    AuthenticatorCard(
                        row = sampleRow(seconds = 4, notes = "公司 VPN"),
                        query = "",
                        trash = false,
                        onCopy = {},
                        onEdit = {},
                        onTrash = {},
                        onRestore = {},
                        onDelete = {},
                    )
                }
            }
        }
        val oldHeight = heightOf("OldRow")
        val newHeight = heightOf("NewRow")
        val urgentHeight = heightOf("UrgentRow")
        assertTrue(
            "compact $newHeight px should be under 65% of previous $oldHeight px",
            newHeight < oldHeight * 0.65f,
        )
        assertTrue(
            "urgent $urgentHeight px should stay under previous $oldHeight px",
            urgentHeight < oldHeight,
        )
        File("/opt/cursor/artifacts").mkdirs()
        File("/opt/cursor/artifacts/authenticator-row-heights.txt").writeText(
            "old=$oldHeight\nnew=$newHeight\nurgent=$urgentHeight\n",
        )
        composeTestRule
            .onNodeWithTag("AuthenticatorNextCode", useUnmergedTree = true)
            .assertExists()
        savePng("RowComparison", "authenticator-row-comparison.png")
        savePng("OldRow", "authenticator-row-previous.png")
        savePng("NewRow", "authenticator-row-compact.png")
        savePng("UrgentRow", "authenticator-row-urgent.png")
    }

    @Test
    fun `blank notes take no line and the next code waits until five seconds`() {
        setContent {
            Column(modifier = Modifier.width(360.dp)) {
                AuthenticatorCard(
                    row = sampleRow(seconds = 20, notes = ""),
                    query = "",
                    trash = false,
                    onCopy = {},
                    onEdit = {},
                    onTrash = {},
                    onRestore = {},
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithText("minxiangnan@vpn.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("833 143").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("AuthenticatorCountdown", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("剩余 20 秒", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("下一段 316 431").assertDoesNotExist()
        composeTestRule.onNodeWithText("公司 VPN").assertDoesNotExist()
        val code = boundsOf("AuthenticatorCodeText")
        val bar = boundsOf("AuthenticatorCountdown")
        val seconds = boundsOf("AuthenticatorSeconds")
        assertClose(code.left, bar.left)
        assertClose(code.right, bar.right)
        assertClose(seconds.right, bar.right)
    }

    @Test
    fun `code columns share one width and the next code lines up`() {
        setContent {
            Column(modifier = Modifier.width(360.dp)) {
                AuthenticatorCard(
                    row = sampleRow(seconds = 22, notes = "甲", code = "123 456", title = "甲"),
                    query = "",
                    trash = false,
                    onCopy = {},
                    onEdit = {},
                    onTrash = {},
                    onRestore = {},
                    onDelete = {},
                )
                AuthenticatorCard(
                    row = sampleRow(seconds = 4, notes = "乙", code = "654 321", title = "乙"),
                    query = "",
                    trash = false,
                    onCopy = {},
                    onEdit = {},
                    onTrash = {},
                    onRestore = {},
                    onDelete = {},
                )
            }
        }
        val codes = composeTestRule
            .onAllNodesWithTag("AuthenticatorCodeText", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        val bars = composeTestRule
            .onAllNodesWithTag("AuthenticatorCountdown", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertEquals(2, codes.size)
        assertClose(codes[0].left, codes[1].left)
        assertClose(codes[0].right, codes[1].right)
        assertClose(codes[0].width, bars[0].width)
        assertClose(codes[1].left, bars[1].left)
        val next = boundsOf("AuthenticatorNextCode")
        val seconds = composeTestRule
            .onAllNodesWithTag("AuthenticatorSeconds", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertClose(seconds[1].right, codes[1].right)
        assertClose(next.bottom, seconds[1].bottom)
        assertTrue(
            "next code should share the seconds line, tops ${next.top} and ${seconds[1].top}",
            kotlin.math.abs(next.top - seconds[1].top) <= 4f,
        )
        assertTrue(next.right < seconds[1].left)
    }

    @Test
    fun `tapping the row copies and the overflow opens edit`() {
        var copied = false
        var edited = false
        setContent {
            AuthenticatorCard(
                row = sampleRow(seconds = 20, notes = "公司 VPN"),
                query = "公司",
                trash = false,
                onCopy = { copied = true },
                onEdit = { edited = true },
                onTrash = {},
                onRestore = {},
                onDelete = {},
            )
        }
        composeTestRule.onNodeWithText("833 143").performClick()
        assertTrue(copied)
        composeTestRule.onNodeWithTag("AuthenticatorOverflow").performClick()
        composeTestRule.onNodeWithText("Edit").performClick()
        assertTrue(edited)
    }

    private fun heightOf(tag: String): Float =
        composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height

    private fun boundsOf(tag: String) =
        composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

    private fun assertClose(left: Float, right: Float) {
        assertTrue("$left vs $right", kotlin.math.abs(left - right) <= 1f)
    }

    private fun savePng(tag: String, name: String) {
        val image = composeTestRule.onNodeWithTag(tag).captureToImage()
        val bitmap = image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val directory = File("/opt/cursor/artifacts").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    @Test
    fun `rows show the full name and notes and the overflow keeps move`() {
        var copies = 0
        val title = "很长的示例账户名称需要完整显示出来"
        val notes = "这是一段完整备注，不会被截断，应该换到下一行。"
        setContent {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .background(BitwardenTheme.colorScheme.background.primary)
                    .testTag("WrappedList"),
            ) {
                AuthenticatorCard(
                    row = sampleRow(seconds = 20, notes = notes, title = title),
                    query = "",
                    trash = false,
                    canMoveUp = true,
                    canMoveDown = true,
                    onCopy = { copies += 1 },
                    onEdit = {},
                    onTrash = {},
                    onRestore = {},
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(notes).assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuthenticatorDragHandle").assertDoesNotExist()
        composeTestRule.onNodeWithText("833 143").performClick()
        org.junit.Assert.assertEquals(1, copies)
        composeTestRule.onNodeWithContentDescription("Favorite").assertDoesNotExist()
        composeTestRule.onNodeWithTag("AuthenticatorOverflow").performClick()
        composeTestRule.onNodeWithText("上移").assertIsDisplayed()
        composeTestRule.onNodeWithText("置顶").assertIsDisplayed()
        composeTestRule.onNodeWithText("Favorite").assertDoesNotExist()
        composeTestRule.onNodeWithText("Unfavorite").assertDoesNotExist()
        savePng("WrappedList", "authenticator-list-wrapped.png")
    }

    @Test
    fun `copy bubble sits at the top and is announced`() {
        setContent {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .background(BitwardenTheme.colorScheme.background.primary)
                    .testTag("CopyBubbleHost"),
            ) {
                AuthenticatorCopyBubble(
                    visible = true,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithText("已复制验证码").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AuthenticatorCopyBubble").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            ),
        )
        savePng("CopyBubbleHost", "authenticator-copy-bubble.png")
    }
}

/**
 * Height stand-in for the 1.0.6 card: large code, always-visible next code, and an edit button.
 */
@androidx.compose.runtime.Composable
private fun PreviousAuthenticatorCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "minxiangnan@vpn.com",
                    style = BitwardenTheme.typography.titleMedium,
                    color = BitwardenTheme.colorScheme.text.primary,
                )
                Text(
                    text = "VPN",
                    style = BitwardenTheme.typography.bodySmall,
                    color = BitwardenTheme.colorScheme.text.secondary,
                )
            }
            Text(text = "20", color = BitwardenTheme.colorScheme.icon.secondary)
        }
        Text(
            text = "833 143",
            style = BitwardenTheme.typography.displaySmall,
            fontFamily = FontFamily.Monospace,
            color = BitwardenTheme.colorScheme.text.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "下一个 316 431",
            style = BitwardenTheme.typography.bodySmall,
            color = BitwardenTheme.colorScheme.text.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(text = "★", color = BitwardenTheme.colorScheme.text.interaction)
        BitwardenTextButton(
            label = androidx.compose.ui.res.stringResource(BitwardenString.edit),
            onClick = {},
        )
    }
}

private fun sampleRow(
    seconds: Int,
    notes: String,
    code: String = "833 143",
    title: String = "minxiangnan@vpn.com",
) = AuthenticatorRow(
    id = "row-$seconds-$notes",
    title = title,
    notes = notes,
    code = code,
    nextCode = "316 431",
    seconds = seconds,
    period = 30,
    favorite = true,
    alias = null,
    kindLabel = "",
)
