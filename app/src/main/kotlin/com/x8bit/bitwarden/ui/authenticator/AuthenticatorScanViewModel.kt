package com.x8bit.bitwarden.ui.authenticator

import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.data.authenticator.importing.decodeMigrationUri
import com.x8bit.bitwarden.data.authenticator.otp.parseOtpAuthUri
import com.x8bit.bitwarden.data.authenticator.otp.toDraft
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Turns a scanned QR payload into an authenticator draft or an import.
 */
@HiltViewModel
class AuthenticatorScanViewModel @Inject constructor(
    private val repository: AuthenticatorRepository,
) : BaseViewModel<AuthenticatorScanState, AuthenticatorScanEvent, AuthenticatorScanAction>(
    initialState = AuthenticatorScanState(),
) {
    override fun handleAction(action: AuthenticatorScanAction) {
        when (action) {
            is AuthenticatorScanAction.Scanned -> handleScanned(action.value)
            AuthenticatorScanAction.Close -> sendEvent(AuthenticatorScanEvent.Close)
        }
    }

    private fun handleScanned(value: String) {
        if (state.handled) return
        mutableStateFlow.value = AuthenticatorScanState(handled = true)
        val trimmed = value.trim()
        if (trimmed.startsWith("otpauth-migration://")) {
            val drafts = decodeMigrationUri(trimmed)
            if (drafts.isEmpty()) {
                mutableStateFlow.value = AuthenticatorScanState(
                    handled = false,
                    message = "没有读出迁移数据",
                )
                return
            }
            viewModelScope.launch {
                drafts.forEach { repository.save(entryId = null, draft = it) }
                sendEvent(AuthenticatorScanEvent.Imported(drafts.size))
            }
            return
        }
        val parsed = parseOtpAuthUri(trimmed)
        if (parsed == null) {
            mutableStateFlow.value = AuthenticatorScanState(handled = false, message = "这不是验证码二维码")
            return
        }
        repository.stashDraft(parsed.toDraft())
        sendEvent(AuthenticatorScanEvent.OpenEdit)
    }
}

/**
 * Scan screen state.
 */
data class AuthenticatorScanState(
    val handled: Boolean = false,
    val message: String? = null,
)

/**
 * Scan events.
 */
sealed class AuthenticatorScanEvent {
    /** Leave the scanner. */
    data object Close : AuthenticatorScanEvent()

    /** Open the add form with the scanned draft. */
    data object OpenEdit : AuthenticatorScanEvent()

    /** Imported [count] entries from a migration QR. */
    data class Imported(val count: Int) : AuthenticatorScanEvent()
}

/**
 * Scan actions.
 */
sealed class AuthenticatorScanAction {
    /** A QR payload was read. */
    data class Scanned(val value: String) : AuthenticatorScanAction()

    /** Close. */
    data object Close : AuthenticatorScanAction()
}
