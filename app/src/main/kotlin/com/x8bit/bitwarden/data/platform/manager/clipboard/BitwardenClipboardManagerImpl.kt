package com.x8bit.bitwarden.data.platform.manager.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.getSystemService
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bitwarden.annotation.OmitFromCoverage
import com.bitwarden.core.data.manager.toast.ToastManager
import com.bitwarden.core.util.isBuildVersionAtLeast
import com.bitwarden.ui.platform.base.util.toAnnotatedString
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.util.Text
import com.x8bit.bitwarden.data.platform.repository.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Default implementation of the [BitwardenClipboardManager] interface.
 */
@OmitFromCoverage
class BitwardenClipboardManagerImpl(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val toastManager: ToastManager,
) : BitwardenClipboardManager {
    private val clipboardManager: ClipboardManager = requireNotNull(context.getSystemService())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dismissConfirmation = Runnable { toastManager.cancel() }

    private val clearClipboardFrequencySeconds: Int?
        get() = settingsRepository.clearClipboardFrequency.frequencySeconds

    override fun setText(
        text: AnnotatedString,
        isSensitive: Boolean,
        toastDescriptorOverride: String?,
        showToast: Boolean,
    ) {
        clipboardManager.setPrimaryClip(
            ClipData
                .newPlainText("", text)
                .apply {
                    description.extras = PersistableBundle().apply {
                        this.putBoolean(
                            if (isBuildVersionAtLeast(version = Build.VERSION_CODES.TIRAMISU)) {
                                ClipDescription.EXTRA_IS_SENSITIVE
                            } else {
                                "android.content.extra.IS_SENSITIVE"
                            },
                            isSensitive,
                        )
                    }
                },
        )
        if (showToast && !isBuildVersionAtLeast(version = Build.VERSION_CODES.TIRAMISU)) {
            val descriptor = toastDescriptorOverride
                ?.let { context.resources.getString(BitwardenString.value_has_been_copied, it) }
                ?: context.resources.getString(
                    BitwardenString.value_has_been_copied,
                    context.resources.getString(BitwardenString.value),
                )
            showReplacingConfirmation(descriptor)
        }

        val frequency = clearClipboardFrequencySeconds ?: return
        val clearClipboardRequest: OneTimeWorkRequest =
            OneTimeWorkRequest
                .Builder(ClearClipboardWorker::class.java)
                .setInitialDelay(frequency.toLong(), TimeUnit.SECONDS)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "ClearClipboard",
            ExistingWorkPolicy.REPLACE,
            clearClipboardRequest,
        )
    }

    override fun setText(
        text: String,
        isSensitive: Boolean,
        toastDescriptorOverride: String?,
        showToast: Boolean,
    ) {
        setText(text.toAnnotatedString(), isSensitive, toastDescriptorOverride, showToast)
    }

    private fun showReplacingConfirmation(message: String) {
        mainHandler.removeCallbacks(dismissConfirmation)
        toastManager.show(message = message)
        mainHandler.postDelayed(dismissConfirmation, COPY_CONFIRMATION_MS)
    }

    override fun setText(text: String, isSensitive: Boolean, toastDescriptorOverride: Text) {
        setText(
            text.toAnnotatedString(),
            isSensitive,
            toastDescriptorOverride.toString(context.resources),
        )
    }

    override fun setText(text: Text, isSensitive: Boolean, toastDescriptorOverride: String?) {
        setText(text.toString(context.resources), isSensitive, toastDescriptorOverride)
    }

    private companion object {
        const val COPY_CONFIRMATION_MS: Long = 1_200L
    }
}
