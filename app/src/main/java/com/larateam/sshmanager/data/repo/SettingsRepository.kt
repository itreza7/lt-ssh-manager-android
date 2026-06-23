package com.larateam.sshmanager.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.larateam.sshmanager.data.model.AppSettings
import com.larateam.sshmanager.data.model.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Global settings, persisted as plain preferences (no secrets). */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            theme = p[KEY_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
            terminalFontSizeSp = (p[KEY_FONT] ?: AppSettings.DEFAULT_TERMINAL_FONT_SP)
                .coerceIn(AppSettings.MIN_TERMINAL_FONT_SP, AppSettings.MAX_TERMINAL_FONT_SP),
            batteryPromptDismissed = p[KEY_BATTERY_DISMISSED] ?: false,
            terminalKeys = p[KEY_TERMINAL_KEYS]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_TERMINAL_KEYS,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(theme: AppTheme) = dataStore.edit { it[KEY_THEME] = theme.name }

    suspend fun setTerminalFontSize(sp: Int) = dataStore.edit {
        it[KEY_FONT] = sp.coerceIn(AppSettings.MIN_TERMINAL_FONT_SP, AppSettings.MAX_TERMINAL_FONT_SP)
    }

    suspend fun setBatteryPromptDismissed(dismissed: Boolean) = dataStore.edit {
        it[KEY_BATTERY_DISMISSED] = dismissed
    }

    suspend fun setTerminalKeys(layout: String) = dataStore.edit { it[KEY_TERMINAL_KEYS] = layout }

    private companion object {
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_FONT = intPreferencesKey("terminal_font_sp")
        val KEY_BATTERY_DISMISSED = booleanPreferencesKey("battery_prompt_dismissed")
        val KEY_TERMINAL_KEYS = stringPreferencesKey("terminal_keys")
    }
}
