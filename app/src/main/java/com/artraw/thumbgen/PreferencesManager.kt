package com.artraw.thumbgen

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "thumbgen_preferences")

data class ApiKeysConfig(
    val llmKey: String = "",
    val cfAccountId: String = "",
    val cfApiToken: String = "",
    val nvidiaKey: String = "",
    val pollinationsKey: String = ""
)

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_LLM = stringPreferencesKey("llm_api_key")
        val KEY_CF_ACCOUNT = stringPreferencesKey("cf_account_id")
        val KEY_CF_TOKEN = stringPreferencesKey("cf_api_token")
        val KEY_NVIDIA = stringPreferencesKey("nvidia_api_key")
        val KEY_POLLINATIONS = stringPreferencesKey("pollinations_api_key")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_PREFERRED_PROVIDER = stringPreferencesKey("preferred_provider")
    }

    val apiKeysFlow: Flow<ApiKeysConfig> = context.dataStore.data.map { preferences ->
        ApiKeysConfig(
            llmKey = preferences[KEY_LLM] ?: "",
            cfAccountId = preferences[KEY_CF_ACCOUNT] ?: "",
            cfApiToken = preferences[KEY_CF_TOKEN] ?: "",
            nvidiaKey = preferences[KEY_NVIDIA] ?: "",
            pollinationsKey = preferences[KEY_POLLINATIONS] ?: ""
        )
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val preferredProviderFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PREFERRED_PROVIDER] ?: "AUTO"
    }

    suspend fun saveApiKeys(config: ApiKeysConfig) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LLM] = config.llmKey.trim()
            preferences[KEY_CF_ACCOUNT] = config.cfAccountId.trim()
            preferences[KEY_CF_TOKEN] = config.cfApiToken.trim()
            preferences[KEY_NVIDIA] = config.nvidiaKey.trim()
            preferences[KEY_POLLINATIONS] = config.pollinationsKey.trim()
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setPreferredProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PREFERRED_PROVIDER] = provider
        }
    }
}
