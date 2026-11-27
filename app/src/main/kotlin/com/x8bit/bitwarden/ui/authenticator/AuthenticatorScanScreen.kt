package com.x8bit.bitwarden.ui.authenticator

import android.Manifest
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitwarden.ui.platform.base.util.EventsEffect
import com.bitwarden.ui.platform.components.button.BitwardenFilledButton
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.camera.CameraPreview
import com.bitwarden.ui.platform.components.dialog.BitwardenTwoButtonDialog
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.composition.LocalIntentManager
import com.x8bit.bitwarden.ui.platform.composition.LocalPermissionsManager
import com.bitwarden.ui.platform.composition.LocalQrCodeAnalyzer
import com.bitwarden.ui.platform.manager.util.startAppSettingsActivity
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme

/**
 * Camera scan for otpauth and Google Authenticator migration QR codes.
 */
@Composable
fun AuthenticatorScanScreen(
    onClose: () -> Unit,
    onManual: () -> Unit,
    viewModel: AuthenticatorScanViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val analyzer = LocalQrCodeAnalyzer.current
    val intentManager = LocalIntentManager.current
    val view = LocalView.current
    analyzer.onQrCodeScanned = { value ->
        viewModel.trySendAction(AuthenticatorScanAction.Scanned(value))
    }
    TrackCameraPermission(viewModel)
    EventsEffect(viewModel) { event ->
        when (event) {
            AuthenticatorScanEvent.Close -> onClose()
            AuthenticatorScanEvent.Saved -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClose()
            }
            AuthenticatorScanEvent.OpenManual -> onManual()
            AuthenticatorScanEvent.OpenSettings -> intentManager.startAppSettingsActivity()
        }
    }
    if (state.cameraGranted == false) {
        BitwardenTwoButtonDialog(
            title = stringResource(BitwardenString.zh_scan_permission_title),
            message = stringResource(BitwardenString.zh_scan_permission_body),
            confirmButtonText = stringResource(BitwardenString.zh_scan_open_settings),
            dismissButtonText = stringResource(BitwardenString.zh_scan_manual),
            onConfirmClick = {
                viewModel.trySendAction(AuthenticatorScanAction.OpenSettings)
            },
            onDismissClick = {
                viewModel.trySendAction(AuthenticatorScanAction.ManualEntry)
            },
            onDismissRequest = {
                viewModel.trySendAction(AuthenticatorScanAction.Close)
            },
        )
    }
    BitwardenScaffold(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.cameraGranted == true) {
                CameraPreview(
                    analyzer = analyzer,
                    cameraErrorReceive = {
                        viewModel.trySendAction(AuthenticatorScanAction.CameraFailed)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ScanChrome(
                message = state.message,
                onManual = { viewModel.trySendAction(AuthenticatorScanAction.ManualEntry) },
                onClose = { viewModel.trySendAction(AuthenticatorScanAction.Close) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun TrackCameraPermission(viewModel: AuthenticatorScanViewModel) {
    val permissionsManager = LocalPermissionsManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permissionsManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.trySendAction(
                    AuthenticatorScanAction.PermissionResult(
                        granted = permissionsManager.checkPermission(Manifest.permission.CAMERA),
                    ),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(permissionsManager) {
        viewModel.trySendAction(
            AuthenticatorScanAction.PermissionResult(
                granted = permissionsManager.checkPermission(Manifest.permission.CAMERA),
            ),
        )
    }
}

@Composable
private fun ScanChrome(
    message: String?,
    onManual: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(BitwardenTheme.colorScheme.background.primary.copy(alpha = 0.82f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = message ?: stringResource(BitwardenString.zh_scan_aim),
            color = BitwardenTheme.colorScheme.text.primary,
            style = BitwardenTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        BitwardenTextButton(
            label = stringResource(BitwardenString.zh_scan_manual),
            onClick = onManual,
        )
        BitwardenFilledButton(
            label = stringResource(BitwardenString.cancel),
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
