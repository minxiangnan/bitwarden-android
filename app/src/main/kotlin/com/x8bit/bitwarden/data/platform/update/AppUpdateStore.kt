package com.x8bit.bitwarden.data.platform.update

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the daily check, a one-day snooze, and an ignored version.
 */
interface AppUpdateStore {
    /**
     * The values written by earlier checks.
     */
    fun read(): UpdateMemory

    /**
     * Records that an automatic check ran on [day] (`yyyy-MM-dd`).
     */
    fun markAutomaticCheck(day: String)

    /**
     * Hides [version] for the rest of [day].
     */
    fun snooze(version: String, day: String)

    /**
     * Never automatically prompt for [version] again.
     */
    fun ignore(version: String)
}

/**
 * Plain preferences. The update decision is not a secret.
 */
@Singleton
class SharedPrefsAppUpdateStore @Inject constructor(
    @ApplicationContext context: Context,
) : AppUpdateStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): UpdateMemory = UpdateMemory(
        lastAutomaticCheckDay = preferences.getString(KEY_CHECK_DAY, null),
        snoozedVersion = preferences.getString(KEY_SNOOZE_VERSION, null),
        snoozedDay = preferences.getString(KEY_SNOOZE_DAY, null),
        ignoredVersion = preferences.getString(KEY_IGNORED, null),
    )

    override fun markAutomaticCheck(day: String) {
        preferences.edit().putString(KEY_CHECK_DAY, day).apply()
    }

    override fun snooze(version: String, day: String) {
        preferences.edit()
            .putString(KEY_SNOOZE_VERSION, version)
            .putString(KEY_SNOOZE_DAY, day)
            .apply()
    }

    override fun ignore(version: String) {
        preferences.edit().putString(KEY_IGNORED, version).apply()
    }
}

private const val PREFS_NAME: String = "zh_app_update"
private const val KEY_CHECK_DAY: String = "lastAutomaticCheckDay"
private const val KEY_SNOOZE_VERSION: String = "snoozedVersion"
private const val KEY_SNOOZE_DAY: String = "snoozedDay"
private const val KEY_IGNORED: String = "ignoredVersion"
