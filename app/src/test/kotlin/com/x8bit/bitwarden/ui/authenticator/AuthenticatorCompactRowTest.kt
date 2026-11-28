package com.x8bit.bitwarden.ui.authenticator

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import com.x8bit.bitwarden.ui.platform.base.BitwardenComposeTest
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
                        onFavorite = {},
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
                        onFavorite = {},
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
                    onFavorite = {},
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithText("minxiangnan@vpn.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("833 143").assertIsDisplayed()
        composeTestRule.onNodeWithText("下一段 316 431").assertDoesNotExist()
        composeTestRule.onNodeWithText("公司 VPN").assertDoesNotExist()
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
                onFavorite = {},
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

    private fun savePng(tag: String, name: String) {
        val image = composeTestRule.onNodeWithTag(tag).captureToImage()
        val bitmap = image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val directory = File("/opt/cursor/artifacts").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    @Test
    fun `clean rows hide reorder controls until sort mode`() {
        var cleanCopies = 0
        var sortCopies = 0
        setContent {
            val listState = rememberLazyListState()
            val tint = BitwardenTheme.colorScheme.icon.secondary
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .background(BitwardenTheme.colorScheme.background.primary),
            ) {
                Column(modifier = Modifier.testTag("CleanList")) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "验证器",
                            style = BitwardenTheme.typography.titleLarge,
                            color = BitwardenTheme.colorScheme.text.primary,
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                        )
                        BitwardenTextButton(label = "排序", onClick = {})
                    }
                    AuthenticatorCard(
                        row = sampleRow(seconds = 20, notes = "公司 VPN"),
                        query = "",
                        trash = false,
                        canMoveUp = false,
                        canMoveDown = true,
                        onCopy = { cleanCopies += 1 },
                        onEdit = {},
                        onTrash = {},
                        onRestore = {},
                        onFavorite = {},
                        onDelete = {},
                    )
                    AuthenticatorCard(
                        row = sampleRow(seconds = 18, notes = "", code = "441 902"),
                        query = "",
                        trash = false,
                        onCopy = {},
                        onEdit = {},
                        onTrash = {},
                        onRestore = {},
                        onFavorite = {},
                        onDelete = {},
                    )
                }
                Column(modifier = Modifier.testTag("SortList")) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "验证器",
                            style = BitwardenTheme.typography.titleLarge,
                            color = BitwardenTheme.colorScheme.text.primary,
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                        )
                        BitwardenTextButton(label = "完成", onClick = {})
                    }
                    AuthenticatorCard(
                        row = sampleRow(seconds = 12, notes = "邮箱", code = "120 884"),
                        query = "",
                        trash = false,
                        copied = false,
                        reorder = AuthenticatorReorder(
                            index = 1,
                            lastIndex = 1,
                            listState = listState,
                            onDragStart = {},
                            onShift = {},
                            onDrop = {},
                        ),
                        canMoveUp = true,
                        canMoveDown = false,
                        allowCopy = false,
                        onCopy = { sortCopies += 1 },
                        onEdit = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        onMoveTop = {},
                        onTrash = {},
                        onRestore = {},
                        onFavorite = {},
                        onDelete = {},
                        modifier = Modifier
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = tint.copy(alpha = 0.45f),
                                spotColor = tint,
                            )
                            .graphicsLayer {
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                            .background(BitwardenTheme.colorScheme.background.primary)
                            .background(
                                color = tint.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                            ),
                    )
                }
            }
        }
        composeTestRule
            .onNode(
                hasTestTag("AuthenticatorDragHandle") and hasAnyAncestor(hasTestTag("CleanList")),
                useUnmergedTree = true,
            )
            .assertDoesNotExist()
        composeTestRule
            .onAllNodesWithTag("AuthenticatorDragHandle", useUnmergedTree = true)
            .assertCountEquals(1)
        composeTestRule.onNodeWithText("833 143").performClick()
        org.junit.Assert.assertEquals(1, cleanCopies)
        composeTestRule.onNodeWithText("120 884").performClick()
        org.junit.Assert.assertEquals(0, sortCopies)
        composeTestRule.onNodeWithText("排序").assertIsDisplayed()
        composeTestRule.onNodeWithText("完成").assertIsDisplayed()
        savePng("CleanList", "authenticator-list-clean.png")
        savePng("SortList", "authenticator-list-sorting.png")
    }

    @Test
    fun `copied row shows one short inline confirmation`() {
        setContent {
            AuthenticatorCode(
                row = sampleRow(seconds = 20, notes = ""),
                trash = false,
                urgent = true,
                copied = true,
            )
        }
        composeTestRule
            .onNodeWithText("已复制 ✓")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("AuthenticatorNextCode")
            .assertDoesNotExist()
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

private fun sampleRow(seconds: Int, notes: String, code: String = "833 143") = AuthenticatorRow(
    id = "row-$seconds-$notes",
    title = "minxiangnan@vpn.com",
    notes = notes,
    code = code,
    nextCode = "316 431",
    seconds = seconds,
    period = 30,
    favorite = true,
    alias = null,
    kindLabel = "",
)
