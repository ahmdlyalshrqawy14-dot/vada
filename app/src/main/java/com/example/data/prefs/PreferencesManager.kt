package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "voda_user_prefs")

class PreferencesManager private constructor(private val context: Context) {

    companion object {
        val KEY_LANGUAGE = stringPreferencesKey("app_language") // "ar" or "en"
        val KEY_DARK_MODE = booleanPreferencesKey("app_dark_mode") // default true
        val KEY_CUSTOM_SAF_URI = stringPreferencesKey("custom_saf_uri")
        val KEY_LAST_PRESET = stringPreferencesKey("last_preset") // "HEAVY", "MEDIUM", "LIGHT"
        val KEY_NAMING_PATTERN = stringPreferencesKey("naming_pattern") // "{name}_vada"
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled") // default true
        val KEY_SHOW_TECHNICAL_BADGES = booleanPreferencesKey("show_technical_badges") // default false

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferencesManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_LANGUAGE] ?: "ar"
    }
    val languageCode: Flow<String> = languageFlow

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_MODE] ?: true
    }
    val darkTheme: Flow<Boolean> = darkModeFlow

    val customSafUriFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_SAF_URI]
    }
    val safStorageUri: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CUSTOM_SAF_URI] ?: ""
    }

    val lastPresetFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_PRESET] ?: "MEDIUM"
    }

    val namingPatternFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_NAMING_PATTERN] ?: "{name}_compressed"
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val showTechnicalBadgesFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SHOW_TECHNICAL_BADGES] ?: false
    }

    suspend fun setLastPreset(presetName: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_PRESET] = presetName
        }
    }

    suspend fun setNamingPattern(pattern: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NAMING_PATTERN] = pattern
        }
    }

    suspend fun setShowTechnicalBadges(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHOW_TECHNICAL_BADGES] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LANGUAGE] = languageCode
        }
    }
    suspend fun setLanguageCode(code: String) = setLanguage(code)

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_MODE] = enabled
        }
    }
    suspend fun setDarkTheme(enabled: Boolean) = setDarkMode(enabled)

    suspend fun setCustomSafUri(uriString: String?) {
        context.dataStore.edit { preferences ->
            if (uriString != null) {
                preferences[KEY_CUSTOM_SAF_URI] = uriString
            } else {
                preferences.remove(KEY_CUSTOM_SAF_URI)
            }
        }
    }
    suspend fun setSafStorageUri(uriString: String) = setCustomSafUri(uriString)
}
