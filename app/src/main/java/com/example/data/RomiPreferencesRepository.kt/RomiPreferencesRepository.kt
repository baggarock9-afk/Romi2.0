package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SpeakingStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.romiDataStore by preferencesDataStore(name = "romi_companion_prefs")

data class RomiPreferences(
    val nickname: String = "Manish",
    val voiceEnabled: Boolean = true,
    val speakingStyle: SpeakingStyle = SpeakingStyle.HINGLISH
)

class RomiPreferencesRepository(private val context: Context) {

    private object Keys {
        val NICKNAME = stringPreferencesKey("nickname")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val SPEAKING_STYLE = stringPreferencesKey("speaking_style")
    }

    val preferencesFlow: Flow<RomiPreferences> = context.romiDataStore.data.map { prefs ->
        RomiPreferences(
            nickname = prefs[Keys.NICKNAME]?.takeIf { it.isNotBlank() } ?: "Manish",
            voiceEnabled = prefs[Keys.VOICE_ENABLED] ?: true,
            speakingStyle = SpeakingStyle.fromId(prefs[Keys.SPEAKING_STYLE] ?: SpeakingStyle.HINGLISH.id)
        )
    }

    suspend fun setNickname(nickname: String) {
        val cleaned = nickname.trim().take(24).ifBlank { "Manish" }
        context.romiDataStore.edit { prefs ->
            prefs[Keys.NICKNAME] = cleaned
        }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.romiDataStore.edit { prefs ->
            prefs[Keys.VOICE_ENABLED] = enabled
        }
    }

    suspend fun setSpeakingStyle(style: SpeakingStyle) {
        context.romiDataStore.edit { prefs ->
            prefs[Keys.SPEAKING_STYLE] = style.id
        }
    }
}
