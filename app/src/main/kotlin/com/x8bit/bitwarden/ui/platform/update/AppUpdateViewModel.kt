package com.x8bit.bitwarden.ui.platform.update

import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.data.platform.update.AppUpdateClient
import com.x8bit.bitwarden.data.platform.update.AppUpdateCoordinator
import com.x8bit.bitwarden.data.platform.update.AppUpdateInstaller
import com.x8bit.bitwarden.data.platform.update.AppUpdateStore
import com.x8bit.bitwarden.data.platform.update.AppVersionName
import com.x8bit.bitwarden.data.platform.update.DeviceAbis
import com.x8bit.bitwarden.data.platform.update.UpdateFiles
import com.x8bit.bitwarden.data.platform.update.UpdateOffer
import com.x8bit.bitwarden.data.platform.update.latestOffer
import com.x8bit.bitwarden.data.platform.update.parseSha256Sums
import com.x8bit.bitwarden.data.platform.update.shouldCheckOnStartup
import com.x8bit.bitwarden.data.platform.update.shouldPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Checks GitHub for a newer zh authenticator release and installs it after the user agrees.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val client: AppUpdateClient,
    private val store: AppUpdateStore,
    private val installer: AppUpdateInstaller,
    private val coordinator: AppUpdateCoordinator,
    private val versionName: AppVersionName,
    private val abis: DeviceAbis,
    private val files: UpdateFiles,
    private val clock: Clock,
) : BaseViewModel<AppUpdateState, AppUpdateEvent, AppUpdateAction>(AppUpdateState()) {

    private var downloadJob: Job? = null
    private var reportedPercent: Int = -1

    init {
        coordinator.manualChecks
            .onEach { sendAction(AppUpdateAction.Internal.ManualCheck) }
            .launchIn(viewModelScope)
        viewModelScope.launch { check(manual = false) }
    }

    override fun handleAction(action: AppUpdateAction) {
        when (action) {
            AppUpdateAction.UpdateNow -> handleUpdateNow()
            AppUpdateAction.Later -> handleLater()
            AppUpdateAction.Ignore -> handleIgnore()
            AppUpdateAction.CancelDownload -> handleCancelDownload()
            AppUpdateAction.DismissMessage -> handleDismissMessage()
            AppUpdateAction.OpenInstallSettings -> handleOpenInstallSettings()
            AppUpdateAction.DismissPermission -> handleDismissPermission()
            AppUpdateAction.Internal.ManualCheck -> handleManualCheck()
            is AppUpdateAction.Internal.PromptReady -> handlePromptReady(action)
            is AppUpdateAction.Internal.Progress -> handleProgress(action)
            is AppUpdateAction.Internal.Downloaded -> handleDownloaded(action)
            is AppUpdateAction.Internal.Notice -> handleNotice(action)
        }
    }

    private fun handleManualCheck() {
        viewModelScope.launch { check(manual = true) }
    }

    private fun handleUpdateNow() {
        val offer = state.offer ?: return
        val existing = state.downloadedFile
        if (existing != null) {
            mutableStateFlow.update { it.copy(showPrompt = false) }
            startInstall(existing)
            return
        }
        mutableStateFlow.update {
            it.copy(showPrompt = false, downloading = true, progress = null, message = null)
        }
        reportedPercent = -1
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch { download(offer) }
    }

    private fun handleLater() {
        val offer = state.offer ?: return
        store.snooze(offer.version, today())
        mutableStateFlow.update { it.copy(showPrompt = false) }
    }

    private fun handleIgnore() {
        val offer = state.offer ?: return
        store.ignore(offer.version)
        mutableStateFlow.update { it.copy(showPrompt = false) }
    }

    private fun handleCancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        mutableStateFlow.update { it.copy(downloading = false, progress = null) }
    }

    private fun handleDismissMessage() {
        mutableStateFlow.update { it.copy(message = null) }
    }

    private fun handleOpenInstallSettings() {
        mutableStateFlow.update { it.copy(askInstallPermission = false, showPrompt = true) }
        sendEvent(AppUpdateEvent.Launch(installer.unknownSourcesIntent()))
    }

    private fun handleDismissPermission() {
        mutableStateFlow.update { it.copy(askInstallPermission = false, showPrompt = true) }
    }

    private fun handlePromptReady(action: AppUpdateAction.Internal.PromptReady) {
        mutableStateFlow.update {
            it.copy(
                offer = action.offer,
                showPrompt = true,
                downloading = false,
                message = null,
            )
        }
    }

    private fun handleProgress(action: AppUpdateAction.Internal.Progress) {
        if (!state.downloading) return
        val fraction = if (action.total <= 0L) {
            null
        } else {
            (action.read.toFloat() / action.total.toFloat()).coerceIn(0f, 1f)
        }
        mutableStateFlow.update { it.copy(progress = fraction) }
    }

    private fun handleDownloaded(action: AppUpdateAction.Internal.Downloaded) {
        mutableStateFlow.update {
            it.copy(downloading = false, progress = null, downloadedFile = action.path)
        }
        startInstall(action.path)
    }

    private fun handleNotice(action: AppUpdateAction.Internal.Notice) {
        mutableStateFlow.update {
            it.copy(downloading = false, progress = null, message = action.message)
        }
    }

    private suspend fun check(manual: Boolean) {
        val today = today()
        if (!manual && !shouldCheckOnStartup(store.read(), today)) return
        if (!manual) store.markAutomaticCheck(today)
        val offer = try {
            latestOffer(
                releases = client.fetchReleases(),
                current = versionName.current(),
                abis = abis.current(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (manual) sendAction(AppUpdateAction.Internal.Notice(AppUpdateMessage.FAILED))
            return
        }
        if (offer == null) {
            if (manual) sendAction(AppUpdateAction.Internal.Notice(AppUpdateMessage.UP_TO_DATE))
            return
        }
        if (!shouldPrompt(store.read(), offer.version, today, manual)) return
        sendAction(AppUpdateAction.Internal.PromptReady(offer))
    }

    @Suppress("LongMethod")
    private suspend fun download(offer: UpdateOffer) {
        val file = files.apkFile()
        try {
            if (file.exists()) file.delete()
            client.download(offer.apkUrl, file) { read, total ->
                val percent = if (total <= 0L) -1 else ((read * PERCENT) / total).toInt()
                if (percent != reportedPercent) {
                    reportedPercent = percent
                    trySendAction(AppUpdateAction.Internal.Progress(read, total))
                }
            }
            val sumsUrl = offer.sumsUrl
            val expected = if (sumsUrl.isNullOrBlank()) {
                null
            } else {
                parseSha256Sums(client.downloadText(sumsUrl))[offer.apkName]
            }
            val actual = client.sha256(file)
            if (expected == null || !expected.equals(actual, ignoreCase = true)) {
                file.delete()
                sendAction(AppUpdateAction.Internal.Notice(AppUpdateMessage.BAD_HASH))
                return
            }
            sendAction(AppUpdateAction.Internal.Downloaded(file.absolutePath))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            sendAction(AppUpdateAction.Internal.Notice(AppUpdateMessage.FAILED))
        }
    }

    private fun startInstall(path: String) {
        if (!installer.canRequestInstall()) {
            mutableStateFlow.update { it.copy(askInstallPermission = true, showPrompt = false) }
            return
        }
        sendEvent(AppUpdateEvent.Launch(installer.installIntent(File(path))))
    }

    private fun today(): String = LocalDate.now(clock).toString()
}

/**
 * Dialogs the activity shows for an update.
 */
data class AppUpdateState(
    val offer: UpdateOffer? = null,
    val showPrompt: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val downloadedFile: String? = null,
    val message: AppUpdateMessage? = null,
    val askInstallPermission: Boolean = false,
)

/**
 * Why a short dialog is showing.
 */
enum class AppUpdateMessage {
    UP_TO_DATE,
    FAILED,
    BAD_HASH,
}

/**
 * One-shot navigation to a system screen.
 */
sealed class AppUpdateEvent {
    /**
     * Starts [intent].
     */
    data class Launch(val intent: Intent) : AppUpdateEvent()
}

/**
 * User and internal actions for the update dialogs.
 */
sealed class AppUpdateAction {
    /**
     * Download, or install when the file is already verified.
     */
    data object UpdateNow : AppUpdateAction()

    /**
     * Hide this version until tomorrow.
     */
    data object Later : AppUpdateAction()

    /**
     * Never automatically prompt for this version again.
     */
    data object Ignore : AppUpdateAction()

    /**
     * Stop the download.
     */
    data object CancelDownload : AppUpdateAction()

    /**
     * Close a message dialog.
     */
    data object DismissMessage : AppUpdateAction()

    /**
     * Open the system "install unknown apps" screen.
     */
    data object OpenInstallSettings : AppUpdateAction()

    /**
     * Close the permission dialog and return to the update prompt.
     */
    data object DismissPermission : AppUpdateAction()

    /**
     * Results of work started by the view model.
     */
    sealed class Internal : AppUpdateAction() {
        /**
         * The settings button was tapped.
         */
        data object ManualCheck : Internal()

        /**
         * A newer release should be shown.
         */
        data class PromptReady(val offer: UpdateOffer) : Internal()

        /**
         * Download progress. [total] is `-1` when the length is unknown.
         */
        data class Progress(val read: Long, val total: Long) : Internal()

        /**
         * [path] passed the checksum and can be installed.
         */
        data class Downloaded(val path: String) : Internal()

        /**
         * A short message for the user.
         */
        data class Notice(val message: AppUpdateMessage) : Internal()
    }
}

private const val PERCENT: Long = 100
