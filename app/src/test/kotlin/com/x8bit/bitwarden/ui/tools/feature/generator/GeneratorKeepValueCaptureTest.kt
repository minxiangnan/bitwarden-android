package com.x8bit.bitwarden.ui.tools.feature.generator

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.bitwarden.core.data.repository.util.bufferedMutableSharedFlow
import com.bitwarden.ui.platform.feature.settings.appearance.model.AppTheme
import com.bitwarden.ui.platform.manager.IntentManager
import com.bitwarden.ui.platform.resource.BitwardenString
import com.x8bit.bitwarden.data.platform.manager.util.AppResumeStateManager
import com.x8bit.bitwarden.ui.platform.base.BitwardenComposeTest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@Config(qualifiers = "zh-rCN-w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneratorKeepValueCaptureTest : BitwardenComposeTest() {
    @Test
    fun `captures the generator in light`() {
        capture(AppTheme.LIGHT, "light")
    }

    @Test
    fun `captures the generator in dark`() {
        capture(AppTheme.DARK, "dark")
    }

    private fun capture(theme: AppTheme, name: String) {
        val state = MutableStateFlow(
            GeneratorState(
                generatedText = "kept-password",
                selectedType = GeneratorState.MainType.Password(),
                currentEmailAddress = "currentEmail",
                shouldShowCoachMarkTour = false,
                copiedMessage = BitwardenString.zh_generator_password_copied,
                copiedNonce = 1,
            ),
        )
        val viewModel = mockk<GeneratorViewModel>(relaxed = true) {
            every { eventFlow } returns bufferedMutableSharedFlow()
            every { stateFlow } returns state
        }
        val intentManager = mockk<IntentManager> { every { launchUri(any()) } just runs }
        val resume = mockk<AppResumeStateManager>(relaxed = true)
        setContent(theme = theme, intentManager = intentManager, appResumeStateManager = resume) {
            GeneratorScreen(
                viewModel = viewModel,
                onNavigateToPasswordHistory = {},
                navigateToVaultAddItem = {},
                onNavigateBack = {},
                onDimNavBarRequest = {},
            )
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.waitForIdle()
        val image = composeTestRule.onRoot().captureToImage()
        val bitmap = image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val dir = File("/opt/cursor/artifacts/zh-design")
        dir.mkdirs()
        FileOutputStream(File(dir, "generator-$name.png")).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
