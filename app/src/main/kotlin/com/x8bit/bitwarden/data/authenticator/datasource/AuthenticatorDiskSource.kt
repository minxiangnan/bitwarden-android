package com.x8bit.bitwarden.data.authenticator.datasource

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores ciphertext authenticator rows and recent copy order on disk.
 * Secrets are not written in plaintext.
 */
@Singleton
class AuthenticatorDiskSource @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Cached `GET /authenticator` JSON for [userId], or null.
     */
    fun readCache(userId: String): String? = preferences.getString(cacheKey(userId), null)

    /**
     * Replaces the ciphertext cache for [userId].
     */
    fun writeCache(userId: String, json: String) {
        preferences.edit { putString(cacheKey(userId), json) }
    }

    /**
     * Recently copied entry ids, newest first.
     */
    fun readRecent(userId: String): List<String> =
        preferences.getString(recentKey(userId), null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /**
     * Stores recently copied entry ids.
     */
    fun writeRecent(userId: String, ids: List<String>) {
        preferences.edit { putString(recentKey(userId), ids.joinToString("\n")) }
    }

    /**
     * Group ids collapsed on this device. An empty string is 未分组.
     */
    fun readCollapsed(userId: String): List<String> =
        preferences.getString(collapsedKey(userId), null)
            ?.split('\n')
            .orEmpty()

    /**
     * Stores collapsed group ids for this device only.
     */
    fun writeCollapsed(userId: String, ids: List<String>) {
        preferences.edit { putString(collapsedKey(userId), ids.joinToString("\n")) }
    }

    private fun cacheKey(userId: String): String = "cache_$userId"

    private fun recentKey(userId: String): String = "recent_$userId"

    private fun collapsedKey(userId: String): String = "collapsed_$userId"

    private companion object {
        const val PREFS: String = "zh_authenticator"
    }
}
