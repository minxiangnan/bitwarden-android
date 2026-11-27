package com.x8bit.bitwarden.ui.authenticator

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.bitwarden.core.data.repository.util.bufferedMutableSharedFlow
import com.x8bit.bitwarden.ui.platform.base.BitwardenComposeTest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test

class AuthenticatorEditNotesTest : BitwardenComposeTest() {
    private val state = MutableStateFlow(
        AuthenticatorEditState(
            entryId = "entry",
            name = "minxiangnan@vpn.com",
            notes = "公司 VPN",
        ),
    )
    private val viewModel = mockk<AuthenticatorEditViewModel>(relaxed = true) {
        every { stateFlow } returns state
        every { eventFlow } returns bufferedMutableSharedFlow<AuthenticatorEditEvent>()
        every { trySendAction(any()) } just runs
    }

    @Before
    fun setup() {
        setContent {
            AuthenticatorEditScreen(
                entryId = "entry",
                onBack = {},
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `notes field shows the cipher note and writes changes`() {
        composeTestRule.onNodeWithTag("AuthenticatorNotesField").performTextReplacement("机房门禁")
        verify {
            viewModel.trySendAction(AuthenticatorEditAction.NotesChanged("机房门禁"))
        }
    }
}
