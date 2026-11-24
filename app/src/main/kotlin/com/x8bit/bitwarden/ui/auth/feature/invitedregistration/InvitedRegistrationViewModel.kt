package com.x8bit.bitwarden.ui.auth.feature.invitedregistration

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.bitwarden.ui.platform.resource.BitwardenString
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.LoginResult
import com.x8bit.bitwarden.data.auth.repository.model.RegisterResult
import com.x8bit.bitwarden.data.auth.repository.model.SendVerificationEmailResult
import com.x8bit.bitwarden.data.platform.repository.EnvironmentRepository
import com.x8bit.bitwarden.ui.auth.feature.auth.AuthGraph
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

private const val KEY_STATE = "state"

/**
 * Creates an account for an email an administrator has already invited.
 */
@HiltViewModel(assistedFactory = InvitedRegistrationViewModel.Factory::class)
class InvitedRegistrationViewModel @AssistedInject constructor(
    @Assisted route: AuthGraph.InvitedRegistrationRoute,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val environmentRepository: EnvironmentRepository,
) : BaseViewModel<
    InvitedRegistrationState,
    InvitedRegistrationEvent,
    InvitedRegistrationAction,
    >(
    initialState = savedStateHandle[KEY_STATE] ?: InvitedRegistrationState(
        email = route.emailAddress,
        name = route.emailAddress.substringBefore("@"),
        password = "",
        confirmPassword = "",
        dialogMessage = null,
        isLoading = false,
    ),
) {
    /**
     * Factory for an [InvitedRegistrationViewModel] bound to one invited email.
     */
    @AssistedFactory
    interface Factory {
        /**
         * Creates the view model for [route].
         */
        fun create(route: AuthGraph.InvitedRegistrationRoute): InvitedRegistrationViewModel
    }

    init {
        stateFlow
            .onEach { savedStateHandle[KEY_STATE] = it }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: InvitedRegistrationAction) {
        when (action) {
            is InvitedRegistrationAction.NameChanged -> {
                mutableStateFlow.update { it.copy(name = action.value) }
            }

            is InvitedRegistrationAction.PasswordChanged -> {
                mutableStateFlow.update { it.copy(password = action.value) }
            }

            is InvitedRegistrationAction.ConfirmPasswordChanged -> {
                mutableStateFlow.update { it.copy(confirmPassword = action.value) }
            }

            InvitedRegistrationAction.DismissDialog -> {
                mutableStateFlow.update { it.copy(dialogMessage = null) }
            }

            InvitedRegistrationAction.Submit -> submit()
            is InvitedRegistrationAction.Internal.VerificationReceived -> {
                handleVerification(action.result)
            }

            is InvitedRegistrationAction.Internal.RegisterReceived -> {
                handleRegister(action.result)
            }

            is InvitedRegistrationAction.Internal.LoginReceived -> handleLogin(action.result)
        }
    }

    private fun submit() {
        val problem = checkInvitePassword(state.password, state.confirmPassword)
        if (problem != null) {
            val message = when (problem) {
                InvitePasswordProblem.TOO_SHORT -> BitwardenString.zh_password_too_short
                InvitePasswordProblem.MISMATCH -> BitwardenString.zh_password_mismatch
            }
            mutableStateFlow.update { it.copy(dialogMessage = message) }
            return
        }
        mutableStateFlow.update { it.copy(isLoading = true, dialogMessage = null) }
        viewModelScope.launch {
            environmentRepository.saveCurrentEnvironmentForEmail(state.email)
            val result = authRepository.sendVerificationEmail(
                email = state.email,
                name = state.name.ifBlank { state.email.substringBefore("@") },
                receiveMarketingEmails = false,
            )
            sendAction(InvitedRegistrationAction.Internal.VerificationReceived(result))
        }
    }

    private fun handleVerification(result: SendVerificationEmailResult) {
        when (result) {
            is SendVerificationEmailResult.Error -> showFailure(result.errorMessage)
            is SendVerificationEmailResult.Success -> {
                val token = result.emailVerificationToken
                if (token.isNullOrBlank()) {
                    showFailure(null)
                    return
                }
                viewModelScope.launch {
                    val registerResult = authRepository.register(
                        email = state.email,
                        masterPassword = state.password,
                        masterPasswordHint = null,
                        emailVerificationToken = token,
                        shouldCheckDataBreaches = false,
                        isMasterPasswordStrong = true,
                    )
                    sendAction(InvitedRegistrationAction.Internal.RegisterReceived(registerResult))
                }
            }
        }
    }

    private fun handleRegister(result: RegisterResult) {
        when (result) {
            RegisterResult.Success -> {
                viewModelScope.launch {
                    val loginResult = authRepository.login(
                        email = state.email,
                        password = state.password,
                    )
                    sendAction(InvitedRegistrationAction.Internal.LoginReceived(loginResult))
                }
            }

            is RegisterResult.Error -> showFailure(result.errorMessage)
            RegisterResult.WeakPassword -> {
                mutableStateFlow.update {
                    it.copy(
                        isLoading = false,
                        dialogMessage = BitwardenString.zh_password_too_short,
                    )
                }
            }

            RegisterResult.DataBreachFound,
            RegisterResult.DataBreachAndWeakPassword,
            -> showFailure(null)
        }
    }

    private fun handleLogin(result: LoginResult) {
        mutableStateFlow.update { it.copy(isLoading = false) }
        when (result) {
            LoginResult.Success -> Unit
            else -> sendEvent(InvitedRegistrationEvent.NavigateToLogin(state.email))
        }
    }

    private fun showFailure(serverMessage: String?) {
        val message = if (isInviteRejected(serverMessage)) {
            BitwardenString.zh_invite_rejected
        } else {
            BitwardenString.generic_error_message
        }
        mutableStateFlow.update { it.copy(isLoading = false, dialogMessage = message) }
    }
}

/**
 * State for creating an invited account.
 */
@Parcelize
data class InvitedRegistrationState(
    val email: String,
    val name: String,
    val password: String,
    val confirmPassword: String,
    val dialogMessage: Int?,
    val isLoading: Boolean,
) : Parcelable

/**
 * One-shot navigation from the invited registration screen.
 */
sealed class InvitedRegistrationEvent {
    /**
     * The account exists but automatic login did not finish. Open the password screen.
     */
    data class NavigateToLogin(val email: String) : InvitedRegistrationEvent()
}

/**
 * User and internal actions for invited registration.
 */
sealed class InvitedRegistrationAction {
    /**
     * The display name changed.
     */
    data class NameChanged(val value: String) : InvitedRegistrationAction()

    /**
     * The master password changed.
     */
    data class PasswordChanged(val value: String) : InvitedRegistrationAction()

    /**
     * The confirmation password changed.
     */
    data class ConfirmPasswordChanged(val value: String) : InvitedRegistrationAction()

    /**
     * Create the account.
     */
    data object Submit : InvitedRegistrationAction()

    /**
     * Dismiss the error dialog.
     */
    data object DismissDialog : InvitedRegistrationAction()

    /**
     * Results posted from coroutines.
     */
    sealed class Internal : InvitedRegistrationAction() {
        /**
         * The verification-email call finished.
         */
        data class VerificationReceived(
            val result: SendVerificationEmailResult,
        ) : Internal()

        /**
         * Account creation finished.
         */
        data class RegisterReceived(val result: RegisterResult) : Internal()

        /**
         * The follow-up login finished.
         */
        data class LoginReceived(val result: LoginResult) : Internal()
    }
}
