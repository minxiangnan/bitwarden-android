package com.x8bit.bitwarden.data.platform.update.di

import com.x8bit.bitwarden.data.platform.update.AndroidDeviceAbis
import com.x8bit.bitwarden.data.platform.update.AppUpdateClient
import com.x8bit.bitwarden.data.platform.update.AppUpdateInstaller
import com.x8bit.bitwarden.data.platform.update.AppUpdateStore
import com.x8bit.bitwarden.data.platform.update.AppVersionName
import com.x8bit.bitwarden.data.platform.update.BuildConfigAppVersion
import com.x8bit.bitwarden.data.platform.update.CacheUpdateFiles
import com.x8bit.bitwarden.data.platform.update.DeviceAbis
import com.x8bit.bitwarden.data.platform.update.FileProviderAppUpdateInstaller
import com.x8bit.bitwarden.data.platform.update.GithubAppUpdateClient
import com.x8bit.bitwarden.data.platform.update.SharedPrefsAppUpdateStore
import com.x8bit.bitwarden.data.platform.update.UpdateFiles
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the in-app update collaborators.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {
    @Binds
    @Singleton
    abstract fun bindClient(impl: GithubAppUpdateClient): AppUpdateClient

    @Binds
    @Singleton
    abstract fun bindStore(impl: SharedPrefsAppUpdateStore): AppUpdateStore

    @Binds
    @Singleton
    abstract fun bindInstaller(impl: FileProviderAppUpdateInstaller): AppUpdateInstaller

    @Binds
    @Singleton
    abstract fun bindAbis(impl: AndroidDeviceAbis): DeviceAbis

    @Binds
    @Singleton
    abstract fun bindVersion(impl: BuildConfigAppVersion): AppVersionName

    @Binds
    @Singleton
    abstract fun bindFiles(impl: CacheUpdateFiles): UpdateFiles
}
