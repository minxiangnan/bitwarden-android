package com.bitwarden.core.data.manager.toast

import android.content.Context
import android.widget.Toast
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToastManagerTest {
    private val context: Context = mockk()
    private val toast: Toast = mockk {
        every { show() } just runs
        every { cancel() } just runs
    }
    private val toastManager: ToastManager = ToastManagerImpl(
        context = context,
    )

    @BeforeEach
    fun setup() {
        mockkStatic(Toast::class)
        every { Toast.makeText(context, any<CharSequence>(), any()) } returns toast
        every { Toast.makeText(context, any<Int>(), any()) } returns toast
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Toast::class)
    }

    @Test
    fun `show with string should call show on Toast`() {
        val message = "Test"
        toastManager.show(message = message)
        verify {
            Toast.makeText(context, message, Toast.LENGTH_SHORT)
            toast.show()
        }

        toastManager.show(message = message, Toast.LENGTH_LONG)
        verify {
            Toast.makeText(context, message, Toast.LENGTH_LONG)
            toast.show()
        }
    }

    @Test
    fun `show with string resource should call show on Toast`() {
        val messageId = 555
        toastManager.show(messageId = messageId)
        verify {
            Toast.makeText(context, messageId, Toast.LENGTH_SHORT)
            toast.show()
        }

        toastManager.show(messageId = messageId, Toast.LENGTH_LONG)
        verify {
            Toast.makeText(context, messageId, Toast.LENGTH_LONG)
            toast.show()
        }
    }

    @Test
    fun `a second show cancels the toast already on screen`() {
        toastManager.show(message = "one")
        toastManager.show(message = "two")

        verify(exactly = 1) { toast.cancel() }
        verify(exactly = 2) { toast.show() }
    }

    @Test
    fun `cancel removes the current toast`() {
        toastManager.show(message = "one")
        toastManager.cancel()

        verify(exactly = 1) { toast.cancel() }
    }
}
