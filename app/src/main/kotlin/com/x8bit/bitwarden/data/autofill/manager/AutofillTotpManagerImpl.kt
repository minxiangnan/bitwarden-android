package com.x8bit.bitwarden.data.autofill.manager

import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.util.asText
import com.bitwarden.vault.CipherView
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import com.x8bit.bitwarden.data.platform.manager.clipboard.BitwardenClipboardManager
import com.x8bit.bitwarden.data.platform.repository.SettingsRepository
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import com.x8bit.bitwarden.data.vault.repository.model.GenerateTotpResult
import java.time.Clock

/**
 * Default implementation of the [AutofillTotpManager].
 */
class AutofillTotpManagerImpl(
    private val clock: Clock,
    private val clipboardManager: BitwardenClipboardManager,
    private val settingsRepository: SettingsRepository,
    private val vaultRepository: VaultRepository,
    private val authenticatorRepository: AuthenticatorRepository,
) : AutofillTotpManager {
    override suspend fun tryCopyTotpToClipboard(cipherView: CipherView) {
        if (settingsRepository.isAutoCopyTotpDisabled) return
        val copiedLoginCode = copyLoginTotp(cipherView)
        if (copiedLoginCode) return
        val url = cipherView.login?.uris?.firstOrNull()?.uri ?: return
        val code = authenticatorRepository.codeForUrl(url, clock.millis()) ?: return
        clipboardManager.setText(
            text = code,
            toastDescriptorOverride = BitwardenString.verification_code_totp.asText(),
        )
    }

    private suspend fun copyLoginTotp(cipherView: CipherView): Boolean {
        if (cipherView.login?.totp.isNullOrBlank()) return false
        val cipherId = cipherView.id ?: return false
        val totpResult = vaultRepository.generateTotp(
            time = clock.instant(),
            cipherId = cipherId,
        )
        if (totpResult !is GenerateTotpResult.Success) return false
        clipboardManager.setText(
            text = totpResult.code,
            toastDescriptorOverride = BitwardenString.verification_code_totp.asText(),
        )
        return true
    }
}
