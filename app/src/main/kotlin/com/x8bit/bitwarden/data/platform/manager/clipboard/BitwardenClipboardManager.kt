package com.x8bit.bitwarden.data.platform.manager.clipboard

import androidx.compose.ui.text.AnnotatedString
import com.bitwarden.ui.util.Text

/**
 * Wrapper class for using the clipboard.
 */
interface BitwardenClipboardManager {

    /**
     * Places the given [text] into the device's clipboard. Setting the data to [isSensitive] will
     * obfuscate the displayed data on the default popup (true by default). On Android 13 and
     * newer the system already shows a clipboard popup, so this manager does not add another.
     * On older versions a toast is shown unless [showToast] is false. That toast replaces any
     * confirmation already on screen and dismisses after about 1.2 seconds. It is formatted as
     * "[text] copied", or "[toastDescriptorOverride] copied" when a descriptor is provided.
     */
    fun setText(
        text: AnnotatedString,
        isSensitive: Boolean = true,
        toastDescriptorOverride: String? = null,
        showToast: Boolean = true,
    )

    /**
     * See [setText] for more details.
     */
    fun setText(
        text: String,
        isSensitive: Boolean = true,
        toastDescriptorOverride: String? = null,
        showToast: Boolean = true,
    )

    /**
     * See [setText] for more details.
     */
    fun setText(
        text: String,
        isSensitive: Boolean = true,
        toastDescriptorOverride: Text,
    )

    /**
     * See [setText] for more details.
     */
    fun setText(
        text: Text,
        isSensitive: Boolean = true,
        toastDescriptorOverride: String? = null,
    )
}
