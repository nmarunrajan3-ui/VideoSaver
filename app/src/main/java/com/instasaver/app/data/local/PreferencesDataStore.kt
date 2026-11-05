package com.instasaver.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

class PreferencesDataStore(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_DOWNLOAD_CLIPBOARD = booleanPreferencesKey("auto_download_clipboard")
        val WARN_DUPLICATES = booleanPreferencesKey("warn_duplicates")
        val NOTIFY_COMPLETION = booleanPreferencesKey("notify_completion")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name
        runCatching { ThemeMode.valueOf(modeStr) }.getOrDefault(ThemeMode.DARK)
    }

    val autoDownloadClipboardFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTO_DOWNLOAD_CLIPBOARD] ?: false
    }

    val warnDuplicatesFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.WARN_DUPLICATES] ?: true
    }

    val notifyCompletionFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NOTIFY_COMPLETION] ?: true
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setAutoDownloadClipboard(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_DOWNLOAD_CLIPBOARD] = enabled
        }
    }

    suspend fun setWarnDuplicates(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WARN_DUPLICATES] = enabled
        }
    }

    suspend fun setNotifyCompletion(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFY_COMPLETION] = enabled
        }
    }
}
