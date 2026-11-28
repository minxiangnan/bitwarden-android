@file:Suppress("TooManyFunctions")

package com.x8bit.bitwarden.ui.platform.theme

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.bitwarden.ui.platform.components.button.BitwardenFilledButton
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.content.BitwardenEmptyContent
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.components.field.BitwardenTextField
import com.bitwarden.ui.platform.components.icon.model.IconData
import com.bitwarden.ui.platform.components.model.CardStyle
import com.bitwarden.ui.platform.components.navigation.BitwardenBottomAppBar
import com.bitwarden.ui.platform.components.row.BitwardenPushRow
import com.bitwarden.ui.platform.components.snackbar.BitwardenSnackbar
import com.bitwarden.ui.platform.components.snackbar.model.BitwardenSnackbarData
import com.bitwarden.ui.platform.feature.settings.appearance.model.AppTheme
import com.bitwarden.ui.platform.resource.BitwardenDrawable
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import com.bitwarden.ui.util.asText
import com.x8bit.bitwarden.ui.authenticator.AuthenticatorCard
import com.x8bit.bitwarden.ui.authenticator.AuthenticatorCopyBubble
import com.x8bit.bitwarden.ui.authenticator.AuthenticatorRow
import com.x8bit.bitwarden.ui.platform.base.BitwardenComposeTest
import com.x8bit.bitwarden.ui.platform.components.listitem.BitwardenListItem
import com.x8bit.bitwarden.ui.platform.feature.vaultunlockednavbar.model.VaultUnlockedNavBarTab
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Phone-width renders of the shared composables each screen is built from.
 * Mock data only. No vault, no account.
 */
