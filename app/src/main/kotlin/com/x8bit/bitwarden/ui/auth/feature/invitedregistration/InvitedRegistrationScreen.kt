package com.x8bit.bitwarden.ui.auth.feature.invitedregistration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitwarden.ui.platform.base.util.EventsEffect
import com.bitwarden.ui.platform.base.util.standardHorizontalMargin
import com.bitwarden.ui.platform.components.button.BitwardenFilledButton
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.dialog.BitwardenBasicDialog
import com.bitwarden.ui.platform.components.dialog.BitwardenLoadingDialog
import com.bitwarden.ui.platform.components.field.BitwardenPasswordField
import com.bitwarden.ui.platform.components.field.BitwardenTextField
import com.bitwarden.ui.platform.components.model.CardStyle
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import com.x8bit.bitwarden.ui.auth.feature.auth.AuthGraph

/**
 * Form for an invited email: name, master password, and confirmation.
 */
@Composable
fun InvitedRegistrationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: (String) -> Unit,
    viewModel: InvitedRegistrationViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    EventsEffect(viewModel = viewModel) { event ->
        when (event) {
            is InvitedRegistrationEvent.NavigateToLogin -> onNavigateToLogin(event.email)
        }
    }
    if (state.isLoading) {
        BitwardenLoadingDialog(text = stringResource(BitwardenString.loading))
    }
    state.dialogMessage?.let { message ->
        BitwardenBasicDialog(
            title = stringResource(BitwardenString.an_error_has_occurred),
            message = stringResource(message),
            onDismissRequest = {
                viewModel.trySendAction(InvitedRegistrationAction.DismissDialog)
            },
        )
    }
    BackHandler(onBack = onNavigateBack)
    BitwardenScaffold {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        BitwardenTextButton(
            label = stringResource(BitwardenString.back),
            onClick = onNavigateBack,
        )
        Text(
            text = stringResource(BitwardenString.create_account),
            style = BitwardenTheme.typography.titleLarge,
            color = BitwardenTheme.colorScheme.text.primary,
            modifier = Modifier.standardHorizontalMargin(),
        )
        InvitedRegistrationContent(
            state = state,
            onNameChange = { viewModel.trySendAction(InvitedRegistrationAction.NameChanged(it)) },
            onPasswordChange = {
                viewModel.trySendAction(InvitedRegistrationAction.PasswordChanged(it))
            },
            onConfirmChange = {
                viewModel.trySendAction(InvitedRegistrationAction.ConfirmPasswordChanged(it))
            },
            onSubmit = { viewModel.trySendAction(InvitedRegistrationAction.Submit) },
        )
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun InvitedRegistrationContent(
    state: InvitedRegistrationState,
    onNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = state.email,
            style = BitwardenTheme.typography.bodyLarge,
            color = BitwardenTheme.colorScheme.text.primary,
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(BitwardenString.zh_invited_create_body),
            style = BitwardenTheme.typography.bodySmall,
            color = BitwardenTheme.colorScheme.text.secondary,
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        BitwardenTextField(
            label = stringResource(BitwardenString.name),
            value = state.name,
            onValueChange = onNameChange,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            cardStyle = CardStyle.Full,
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        BitwardenPasswordField(
            label = stringResource(BitwardenString.master_password),
            value = state.password,
            showPassword = showPassword,
            showPasswordChange = { showPassword = it },
            onValueChange = onPasswordChange,
            supportingContent = null,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { confirmFocus.requestFocus() }),
            cardStyle = CardStyle.Full,
            modifier = Modifier
                .focusRequester(passwordFocus)
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        BitwardenPasswordField(
            label = stringResource(BitwardenString.password_confirmation),
            value = state.confirmPassword,
            showPassword = showConfirm,
            showPasswordChange = { showConfirm = it },
            onValueChange = onConfirmChange,
            supportingContent = null,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            cardStyle = CardStyle.Full,
            modifier = Modifier
                .focusRequester(confirmFocus)
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        BitwardenFilledButton(
            label = stringResource(BitwardenString.create_account),
            onClick = onSubmit,
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

/**
 * Creates the view model for [route].
 */
@Composable
fun invitedRegistrationViewModel(
    route: AuthGraph.InvitedRegistrationRoute,
): InvitedRegistrationViewModel = hiltViewModel<
    InvitedRegistrationViewModel,
    InvitedRegistrationViewModel.Factory,
    >(
    creationCallback = { factory -> factory.create(route = route) },
)
