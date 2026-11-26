package com.x8bit.bitwarden.data.platform.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bitwarden.annotation.OmitFromCoverage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the system installer for a downloaded APK.
 */
interface AppUpdateInstaller {
    /**
     * Whether the user has allowed this app to install packages.
     */
    fun canRequestInstall(): Boolean

    /**
     * System screen for "install unknown apps".
     */
    fun unknownSourcesIntent(): Intent

    /**
     * Installer intent for [file], granted through the app FileProvider.
     */
    fun installIntent(file: File): Intent
}

/**
 * FileProvider plus `ACTION_VIEW`, which brings up the system package installer.
 */
@OmitFromCoverage
@Singleton
class FileProviderAppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppUpdateInstaller {
    override fun canRequestInstall(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    override fun unknownSourcesIntent(): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private const val APK_MIME: String = "application/vnd.android.package-archive"
