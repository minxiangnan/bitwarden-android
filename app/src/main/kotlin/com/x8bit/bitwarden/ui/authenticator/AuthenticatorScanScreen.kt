package com.x8bit.bitwarden.ui.authenticator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitwarden.ui.platform.base.util.EventsEffect
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.camera.CameraPreview
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.composition.LocalQrCodeAnalyzer
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import androidx.compose.ui.res.stringResource

/**
 * Camera scan for otpauth and Google Authenticator migration QR codes.
 */
@Composable
fun AuthenticatorScanScreen(
    onClose: () -> Unit,
    onOpenEdit: () -> Unit,
    viewModel: AuthenticatorScanViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val analyzer = LocalQrCodeAnalyzer.current
    analyzer.onQrCodeScanned = { value ->
        viewModel.trySendAction(AuthenticatorScanAction.Scanned(value))
    }
    EventsEffect(viewModel) { event ->
        when (event) {
            AuthenticatorScanEvent.Close -> onClose()
            AuthenticatorScanEvent.OpenEdit -> onOpenEdit()
            is AuthenticatorScanEvent.Imported -> onClose()
        }
    }
    BitwardenScaffold(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(
                analyzer = analyzer,
                cameraErrorReceive = {
                    viewModel.trySendAction(
                        AuthenticatorScanAction.Scanned(""),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
            BitwardenTextButton(
                label = stringResource(BitwardenString.cancel),
                onClick = { viewModel.trySendAction(AuthenticatorScanAction.Close) },
                modifier = Modifier.align(Alignment.TopStart),
            )
            state.message?.let { message ->
                Text(
                    text = message,
                    color = BitwardenTheme.colorScheme.text.reversed,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
