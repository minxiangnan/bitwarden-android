package com.x8bit.bitwarden.data.platform.update

import android.content.Context
import android.os.Build
import com.x8bit.bitwarden.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `versionName` of the running build.
 */
fun interface AppVersionName {
    /**
     * Current `versionName`, compared with the release tag suffix.
     */
    fun current(): String
}

/**
 * ABIs the device can run, most preferred first.
 */
fun interface DeviceAbis {
    /**
     * [Build.SUPPORTED_ABIS] order.
     */
    fun current(): List<String>
}

/**
 * Where the downloaded APK is written.
 */
fun interface UpdateFiles {
    /**
     * Cache file replaced on every download.
     */
    fun apkFile(): File
}

/**
 * Version from the Gradle `versionName`.
 */
@Singleton
class BuildConfigAppVersion @Inject constructor() : AppVersionName {
    override fun current(): String = BuildConfig.VERSION_NAME
}

/**
 * Device ABI list.
 */
@Singleton
class AndroidDeviceAbis @Inject constructor() : DeviceAbis {
    override fun current(): List<String> = Build.SUPPORTED_ABIS.toList()
}

/**
 * `cache/zh-update/update.apk`, which the existing FileProvider cache path can share.
 */
@Singleton
class CacheUpdateFiles @Inject constructor(
    @ApplicationContext context: Context,
) : UpdateFiles {
    private val directory: File = File(context.cacheDir, "zh-update")

    override fun apkFile(): File {
        directory.mkdirs()
        return File(directory, "update.apk")
    }
}
