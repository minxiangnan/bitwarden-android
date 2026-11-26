package com.x8bit.bitwarden.ui.platform.update

import android.content.Intent
import app.cash.turbine.test
import com.bitwarden.ui.platform.base.BaseViewModelTest
import com.x8bit.bitwarden.data.platform.update.AppUpdateClient
import com.x8bit.bitwarden.data.platform.update.AppUpdateCoordinator
import com.x8bit.bitwarden.data.platform.update.AppUpdateInstaller
import com.x8bit.bitwarden.data.platform.update.AppUpdateStore
import com.x8bit.bitwarden.data.platform.update.AppVersionName
import com.x8bit.bitwarden.data.platform.update.DeviceAbis
import com.x8bit.bitwarden.data.platform.update.GithubAsset
import com.x8bit.bitwarden.data.platform.update.GithubRelease
import com.x8bit.bitwarden.data.platform.update.UpdateFiles
import com.x8bit.bitwarden.data.platform.update.UpdateMemory
import com.x8bit.bitwarden.data.platform.update.sha256Hex
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val APK_NAME: String = "com.minxiangnan.bitwarden.zh-arm64-v8a.apk"

class AppUpdateViewModelTest : BaseViewModelTest() {
    private val client: AppUpdateClient = mockk()
    private val store: AppUpdateStore = mockk(relaxed = true)
    private val installer: AppUpdateInstaller = mockk()
    private val coordinator = AppUpdateCoordinator()
    private val versionName = AppVersionName { "1.0.4" }
    private val abis = DeviceAbis { listOf("arm64-v8a") }
    private val apk = File("build/test-update.apk")
    private val files = UpdateFiles { apk }
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-26T01:00:00Z"), ZoneOffset.UTC)
    private val installIntent = Intent("install")

    @Test
    fun `startup skips the network when today was already checked`() = runTest {
        every { store.read() } returns UpdateMemory(lastAutomaticCheckDay = "2026-11-26")

        val viewModel = createViewModel()

        assertFalse(viewModel.stateFlow.value.showPrompt)
        coVerify(exactly = 0) { client.fetchReleases() }
    }

    @Test
    fun `startup prompts for a newer release and later snoozes it`() = runTest {
        every { store.read() } returns UpdateMemory()
        every { store.markAutomaticCheck(any()) } just runs
        every { store.snooze(any(), any()) } just runs
        coEvery { client.fetchReleases() } returns listOf(release("1.0.5"))

        val viewModel = createViewModel()

        assertEquals("1.0.5", viewModel.stateFlow.value.offer?.version)
        assertTrue(viewModel.stateFlow.value.showPrompt)
        verify { store.markAutomaticCheck("2026-11-26") }

        viewModel.trySendAction(AppUpdateAction.Later)
        assertFalse(viewModel.stateFlow.value.showPrompt)
        verify { store.snooze("1.0.5", "2026-11-26") }
    }

    @Test
    fun `ignored version stays quiet until the user checks manually`() = runTest {
        every { store.read() } returns UpdateMemory(ignoredVersion = "1.0.5")
        every { store.markAutomaticCheck(any()) } just runs
        coEvery { client.fetchReleases() } returns listOf(release("1.0.5"))

        val viewModel = createViewModel()

        assertFalse(viewModel.stateFlow.value.showPrompt)
        coordinator.requestManualCheck()
        assertTrue(viewModel.stateFlow.value.showPrompt)
    }

    @Test
    fun `manual check reports the current version when nothing is newer`() = runTest {
        every { store.read() } returns UpdateMemory(lastAutomaticCheckDay = "2026-11-26")
        coEvery { client.fetchReleases() } returns listOf(release("1.0.4"))

        val viewModel = createViewModel()
        coordinator.requestManualCheck()

        assertEquals(AppUpdateMessage.UP_TO_DATE, viewModel.stateFlow.value.message)
        assertNull(viewModel.stateFlow.value.offer)
    }

    @Test
    fun `update now installs only after the checksum matches`() = runTest {
        val bytes = "apk-bytes".toByteArray()
        val hash = sha256Hex(bytes)
        every { store.read() } returns UpdateMemory()
        every { store.markAutomaticCheck(any()) } just runs
        coEvery { client.fetchReleases() } returns listOf(release("1.0.5"))
        coEvery { client.download(any(), any(), any()) } coAnswers {
            apk.parentFile?.mkdirs()
            apk.writeBytes(bytes)
            thirdArg<(Long, Long) -> Unit>().invoke(bytes.size.toLong(), bytes.size.toLong())
        }
        coEvery { client.downloadText(any()) } returns "$hash  $APK_NAME\n"
        every { client.sha256(any()) } returns hash
        every { installer.canRequestInstall() } returns true
        every { installer.installIntent(any()) } returns installIntent

        val viewModel = createViewModel()
        viewModel.eventFlow.test {
            viewModel.trySendAction(AppUpdateAction.UpdateNow)
            assertEquals(AppUpdateEvent.Launch(installIntent), awaitItem())
        }
        assertEquals(apk.absolutePath, viewModel.stateFlow.value.downloadedFile)
        assertFalse(viewModel.stateFlow.value.downloading)
    }

    @Test
    fun `a bad checksum does not open the installer`() = runTest {
        every { store.read() } returns UpdateMemory()
        every { store.markAutomaticCheck(any()) } just runs
        coEvery { client.fetchReleases() } returns listOf(release("1.0.5"))
        coEvery { client.download(any(), any(), any()) } coAnswers {
            apk.writeBytes("nope".toByteArray())
        }
        coEvery { client.downloadText(any()) } returns "${"ab".repeat(32)}  $APK_NAME\n"
        every { client.sha256(any()) } returns "ff".repeat(32)

        val viewModel = createViewModel()
        viewModel.trySendAction(AppUpdateAction.UpdateNow)

        assertEquals(AppUpdateMessage.BAD_HASH, viewModel.stateFlow.value.message)
        assertFalse(apk.exists())
    }

    @Test
    fun `missing install permission opens the unknown-sources settings`() = runTest {
        val settings = Intent("settings")
        every { store.read() } returns UpdateMemory(lastAutomaticCheckDay = "2026-11-26")
        every { installer.canRequestInstall() } returns false
        every { installer.unknownSourcesIntent() } returns settings

        val viewModel = createViewModel()
        viewModel.trySendAction(
            AppUpdateAction.Internal.Downloaded(apk.absolutePath),
        )
        viewModel.eventFlow.test {
            viewModel.trySendAction(AppUpdateAction.OpenInstallSettings)
            assertEquals(AppUpdateEvent.Launch(settings), awaitItem())
        }
        assertTrue(viewModel.stateFlow.value.showPrompt)
        assertFalse(viewModel.stateFlow.value.askInstallPermission)
    }

    private fun createViewModel() = AppUpdateViewModel(
        client = client,
        store = store,
        installer = installer,
        coordinator = coordinator,
        versionName = versionName,
        abis = abis,
        files = files,
        clock = clock,
    )

    private fun release(version: String) = GithubRelease(
        tagName = "v-zh-authenticator-android-$version",
        body = "notes",
        assets = listOf(
            GithubAsset(
                name = APK_NAME,
                browserDownloadUrl = "https://example/$version.apk",
            ),
            GithubAsset(
                name = "SHA256SUMS",
                browserDownloadUrl = "https://example/$version.sums",
            ),
        ),
    )
}
