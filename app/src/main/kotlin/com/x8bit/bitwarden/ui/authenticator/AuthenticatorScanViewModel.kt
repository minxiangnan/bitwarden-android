package com.x8bit.bitwarden.ui.authenticator

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.data.authenticator.importing.decodeMigrationUri
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorDraft
import com.x8bit.bitwarden.data.authenticator.otp.parseOtpAuthUri
import com.x8bit.bitwarden.data.authenticator.otp.toDraft
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

private const val KEY_STATE = "scan_state"

/**
 * Turns a scanned QR payload into saved authenticator entries.
 */
@HiltViewModel
class AuthenticatorScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AuthenticatorRepository,
) : BaseViewModel<AuthenticatorScanState, AuthenticatorScanEvent, AuthenticatorScanAction>(
    initialState = savedStateHandle[KEY_STATE] ?: AuthenticatorScanState(),
) {
    init {
        stateFlow
            .onEach { savedStateHandle[KEY_STATE] = it }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: AuthenticatorScanAction) {
        when (action) {
            is AuthenticatorScanAction.PermissionResult -> {
                mutableStateFlow.value = state.copy(cameraGranted = action.granted)
            }
            is AuthenticatorScanAction.Scanned -> handleScanned(action.value)
            AuthenticatorScanAction.CameraFailed -> {
                mutableStateFlow.value = state.copy(message = CAMERA_FAILED)
            }
            AuthenticatorScanAction.Close -> sendEvent(AuthenticatorScanEvent.Close)
            AuthenticatorScanAction.ManualEntry -> sendEvent(AuthenticatorScanEvent.OpenManual)
            AuthenticatorScanAction.OpenSettings -> sendEvent(AuthenticatorScanEvent.OpenSettings)
            is AuthenticatorScanAction.Internal.Saved -> handleSaved(action.message)
            is AuthenticatorScanAction.Internal.Failed -> {
                mutableStateFlow.value = state.copy(handled = false, message = action.message)
            }
        }
    }

    private fun handleScanned(value: String) {
        if (state.handled) return
        val trimmed = value.trim()
        if (trimmed.startsWith("otpauth-migration://")) {
            saveDrafts(decodeMigrationUri(trimmed), emptyMessage = "没有读出迁移数据")
            return
        }
        val parsed = parseOtpAuthUri(trimmed)
        if (parsed == null) {
            mutableStateFlow.value = state.copy(message = "这不是验证码二维码")
            return
        }
        val label = parsed.issuer.ifBlank { parsed.name }
        saveDrafts(listOf(parsed.toDraft()), emptyMessage = "这不是验证码二维码", label = label)
    }

    private fun saveDrafts(
        drafts: List<AuthenticatorDraft>,
        emptyMessage: String,
        label: String? = null,
    ) {
        if (drafts.isEmpty()) {
            mutableStateFlow.value = state.copy(message = emptyMessage)
            return
        }
        mutableStateFlow.value = state.copy(handled = true, message = null)
        viewModelScope.launch {
            val failed = drafts.firstNotNullOfOrNull { draft ->
                repository.save(entryId = null, draft = draft).exceptionOrNull()
            }
            if (failed != null) {
                sendAction(
                    AuthenticatorScanAction.Internal.Failed(failed.message ?: "没有保存"),
                )
            } else {
                val message = label?.let { "已添加 $it" } ?: "已添加 ${drafts.size} 条验证码"
                sendAction(AuthenticatorScanAction.Internal.Saved(message))
            }
        }
    }

    private fun handleSaved(message: String) {
        repository.publishScanNotice(message)
        sendEvent(AuthenticatorScanEvent.Saved)
    }
}

/**
 * Scan screen state. [cameraGranted] stays null until the permission check returns.
 */
@Parcelize
data class AuthenticatorScanState(
    val cameraGranted: Boolean? = null,
    val handled: Boolean = false,
    val message: String? = null,
) : Parcelable

/**
 * Scan events.
 */
sealed class AuthenticatorScanEvent {
    /** Leave the scanner. */
    data object Close : AuthenticatorScanEvent()

    /** The entry was saved. */
    data object Saved : AuthenticatorScanEvent()

    /** Open the manual key form. */
    data object OpenManual : AuthenticatorScanEvent()

    /** Open the system app settings. */
    data object OpenSettings : AuthenticatorScanEvent()
}

/**
 * Scan actions.
 */
sealed class AuthenticatorScanAction {
    /** Latest camera permission. */
    data class PermissionResult(val granted: Boolean) : AuthenticatorScanAction()

    /** A QR payload was read. */
    data class Scanned(val value: String) : AuthenticatorScanAction()

    /** CameraX could not open a camera. */
    data object CameraFailed : AuthenticatorScanAction()

    /** Close. */
    data object Close : AuthenticatorScanAction()

    /** Manual key entry. */
    data object ManualEntry : AuthenticatorScanAction()

    /** Open system settings. */
    data object OpenSettings : AuthenticatorScanAction()

    /**
     * Results posted from a coroutine.
     */
    sealed class Internal : AuthenticatorScanAction() {
        /**
         * The drafts were stored.
         */
        data class Saved(val message: String) : Internal()

        /**
         * Saving failed and the scanner can try again.
         */
        data class Failed(val message: String) : Internal()
    }
}

private const val CAMERA_FAILED: String = "打不开摄像头"
