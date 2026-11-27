package com.x8bit.bitwarden.ui.authenticator

import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModelTest
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorSnapshot
import com.x8bit.bitwarden.data.platform.manager.clipboard.BitwardenClipboardManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuthenticatorListViewModelTest : BaseViewModelTest() {
    private val repository: AuthenticatorRepository = mockk {
        every { entries } returns MutableStateFlow(AuthenticatorSnapshot())
        every { scanNotice } returns MutableStateFlow(null)
    }
    private val clipboardManager: BitwardenClipboardManager = mockk {
        every {
            setText(
                text = any<String>(),
                isSensitive = any(),
                toastDescriptorOverride = any<String>(),
                showToast = any(),
            )
        } just runs
    }
    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-01-01T00:00:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun `repeated copies keep one inline confirmation and do not toast`() = runTest {
        coEvery { repository.consumeCode("a", any()) } returns "123456"
        coEvery { repository.consumeCode("b", any()) } returns "654321"
        val viewModel = viewModel()
        try {
            viewModel.trySendAction(AuthenticatorListAction.CopyClick("a"))
            val firstNonce = viewModel.stateFlow.value.copiedNonce
            assertEquals("a", viewModel.stateFlow.value.copiedId)

            viewModel.trySendAction(AuthenticatorListAction.CopyClick("b"))
            assertEquals("b", viewModel.stateFlow.value.copiedId)
            assertTrue(viewModel.stateFlow.value.copiedNonce > firstNonce)

            viewModel.trySendAction(AuthenticatorListAction.Internal.ClearCopied(firstNonce))
            assertEquals("b", viewModel.stateFlow.value.copiedId)

            viewModel.trySendAction(
                AuthenticatorListAction.Internal.ClearCopied(
                    viewModel.stateFlow.value.copiedNonce,
                ),
            )
            assertNull(viewModel.stateFlow.value.copiedId)
            verify(exactly = 1) {
                clipboardManager.setText(
                    text = "123456",
                    isSensitive = true,
                    toastDescriptorOverride = "验证码",
                    showToast = false,
                )
            }
            verify(exactly = 1) {
                clipboardManager.setText(
                    text = "654321",
                    isSensitive = true,
                    toastDescriptorOverride = "验证码",
                    showToast = false,
                )
            }
        } finally {
            // The list ticks forever. Cancel it so runTest can drain the scheduler.
            viewModel.viewModelScope.cancel()
        }
    }

    private fun viewModel() = AuthenticatorListViewModel(
        trash = false,
        repository = repository,
        clipboardManager = clipboardManager,
        clock = clock,
    )
}
