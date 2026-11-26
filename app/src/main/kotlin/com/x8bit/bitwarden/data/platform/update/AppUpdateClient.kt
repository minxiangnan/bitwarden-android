package com.x8bit.bitwarden.data.platform.update

import java.io.File

/**
 * Reads GitHub releases and downloads an APK.
 */
interface AppUpdateClient {
    /**
     * Published releases, newest page first.
     */
    suspend fun fetchReleases(): List<GithubRelease>

    /**
     * Writes [url] to [dest], reporting bytes read and the content length (`-1` when unknown).
     */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (read: Long, total: Long) -> Unit,
    )

    /**
     * Downloads a small text file, such as `SHA256SUMS`.
     */
    suspend fun downloadText(url: String): String

    /**
     * Lowercase hex SHA-256 of [file].
     */
    fun sha256(file: File): String
}
