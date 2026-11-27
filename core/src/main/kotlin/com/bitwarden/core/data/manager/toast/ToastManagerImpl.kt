package com.bitwarden.core.data.manager.toast

import android.content.Context
import android.widget.Toast

/**
 * The default implementation of the [ToastManager].
 */
class ToastManagerImpl(
    private val context: Context,
) : ToastManager {
    private var current: Toast? = null

    override fun show(message: CharSequence, duration: Int) {
        showToast(Toast.makeText(context, message, duration))
    }

    override fun show(messageId: Int, duration: Int) {
        showToast(Toast.makeText(context, messageId, duration))
    }

    override fun cancel() {
        current?.cancel()
        current = null
    }

    private fun showToast(toast: Toast) {
        current?.cancel()
        current = toast
        toast.show()
    }
}
