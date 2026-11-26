package com.x8bit.bitwarden.data.platform.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppUpdatePolicyTest {
    @Test
    fun `latest stable tag newer than the installed version is chosen`() {
        val releases = parseReleases(
            """
            [
              {
                "tag_name": "v-zh-authenticator-android-1.0.4",
                "body": "current",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {"name": "com.minxiangnan.bitwarden.zh-arm64-v8a.apk", "browser_download_url": "https://example/1.0.4-arm.apk"},
                  {"name": "SHA256SUMS", "browser_download_url": "https://example/1.0.4-sums"}
                ]
              },
              {
                "tag_name": "v-zh-authenticator-android-1.0.10",
                "body": "ten",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {"name": "com.minxiangnan.bitwarden.zh-arm64-v8a.apk", "browser_download_url": "https://example/1.0.10-arm.apk"},
                  {"name": "com.minxiangnan.bitwarden.zh.apk", "browser_download_url": "https://example/1.0.10-universal.apk"},
                  {"name": "SHA256SUMS", "browser_download_url": "https://example/1.0.10-sums"}
                ]
              },
              {
                "tag_name": "v-zh-authenticator-android-9.0.0",
                "body": "draft",
                "draft": true,
                "prerelease": false,
                "assets": [
                  {"name": "com.minxiangnan.bitwarden.zh-arm64-v8a.apk", "browser_download_url": "https://example/draft.apk"}
                ]
              },
              {
                "tag_name": "v-zh-authenticator-android-8.0.0",
                "body": "pre",
                "draft": false,
                "prerelease": true,
                "assets": [
                  {"name": "com.minxiangnan.bitwarden.zh-arm64-v8a.apk", "browser_download_url": "https://example/pre.apk"}
                ]
              },
              {
                "tag_name": "v1.2.3",
                "body": "other",
                "assets": []
              }
            ]
            """.trimIndent(),
        )

        val offer = latestOffer(releases, current = "1.0.5", abis = listOf("arm64-v8a"))

        assertEquals("1.0.10", offer?.version)
        assertEquals("ten", offer?.notes)
        assertEquals("com.minxiangnan.bitwarden.zh-arm64-v8a.apk", offer?.apkName)
        assertEquals("https://example/1.0.10-arm.apk", offer?.apkUrl)
        assertEquals("https://example/1.0.10-sums", offer?.sumsUrl)
        assertNull(latestOffer(releases, current = "1.0.10", abis = listOf("arm64-v8a")))
    }

    @Test
    fun `abi apk is preferred and x86 does not take the x86_64 package`() {
        val assets = listOf(
            GithubAsset("com.minxiangnan.bitwarden.zh-x86_64.apk", "https://example/x64.apk"),
            GithubAsset("com.minxiangnan.bitwarden.zh-x86.apk", "https://example/x86.apk"),
            GithubAsset(ZH_UNIVERSAL_APK, "https://example/universal.apk"),
        )

        assertEquals(
            "https://example/x86.apk",
            selectApk(assets, listOf("x86"))?.browserDownloadUrl,
        )
        assertEquals(
            "https://example/universal.apk",
            selectApk(assets, listOf("armeabi-v7a"))?.browserDownloadUrl,
        )
        assertEquals(
            "https://example/x64.apk",
            selectApk(assets, listOf("x86_64", "x86"))?.browserDownloadUrl,
        )
    }

    @Test
    fun `startup checks once a day and snooze or ignore hides the automatic prompt`() {
        val today = "2026-11-26"
        val fresh = UpdateMemory()
        assertTrue(shouldCheckOnStartup(fresh, today))
        assertFalse(shouldCheckOnStartup(fresh.copy(lastAutomaticCheckDay = today), today))

        assertTrue(shouldPrompt(fresh, "1.0.5", today, manual = false))
        assertFalse(
            shouldPrompt(
                fresh.copy(snoozedVersion = "1.0.5", snoozedDay = today),
                "1.0.5",
                today,
                manual = false,
            ),
        )
        assertTrue(
            shouldPrompt(
                fresh.copy(snoozedVersion = "1.0.5", snoozedDay = "2026-11-25"),
                "1.0.5",
                today,
                manual = false,
            ),
        )
        assertFalse(
            shouldPrompt(fresh.copy(ignoredVersion = "1.0.5"), "1.0.5", today, manual = false),
        )
        assertTrue(
            shouldPrompt(fresh.copy(ignoredVersion = "1.0.5"), "1.0.5", today, manual = true),
        )
    }

    @Test
    fun `checksum file matches sha256sum output`() {
        val bytes = "apk".toByteArray()
        val hash = sha256Hex(bytes)
        val sums = parseSha256Sums(
            """
            $hash  com.minxiangnan.bitwarden.zh-arm64-v8a.apk
            not-a-hash  skipped.txt
            ${hash.uppercase()} *com.minxiangnan.bitwarden.zh.apk
            """.trimIndent(),
        )

        assertEquals(hash, sums["com.minxiangnan.bitwarden.zh-arm64-v8a.apk"])
        assertEquals(hash, sums[ZH_UNIVERSAL_APK])
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray()),
        )
        assertTrue(compareVersions("1.0.10", "1.0.9") > 0)
        assertEquals(0, compareVersions("1.0.5", "1.0.5"))
    }
}
