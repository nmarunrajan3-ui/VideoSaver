package com.instasaver.app.data.repository

import com.instasaver.app.data.local.PreferencesDataStore
import com.instasaver.app.data.local.ThemeMode
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val preferencesDataStore: PreferencesDataStore) {

    val themeMode: Flow<ThemeMode> = preferencesDataStore.themeModeFlow
    val autoDownloadClipboard: Flow<Boolean> = preferencesDataStore.autoDownloadClipboardFlow
    val warnDuplicates: Flow<Boolean> = preferencesDataStore.warnDuplicatesFlow
    val notifyCompletion: Flow<Boolean> = preferencesDataStore.notifyCompletionFlow

    suspend fun setThemeMode(themeMode: ThemeMode) {
        preferencesDataStore.setThemeMode(themeMode)
    }

    suspend fun setAutoDownloadClipboard(enabled: Boolean) {
        preferencesDataStore.setAutoDownloadClipboard(enabled)
    }

    suspend fun setWarnDuplicates(enabled: Boolean) {
        preferencesDataStore.setWarnDuplicates(enabled)
    }

    suspend fun setNotifyCompletion(enabled: Boolean) {
        preferencesDataStore.setNotifyCompletion(enabled)
    }
}
