package com.x8bit.bitwarden.ui.auth.feature.invitedregistration

import androidx.navigation3.runtime.EntryProviderScope
import com.bitwarden.ui.platform.navigation.NavigationController
import com.bitwarden.ui.platform.navigation.util.slideUpEntry
import com.x8bit.bitwarden.ui.auth.feature.auth.AuthGraph

/**
 * Opens the invited-account form for [emailAddress].
 */
fun NavigationController<AuthGraph>.navigateToInvitedRegistration(emailAddress: String) {
    this.navigate(route = AuthGraph.InvitedRegistrationRoute(emailAddress = emailAddress))
}

/**
 * Adds the invited registration screen to the auth graph.
 */
fun EntryProviderScope<AuthGraph>.invitedRegistrationDestination(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: (String) -> Unit,
) {
    slideUpEntry<AuthGraph.InvitedRegistrationRoute> { route ->
        InvitedRegistrationScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToLogin = onNavigateToLogin,
            viewModel = invitedRegistrationViewModel(route = route),
        )
    }
}
