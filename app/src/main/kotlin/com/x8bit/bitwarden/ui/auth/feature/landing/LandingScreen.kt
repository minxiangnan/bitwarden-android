package com.x8bit.bitwarden.ui.auth.feature.landing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitwarden.ui.platform.base.util.EventsEffect
import com.bitwarden.ui.platform.base.util.standardHorizontalMargin
import com.bitwarden.ui.platform.components.account.BitwardenAccountSwitcher
import com.bitwarden.ui.platform.components.account.BitwardenPlaceholderAccountActionItem
import com.bitwarden.ui.platform.components.appbar.BitwardenTopAppBar
import com.bitwarden.ui.platform.components.button.BitwardenFilledButton
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.dialog.BitwardenBasicDialog
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.components.field.BitwardenTextField
import com.bitwarden.ui.platform.components.model.CardStyle
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.components.snackbar.BitwardenSnackbarHost
import com.bitwarden.ui.platform.components.snackbar.model.rememberBitwardenSnackbarHostState
import com.bitwarden.ui.platform.components.util.rememberVectorPainter
import com.bitwarden.ui.platform.resource.BitwardenDrawable
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme

/**
 * The top level composable for the Landing screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun LandingScreen(
    onNavigateToLogin: (emailAddress: String) -> Unit,
    onNavigateToEnvironment: () -> Unit,
    onNavigateToStartRegistration: () -> Unit,
    onNavigateToPreAuthSettings: () -> Unit,
    onNavigateToInvitedRegistration: (emailAddress: String) -> Unit,
    viewModel: LandingViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val snackbarHostState = rememberBitwardenSnackbarHostState()
    EventsEffect(viewModel = viewModel) { event ->
        when (event) {
            is LandingEvent.NavigateToLogin -> onNavigateToLogin(event.emailAddress)
            LandingEvent.NavigateToEnvironment -> onNavigateToEnvironment()
            LandingEvent.NavigateToStartRegistration -> onNavigateToStartRegistration()
            is LandingEvent.NavigateToInvitedRegistration -> {
                onNavigateToInvitedRegistration(event.emailAddress)
            }
            LandingEvent.NavigateToSettings -> onNavigateToPreAuthSettings()
            is LandingEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.data)
        }
    }

    when (val dialog = state.dialog) {
        is LandingState.DialogState.AccountAlreadyAdded -> {
            BitwardenTwoButtonDialog(
                title = stringResource(id = BitwardenString.account_already_added),
                message = stringResource(
                    id = BitwardenString.switch_to_already_added_account_confirmation,
                ),
                confirmButtonText = stringResource(id = BitwardenString.yes),
                dismissButtonText = stringResource(id = BitwardenString.cancel),
                onConfirmClick = {
                    viewModel.trySendAction(
                        LandingAction.ConfirmSwitchToMatchingAccountClick(dialog.accountSummary),
                    )
                },
                onDismissClick = { viewModel.trySendAction(LandingAction.DialogDismiss) },
                onDismissRequest = { viewModel.trySendAction(LandingAction.DialogDismiss) },
            )
        }

        is LandingState.DialogState.Error -> {
            BitwardenBasicDialog(
                title = stringResource(id = BitwardenString.an_error_has_occurred),
                message = dialog.message(),
                onDismissRequest = { viewModel.trySendAction(LandingAction.DialogDismiss) },
            )
        }

        null -> Unit
    }

    var isAccountMenuVisible by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { !isAccountMenuVisible },
    )

    BitwardenScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (state.isAppBarVisible) {
                BitwardenTopAppBar(
                    title = "",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = null,
                    actions = {
                        BitwardenPlaceholderAccountActionItem(
                            onClick = { isAccountMenuVisible = !isAccountMenuVisible },
                        )
                    },
                )
            }
        },
        overlay = {
            BitwardenAccountSwitcher(
                isVisible = isAccountMenuVisible,
                accountSummaries = state.accountSummaries,
                onSwitchAccountClick = {
                    viewModel.trySendAction(LandingAction.SwitchAccountClick(it))
                },
                onLockAccountClick = {
                    viewModel.trySendAction(LandingAction.LockAccountClick(it))
                },
                onLogoutAccountClick = {
                    viewModel.trySendAction(LandingAction.LogoutAccountClick(it))
                },
                onAddAccountClick = {
                    // Not available
                },
                onDismissRequest = { isAccountMenuVisible = false },
                isAddAccountAvailable = false,
                topAppBarScrollBehavior = scrollBehavior,
                modifier = Modifier.fillMaxSize(),
            )
        },
        snackbarHost = {
            BitwardenSnackbarHost(bitwardenHostState = snackbarHostState)
        },
    ) {
        LandingScreenContent(
            state = state,
            onServerUrlInputChange = {
                viewModel.trySendAction(LandingAction.ServerUrlInputChanged(it))
            },
            onEmailInputChange = { viewModel.trySendAction(LandingAction.EmailInputChanged(it)) },
            onContinueClick = { viewModel.trySendAction(LandingAction.ContinueButtonClick) },
            onInvitedRegistrationClick = {
                viewModel.trySendAction(LandingAction.InvitedRegistrationClick)
            },
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun LandingScreenContent(
    state: LandingState,
    onServerUrlInputChange: (String) -> Unit,
    onEmailInputChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    onInvitedRegistrationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emailFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .imePadding(),
    ) {
        Spacer(modifier = Modifier.height(height = 12.dp))
        Spacer(modifier = Modifier.weight(1f))

        Image(
            painter = rememberVectorPainter(id = BitwardenDrawable.logo_bitwarden),
            contentDescription = null,
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(height = 12.dp))

        Text(
            text = stringResource(id = BitwardenString.login_to_bitwarden),
            textAlign = TextAlign.Center,
            style = BitwardenTheme.typography.headlineSmall,
            color = BitwardenTheme.colorScheme.text.primary,
            modifier = Modifier
                .standardHorizontalMargin()
                .wrapContentHeight(),
        )

        Spacer(modifier = Modifier.height(height = 24.dp))

        BitwardenTextField(
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
            value = state.serverUrlInput,
            onValueChange = onServerUrlInputChange,
            label = stringResource(id = BitwardenString.zh_server_address),
            placeholder = stringResource(id = BitwardenString.zh_server_address_hint),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
            textFieldTestTag = "ServerUrlEntry",
            cardStyle = CardStyle.Full,
        )

        Spacer(modifier = Modifier.height(height = 8.dp))

        BitwardenTextField(
            modifier = Modifier
                .focusRequester(emailFocus)
                .standardHorizontalMargin()
                .fillMaxWidth(),
            value = state.emailInput,
            onValueChange = onEmailInputChange,
            label = stringResource(id = BitwardenString.email_address),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onContinueClick()
                },
            ),
            textFieldTestTag = "EmailAddressEntry",
            cardStyle = CardStyle.Full,
        )

        Spacer(modifier = Modifier.height(height = 24.dp))

        BitwardenFilledButton(
            label = stringResource(id = BitwardenString.continue_text),
            onClick = onContinueClick,
            isEnabled = state.isContinueButtonEnabled,
            modifier = Modifier
                .testTag("ContinueButton")
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(height = 8.dp))
        BitwardenTextButton(
            label = stringResource(id = BitwardenString.zh_invited_create_account),
            onClick = onInvitedRegistrationClick,
            modifier = Modifier
                .testTag("InvitedRegistrationButton")
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(height = 12.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}
