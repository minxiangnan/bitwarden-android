package com.x8bit.bitwarden.ui.authenticator

import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import com.x8bit.bitwarden.data.authenticator.repository.McpAccessState
import com.x8bit.bitwarden.data.platform.manager.clipboard.BitwardenClipboardManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings for the MCP access token.
 */
@HiltViewModel
class AuthenticatorMcpViewModel @Inject constructor(
    private val repository: AuthenticatorRepository,
    private val clipboardManager: BitwardenClipboardManager,
) : BaseViewModel<McpAccessState, AuthenticatorMcpEvent, AuthenticatorMcpAction>(
    initialState = McpAccessState(),
) {
    init {
        repository.mcp
            .onEach { sendAction(AuthenticatorMcpAction.Internal.State(it)) }
            .launchIn(viewModelScope)
        viewModelScope.launch { repository.refresh() }
    }

    override fun handleAction(action: AuthenticatorMcpAction) {
        when (action) {
            AuthenticatorMcpAction.CreateClick -> issue(rotate = false)
            AuthenticatorMcpAction.RotateClick -> {
                mutableStateFlow.update { it.copy(message = CONFIRM_ROTATE) }
            }
            AuthenticatorMcpAction.RevokeClick -> {
                mutableStateFlow.update { it.copy(message = CONFIRM_REVOKE) }
            }
            AuthenticatorMcpAction.Confirm -> confirm()
            AuthenticatorMcpAction.Dismiss -> {
                repository.dismissRevealedToken()
                mutableStateFlow.update { it.copy(message = null, revealedToken = null) }
            }
            AuthenticatorMcpAction.CopyToken -> {
                val token = state.revealedToken ?: return
                clipboardManager.setText(text = token, toastDescriptorOverride = "访问令牌")
            }
            is AuthenticatorMcpAction.Failed -> {
                mutableStateFlow.update { it.copy(message = action.text) }
            }
            is AuthenticatorMcpAction.Internal.State -> {
                mutableStateFlow.value = action.state.copy(message = state.message)
            }
        }
    }

    private fun confirm() {
        val which = state.message
        mutableStateFlow.update { it.copy(message = null) }
        when (which) {
            CONFIRM_ROTATE -> issue(rotate = true)
            CONFIRM_REVOKE -> viewModelScope.launch { repository.revokeMcpToken() }
            else -> Unit
        }
    }

    private fun issue(rotate: Boolean) {
        viewModelScope.launch {
            val result = if (rotate) repository.rotateMcpToken() else repository.createMcpToken()
            result.onFailure { error ->
                sendAction(AuthenticatorMcpAction.Failed(error.message ?: "没有完成"))
            }
        }
    }
}

internal const val CONFIRM_ROTATE: String =
    "轮换后，旧令牌立即失效。已经交给 MCP 的验证码会用新令牌重新封装。"
internal const val CONFIRM_REVOKE: String =
    "撤销后，所有 MCP 读取都会失败。需要的话可以再生成一个新令牌。"

/**
 * MCP screen events. None are required; the token stays on the screen.
 */
sealed class AuthenticatorMcpEvent

/**
 * MCP screen actions.
 */
sealed class AuthenticatorMcpAction {
    /** Create a token. */
    data object CreateClick : AuthenticatorMcpAction()
    /** Ask to rotate. */
    data object RotateClick : AuthenticatorMcpAction()
    /** Ask to revoke. */
    data object RevokeClick : AuthenticatorMcpAction()
    /** Confirm the pending destructive action. */
    data object Confirm : AuthenticatorMcpAction()
    /** Dismiss the dialog and hide the one-time token. */
    data object Dismiss : AuthenticatorMcpAction()
    /** Copy the one-time token. */
    data object CopyToken : AuthenticatorMcpAction()
    /** Show a failure. */
    data class Failed(val text: String) : AuthenticatorMcpAction()

    /**
     * Internal updates.
     */
    sealed class Internal : AuthenticatorMcpAction() {
        /**
         * Repository state arrived.
         */
        data class State(val state: McpAccessState) : Internal()
    }
}
