package com.x8bit.bitwarden.data.platform.update

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets the settings row ask for a check without owning the dialog.
 */
@Singleton
class AppUpdateCoordinator @Inject constructor() {
    private val manual = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emits once each time the user taps "检查更新".
     */
    val manualChecks: SharedFlow<Unit> = manual.asSharedFlow()

    /**
     * Requests a check that ignores the once-per-day limit and the snooze.
     */
    fun requestManualCheck() {
        manual.tryEmit(Unit)
    }
}
