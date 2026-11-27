package com.x8bit.bitwarden.ui.authenticator

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bitwarden.ui.platform.components.camera.CameraPreview
import com.bitwarden.ui.platform.feature.qrcodescan.util.QrCodeAnalyzerImpl
import com.x8bit.bitwarden.data.authenticator.otp.parseOtpAuthUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Opens the real camera preview on a device or emulator and saves a screenshot.
 */
@RunWith(AndroidJUnit4::class)
class AuthenticatorCameraPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun grantCamera() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.CAMERA,
        )
    }

    @Test
    fun cameraPreviewBindsAndScreenshotIsSaved() {
        val error = AtomicReference<String?>(null)
        composeRule.setContent {
            CameraPreview(
                analyzer = QrCodeAnalyzerImpl(),
                cameraErrorReceive = { failure ->
                    error.set(failure.message ?: failure.javaClass.simpleName)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(PREVIEW_WAIT_MS)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(SCREENSHOT).outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertNull(error.get())
        assertTrue(bitmap.width > 0)
    }

    @Test
    fun otpauthUriRoundTripsThroughTheParser() {
        val parsed = parseOtpAuthUri(OTP_AUTH)
        assertEquals("ada", parsed?.name)
        assertEquals("GitHub", parsed?.issuer)
        assertEquals("GEZDGNBVGY3TQOJQ", parsed?.secret)
    }
}

private const val PREVIEW_WAIT_MS = 4_000L
private const val SCREENSHOT = "/sdcard/Download/zh-scan-preview.png"
private const val OTP_AUTH =
    "otpauth://totp/GitHub:ada?secret=GEZDGNBVGY3TQOJQ&issuer=GitHub"
