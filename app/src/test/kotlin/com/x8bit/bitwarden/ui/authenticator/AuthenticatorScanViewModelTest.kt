package com.x8bit.bitwarden.ui.authenticator

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.bitwarden.ui.platform.base.BaseViewModelTest
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorDraft
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorKind
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class AuthenticatorScanViewModelTest : BaseViewModelTest() {
    private val repository: AuthenticatorRepository = mockk()

    @Test
    fun `otpauth scan saves the draft and leaves the scanner`() = runTest {
        val draft = slot<AuthenticatorDraft>()
        coEvery { repository.save(entryId = null, draft = capture(draft)) } returns
            Result.success(Unit)
        every { repository.publishScanNotice(any()) } just runs
        val viewModel = createViewModel()

        viewModel.eventFlow.test {
            viewModel.trySendAction(AuthenticatorScanAction.Scanned(OTP_AUTH))
            assertEquals(AuthenticatorScanEvent.Saved, awaitItem())
        }

        assertEquals("ada", draft.captured.name)
        assertEquals("GitHub", draft.captured.issuer)
        assertEquals("GEZDGNBVGY3TQOJQ", draft.captured.secret)
        assertEquals(AuthenticatorKind.TOTP, draft.captured.kind)
        verify { repository.publishScanNotice("已添加 GitHub") }
    }

    @Test
    fun `a second scan is ignored once a code is being saved`() = runTest {
        coEvery { repository.save(entryId = null, draft = any()) } returns Result.success(Unit)
        every { repository.publishScanNotice(any()) } just runs
        val viewModel = createViewModel()

        viewModel.eventFlow.test {
            viewModel.trySendAction(AuthenticatorScanAction.Scanned(OTP_AUTH))
            viewModel.trySendAction(AuthenticatorScanAction.Scanned(OTP_AUTH))
            assertEquals(AuthenticatorScanEvent.Saved, awaitItem())
        }
        coVerify(exactly = 1) { repository.save(entryId = null, draft = any()) }
    }

    @Test
    fun `plain text is not saved`() = runTest {
        val viewModel = createViewModel()
        viewModel.trySendAction(AuthenticatorScanAction.Scanned("https://example.com"))
        assertEquals("这不是验证码二维码", viewModel.stateFlow.value.message)
        assertFalse(viewModel.stateFlow.value.handled)
        coVerify(exactly = 0) { repository.save(any(), any()) }
    }

    @Test
    fun `migration qr saves every decoded entry`() = runTest {
        coEvery { repository.save(entryId = null, draft = any()) } returns Result.success(Unit)
        every { repository.publishScanNotice(any()) } just runs
        val viewModel = createViewModel()
        val secret = "Hello!".toByteArray()
        val otp = protoBytes(1, secret) +
            protoBytes(2, "ada".toByteArray()) +
            protoBytes(3, "GitHub".toByteArray()) +
            protoVarint(6, 2)
        val payload = protoBytes(1, otp)
        val data = Base64.getUrlEncoder().withoutPadding().encodeToString(payload)

        viewModel.eventFlow.test {
            viewModel.trySendAction(
                AuthenticatorScanAction.Scanned("otpauth-migration://offline?data=$data"),
            )
            assertEquals(AuthenticatorScanEvent.Saved, awaitItem())
        }
        coVerify(exactly = 1) {
            repository.save(
                entryId = null,
                draft = match { it.name == "ada" && it.issuer == "GitHub" },
            )
        }
        verify { repository.publishScanNotice("已添加 1 条验证码") }
    }

    @Test
    fun `save failure keeps the scanner open`() = runTest {
        coEvery { repository.save(entryId = null, draft = any()) } returns
            Result.failure(IllegalStateException("当前离线，没有保存。"))
        val viewModel = createViewModel()

        viewModel.eventFlow.test {
            viewModel.trySendAction(AuthenticatorScanAction.Scanned(OTP_AUTH))
            expectNoEvents()
        }
        assertEquals("当前离线，没有保存。", viewModel.stateFlow.value.message)
        assertFalse(viewModel.stateFlow.value.handled)
    }

    @Test
    fun `permission and camera failure stay on the scanner`() = runTest {
        val viewModel = createViewModel()
        assertNull(viewModel.stateFlow.value.cameraGranted)
        viewModel.trySendAction(AuthenticatorScanAction.PermissionResult(granted = true))
        assertEquals(true, viewModel.stateFlow.value.cameraGranted)
        viewModel.trySendAction(AuthenticatorScanAction.CameraFailed)
        assertEquals("打不开摄像头", viewModel.stateFlow.value.message)

        viewModel.eventFlow.test {
            viewModel.trySendAction(AuthenticatorScanAction.PermissionResult(granted = false))
            viewModel.trySendAction(AuthenticatorScanAction.ManualEntry)
            viewModel.trySendAction(AuthenticatorScanAction.OpenSettings)
            assertEquals(AuthenticatorScanEvent.OpenManual, awaitItem())
            assertEquals(AuthenticatorScanEvent.OpenSettings, awaitItem())
        }
        assertEquals(false, viewModel.stateFlow.value.cameraGranted)
    }

    private fun createViewModel(): AuthenticatorScanViewModel = AuthenticatorScanViewModel(
        savedStateHandle = SavedStateHandle(),
        repository = repository,
    )
}

private const val OTP_AUTH =
    "otpauth://totp/GitHub:ada?secret=GEZDGNBVGY3TQOJQ&issuer=GitHub"

private fun protoBytes(field: Int, value: ByteArray): ByteArray {
    val tag = (field shl 3) or 2
    return byteArrayOf(tag.toByte()) + byteArrayOf(value.size.toByte()) + value
}

private fun protoVarint(field: Int, value: Int): ByteArray =
    byteArrayOf((field shl 3).toByte(), value.toByte())
