package com.x8bit.bitwarden.data.platform.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tag prefix for this fork's GitHub releases. */
const val ZH_UPDATE_TAG_PREFIX: String = "v-zh-authenticator-android-"

/** Universal APK published next to the ABI splits. */
const val ZH_UNIVERSAL_APK: String = "com.minxiangnan.bitwarden.zh.apk"

/** Checksum file attached to each release. */
const val ZH_SHA256_SUMS: String = "SHA256SUMS"

/** Public releases endpoint. Drafts are not included in this response. */
const val ZH_RELEASES_URL: String =
    "https://api.github.com/repos/minxiangnan/bitwarden-android/releases"

private val releaseJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * One GitHub release asset.
 */
@Serializable
data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
)

/**
 * The fields of a GitHub release this client uses.
 */
@Serializable
data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

/**
 * A newer release the user can install.
 */
data class UpdateOffer(
    val version: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val sumsUrl: String?,
)

/**
 * What the app remembers between launches.
 */
data class UpdateMemory(
    val lastAutomaticCheckDay: String? = null,
    val snoozedVersion: String? = null,
    val snoozedDay: String? = null,
    val ignoredVersion: String? = null,
)

/**
 * Parses the GitHub releases array.
 */
fun parseReleases(body: String): List<GithubRelease> = releaseJson.decodeFromString(body)

/**
 * Version suffix of a zh authenticator tag, or null when the tag is unrelated.
 */
fun versionOfTag(tag: String): String? {
    if (!tag.startsWith(ZH_UPDATE_TAG_PREFIX)) return null
    val version = tag.removePrefix(ZH_UPDATE_TAG_PREFIX)
    if (version.isEmpty()) return null
    return version
}

/**
 * Compares dotted numeric versions. A trailing `-rc` suffix is ignored, so `1.0.5-rc1`
 * and `1.0.5` compare equal. Callers skip GitHub prereleases separately.
 */
fun compareVersions(left: String, right: String): Int {
    val leftParts = numericParts(left)
    val rightParts = numericParts(right)
    val count = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until count) {
        val difference = leftParts.getOrElse(index) { 0 }
            .compareTo(rightParts.getOrElse(index) { 0 })
        if (difference != 0) return difference
    }
    return 0
}

/**
 * APK for the first supported ABI, otherwise the universal package.
 */
fun selectApk(assets: List<GithubAsset>, abis: List<String>): GithubAsset? {
    val apks = assets.filter { asset ->
        asset.name.endsWith(".apk") && asset.browserDownloadUrl.isNotBlank()
    }
    for (abi in abis) {
        apks.firstOrNull { asset -> asset.name.endsWith("-$abi.apk") }?.let { return it }
    }
    return apks.firstOrNull { asset -> asset.name == ZH_UNIVERSAL_APK }
}

/**
 * The checksum file on the same release, when it was uploaded.
 */
fun selectSums(assets: List<GithubAsset>): GithubAsset? = assets.firstOrNull { asset ->
    asset.name == ZH_SHA256_SUMS && asset.browserDownloadUrl.isNotBlank()
}

/**
 * Newest stable zh release that is newer than [current] and has an installable APK.
 */
fun latestOffer(
    releases: List<GithubRelease>,
    current: String,
    abis: List<String>,
): UpdateOffer? {
    val candidates = releases.mapNotNull { release ->
        if (release.draft || release.prerelease) return@mapNotNull null
        val version = versionOfTag(release.tagName) ?: return@mapNotNull null
        if (compareVersions(version, current) <= 0) return@mapNotNull null
        val apk = selectApk(release.assets, abis) ?: return@mapNotNull null
        Triple(version, release, apk)
    }
    val chosen = candidates.maxWithOrNull { left, right ->
        compareVersions(left.first, right.first)
    } ?: return null
    val (version, release, apk) = chosen
    return UpdateOffer(
        version = version,
        notes = release.body?.trim().orEmpty(),
        apkName = apk.name,
        apkUrl = apk.browserDownloadUrl,
        sumsUrl = selectSums(release.assets)?.browserDownloadUrl,
    )
}

/**
 * Startup hits the network at most once per calendar day.
 */
fun shouldCheckOnStartup(memory: UpdateMemory, today: String): Boolean =
    memory.lastAutomaticCheckDay != today

/**
 * A manual check always shows a newer version. An automatic check stays quiet when the user
 * ignored that version, or asked to be reminded tomorrow.
 */
fun shouldPrompt(
    memory: UpdateMemory,
    version: String,
    today: String,
    manual: Boolean,
): Boolean {
    if (manual) return true
    if (memory.ignoredVersion == version) return false
    if (memory.snoozedVersion == version && memory.snoozedDay == today) return false
    return true
}

private fun numericParts(version: String): List<Int> = version
    .substringBefore('-')
    .split('.')
    .map { part -> part.toIntOrNull() ?: 0 }