@Config(qualifiers = "w480dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZhDesignGalleryTest : BitwardenComposeTest() {
    @Test
    fun `renders every screen in light`() {
        render(theme = AppTheme.LIGHT, themeName = "light")
    }

    @Test
    fun `renders every screen in dark`() {
        render(theme = AppTheme.DARK, themeName = "dark")
    }

    @Test
    fun `renders the update dialog in light`() {
        renderDialog(theme = AppTheme.LIGHT, themeName = "light")
    }

    @Test
    fun `renders the update dialog in dark`() {
        renderDialog(theme = AppTheme.DARK, themeName = "dark")
    }

    private fun render(theme: AppTheme, themeName: String) {
        File(OUTPUT).mkdirs()
        setContent(theme = theme) {
            Column(modifier = Modifier.width(360.dp)) {
                SCREENS.forEach { screen ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BitwardenTheme.colorScheme.background.primary)
                            .testTag(screen),
                    ) {
                        Gallery(screen)
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.waitForIdle()
        SCREENS.forEach { screen -> save(themeName, screen) }
    }

    private fun renderDialog(theme: AppTheme, themeName: String) {
        File(OUTPUT).mkdirs()
        setContent(theme = theme) {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(520.dp)
                    .background(BitwardenTheme.colorScheme.background.primary),
            ) {
                ScreenTitle(stringResource(BitwardenString.about))
                BitwardenTwoButtonDialog(
                    title = "发现新版本",
                    message = "1.0.12 已准备好。示例说明，没有真实更新包。",
                    confirmButtonText = stringResource(BitwardenString.zh_update_now),
                    dismissButtonText = stringResource(BitwardenString.cancel),
                    onConfirmClick = {},
                    onDismissClick = {},
                    onDismissRequest = {},
                )
            }
        }
        val image = composeTestRule.onNode(isDialog()).captureToImage()
        val bitmap = image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        FileOutputStream(File(OUTPUT, "dialog-$themeName.png")).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private fun save(themeName: String, screen: String) {
        val image = composeTestRule.onNodeWithTag(screen).captureToImage()
        val bitmap = image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        FileOutputStream(File(OUTPUT, "$screen-$themeName.png")).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}

private const val OUTPUT = "/opt/cursor/artifacts/zh-design"

private val SCREENS = listOf(
    "authenticator",
    "login",
    "unlock",
    "vault",
    "vault-item",
    "generator",
    "authenticator-add",
    "settings",
    "about",
    "empty",
    "snackbar",
    "search",
    "send",
    "scan",
    "navigation",
)

@Suppress("LongMethod")
@androidx.compose.runtime.Composable
private fun Gallery(screen: String) {
    when (screen) {
        "authenticator" -> AuthenticatorGallery()
        "login" -> FormGallery(
            title = "登录",
            primary = stringResource(BitwardenString.log_in_verb),
            fields = listOf(
                stringResource(BitwardenString.email_address) to "ada@example.com",
                stringResource(BitwardenString.master_password) to "••••••••",
            ),
        )
        "unlock" -> FormGallery(
            title = stringResource(BitwardenString.unlock),
            primary = stringResource(BitwardenString.unlock),
            fields = listOf(stringResource(BitwardenString.master_password) to ""),
        )
        "vault" -> VaultGallery()
        "vault-item" -> FormGallery(
            title = "示例网站",
            primary = stringResource(BitwardenString.edit),
            fields = listOf(
                "用户名" to "ada@example.com",
                stringResource(BitwardenString.password) to "••••••••",
            ),
        )
        "generator" -> GeneratorGallery()
        "authenticator-add" -> FormGallery(
            title = stringResource(BitwardenString.zh_authenticator_add),
            primary = stringResource(BitwardenString.save),
            fields = listOf("名称" to "示例", "密钥" to "JBSWY3DPEHPK3PXP"),
        )
        "settings" -> SettingsGallery()
        "about" -> AboutGallery()
        "empty" -> BitwardenEmptyContent(
            title = stringResource(BitwardenString.zh_authenticator_empty_title),
            text = stringResource(BitwardenString.zh_authenticator_empty),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
        )
        "snackbar" -> Column(modifier = Modifier.fillMaxWidth()) {
            ScreenTitle("提示")
            BitwardenSnackbar(
                bitwardenSnackbarData = BitwardenSnackbarData(
                    message = "验证码已复制".asText(),
                    actionLabel = stringResource(BitwardenString.okay).asText(),
                ),
            )
        }
        "search" -> Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            ScreenTitle(stringResource(BitwardenString.search_vault))
            BitwardenTextField(
                value = "vpn",
                onValueChange = {},
                label = stringResource(BitwardenString.search_vault),
                cardStyle = CardStyle.Full,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            )
            VaultRows()
        }
        "send" -> SendGallery()
        "scan" -> ScanGallery()
        "navigation" -> Column(modifier = Modifier.fillMaxWidth()) {
            ScreenTitle(stringResource(BitwardenString.zh_authenticator_title))
            AuthenticatorGroup()
            BitwardenBottomAppBar(
                navigationItems = persistentListOf(
                    VaultUnlockedNavBarTab.Vault(labelRes = BitwardenString.my_vault),
                    VaultUnlockedNavBarTab.Authenticator,
                    VaultUnlockedNavBarTab.Generator,
                    VaultUnlockedNavBarTab.Send,
                    VaultUnlockedNavBarTab.Settings(),
                ),
                selectedItem = VaultUnlockedNavBarTab.Authenticator,
                onClick = {},
            )
        }
        else -> Unit
    }
}

@androidx.compose.runtime.Composable
private fun AuthenticatorGallery() {
    Box(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(BitwardenString.zh_authenticator_title),
                style = BitwardenTheme.typography.titleLarge,
                color = BitwardenTheme.colorScheme.text.primary,
                modifier = Modifier.weight(1f),
            )
        }
        BitwardenTextField(
            value = "",
            onValueChange = {},
            label = stringResource(BitwardenString.zh_authenticator_search),
            cardStyle = CardStyle.Full,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        )
        AuthenticatorGroup()
    }
        AuthenticatorCopyBubble(
            visible = true,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@androidx.compose.runtime.Composable
private fun AuthenticatorGroup() {
    val rows = listOf(
        galleryRow(
            "示例邮箱 · 这是一个很长的账户名称，需要完整换行",
            "这是完整备注，不会被省略，可以排到第二行。",
            "123 456",
            22,
        ),
        galleryRow("示例 VPN", "公司", "654 321", 4),
        galleryRow("示例银行", "", "111 222", 18),
    )
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BitwardenTheme.colorScheme.background.secondary),
    ) {
        rows.forEachIndexed { index, row ->
            AuthenticatorCard(
                row = row,
                query = "",
                trash = false,
                showDivider = index != rows.lastIndex,
                onCopy = {},
                onEdit = {},
                onTrash = {},
                onRestore = {},
                onDelete = {},
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun FormGallery(
    title: String,
    primary: String,
    fields: List<Pair<String, String>>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        ScreenTitle(title)
        fields.forEach { (label, value) ->
            BitwardenTextField(
                value = value,
                onValueChange = {},
                label = label,
                cardStyle = CardStyle.Full,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(),
            )
        }
        BitwardenFilledButton(
            label = primary,
            onClick = {},
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
        )
    }
}

@androidx.compose.runtime.Composable
private fun VaultGallery() {
    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenTitle(stringResource(BitwardenString.my_vault))
        VaultRows()
    }
}

@androidx.compose.runtime.Composable
private fun VaultRows() {
    listOf(
        Triple("示例网站", "ada@example.com", CardStyle.Top()),
        Triple("示例笔记", "没有真实内容", CardStyle.Middle()),
        Triple("示例银行卡", "•••• 4242", CardStyle.Bottom),
    ).forEach { (label, support, style) ->
        BitwardenListItem(
            label = label,
            supportingLabel = support,
            startIcon = IconData.Local(iconRes = BitwardenDrawable.ic_globe),
            onClick = {},
            selectionDataList = persistentListOf(),
            cardStyle = style,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@androidx.compose.runtime.Composable
private fun GeneratorGallery() {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ScreenTitle(stringResource(BitwardenString.generator))
        Text(
            text = "k7mw-q4np-h2vx",
            style = BitwardenTheme.typography.titleLarge,
            color = BitwardenTheme.colorScheme.text.interaction,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BitwardenTheme.colorScheme.background.secondary)
                .padding(16.dp),
        )
        BitwardenFilledButton(
            label = stringResource(BitwardenString.copy),
            onClick = {},
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        )
    }
}

@androidx.compose.runtime.Composable
private fun SettingsGallery() {
    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenTitle(stringResource(BitwardenString.settings))
        listOf(
            BitwardenString.appearance to CardStyle.Top(),
            BitwardenString.security to CardStyle.Middle(),
            BitwardenString.about to CardStyle.Bottom,
        ).forEach { (label, style) ->
            BitwardenPushRow(
                text = stringResource(label),
                onClick = {},
                cardStyle = style,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun AboutGallery() {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ScreenTitle(stringResource(BitwardenString.about))
        Text(
            text = stringResource(BitwardenString.version) + " 1.0.11",
            style = BitwardenTheme.typography.bodyMedium,
            color = BitwardenTheme.colorScheme.text.secondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@androidx.compose.runtime.Composable
private fun SendGallery() {
    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenTitle(stringResource(BitwardenString.send))
        BitwardenListItem(
            label = "示例发送",
            supportingLabel = "文本 · 7 天后过期",
            startIcon = IconData.Local(iconRes = BitwardenDrawable.ic_send),
            onClick = {},
            selectionDataList = persistentListOf(),
            cardStyle = CardStyle.Full,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@androidx.compose.runtime.Composable
private fun ScanGallery() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .size(width = 328.dp, height = 220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BitwardenTheme.colorScheme.background.scrim),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(BitwardenString.point_your_camera_at_the_qr_code),
                style = BitwardenTheme.typography.bodyMedium,
                color = BitwardenTheme.colorScheme.text.primary,
                modifier = Modifier.padding(16.dp),
            )
        }
        BitwardenTextButton(
            label = stringResource(BitwardenString.zh_authenticator_add),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@androidx.compose.runtime.Composable
private fun ScreenTitle(title: String) {
    Text(
        text = title,
        style = BitwardenTheme.typography.titleLarge,
        color = BitwardenTheme.colorScheme.text.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

private fun galleryRow(title: String, notes: String, code: String, seconds: Int) = AuthenticatorRow(
    id = title,
    title = title,
    notes = notes,
    code = code,
    nextCode = "999 000",
    seconds = seconds,
    period = 30,
    favorite = false,
    alias = null,
    kindLabel = "",
)
