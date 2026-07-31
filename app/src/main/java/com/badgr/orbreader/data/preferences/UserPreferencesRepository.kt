package com.badgr.orbreader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "badgr_user_prefs")

const val THEME_SYSTEM = 0
const val THEME_LIGHT  = 1
const val THEME_DARK   = 2

const val TTS_DISPLAY_ORP     = 0  // single focus word (ORP), advances with the voice
const val TTS_DISPLAY_FLOWING = 1  // Audible-style flowing text with a moving highlight

// Narration speed is a multiplier on the engine's natural cadence (1.0 ≈ 175 wpm),
// intentionally decoupled from the RSVP words-per-minute dial.
const val TTS_SPEED_MIN  = 0.5f
const val TTS_SPEED_MAX  = 2.0f
const val TTS_SPEED_STEP = 0.25f

data class UserPreferences(
    val defaultWpm              : Int     = 150,
    val fontSize                : Int     = 36,
    val showOrpColor            : Boolean = true,
    val orpColorIndex           : Int     = 0,
    val isPro                   : Boolean = false,
    val themeMode               : Int     = THEME_SYSTEM,
    val fontIndex               : Int     = 0,
    val chunkSize               : Int     = 1,
    val sentencePauseMultiplier : Float   = 2.0f,
    val clausePauseMultiplier   : Float   = 1.5f,
    val colorBlindnessMode      : Int     = 0,
    val hasSeenOnboarding       : Boolean = false,
    val hasSeenHelp             : Boolean = false,
    val ttsEnabled              : Boolean = false,
    val ttsNarrationSpeed       : Float   = 1.0f,
    val ttsDisplayMode          : Int     = TTS_DISPLAY_ORP,
    val ttsVoiceId              : String? = null
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_WPM              = intPreferencesKey("default_wpm")
        val FONT_SIZE                = intPreferencesKey("font_size")
        val SHOW_ORP_COLOR           = booleanPreferencesKey("show_orp_color")
        val ORP_COLOR_IDX            = intPreferencesKey("orp_color_index")
        val IS_PRO                   = booleanPreferencesKey("is_pro")
        val THEME_MODE               = intPreferencesKey("theme_mode")
        val FONT_INDEX               = intPreferencesKey("font_index")
        val CHUNK_SIZE               = intPreferencesKey("chunk_size")
        val SENTENCE_PAUSE_MULT      = floatPreferencesKey("sentence_pause_multiplier")
        val CLAUSE_PAUSE_MULT        = floatPreferencesKey("clause_pause_multiplier")
        val COLOR_BLINDNESS_MODE     = intPreferencesKey("color_blindness_mode")
        val HAS_SEEN_ONBOARDING      = booleanPreferencesKey("has_seen_onboarding")
        val HAS_SEEN_HELP            = booleanPreferencesKey("has_seen_help")
        val TTS_ENABLED              = booleanPreferencesKey("tts_enabled")
        val TTS_NARRATION_SPEED      = floatPreferencesKey("tts_narration_speed")
        val TTS_DISPLAY_MODE         = intPreferencesKey("tts_display_mode")
        val TTS_VOICE_ID             = stringPreferencesKey("tts_voice_id")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            UserPreferences(
                defaultWpm              = prefs[Keys.DEFAULT_WPM]          ?: 150,
                fontSize                = prefs[Keys.FONT_SIZE]            ?: 36,
                showOrpColor            = prefs[Keys.SHOW_ORP_COLOR]       ?: true,
                orpColorIndex           = prefs[Keys.ORP_COLOR_IDX]        ?: 0,
                isPro                   = prefs[Keys.IS_PRO]               ?: false,
                themeMode               = prefs[Keys.THEME_MODE]           ?: THEME_SYSTEM,
                fontIndex               = prefs[Keys.FONT_INDEX]           ?: 0,
                chunkSize               = prefs[Keys.CHUNK_SIZE]           ?: 1,
                sentencePauseMultiplier = prefs[Keys.SENTENCE_PAUSE_MULT]  ?: 2.0f,
                clausePauseMultiplier   = prefs[Keys.CLAUSE_PAUSE_MULT]    ?: 1.5f,
                colorBlindnessMode      = prefs[Keys.COLOR_BLINDNESS_MODE] ?: 0,
                hasSeenOnboarding       = prefs[Keys.HAS_SEEN_ONBOARDING]  ?: false,
                hasSeenHelp             = prefs[Keys.HAS_SEEN_HELP]        ?: false,
                ttsEnabled              = prefs[Keys.TTS_ENABLED]          ?: false,
                ttsNarrationSpeed       = prefs[Keys.TTS_NARRATION_SPEED]  ?: 1.0f,
                ttsDisplayMode          = prefs[Keys.TTS_DISPLAY_MODE]      ?: TTS_DISPLAY_ORP,
                ttsVoiceId              = prefs[Keys.TTS_VOICE_ID]
            )
        }

    suspend fun setDefaultWpm(wpm: Int)                    { context.dataStore.edit { it[Keys.DEFAULT_WPM]          = wpm.coerceIn(60, 1200) } }
    suspend fun setFontSize(size: Int)                     { context.dataStore.edit { it[Keys.FONT_SIZE]            = size.coerceIn(16, 52) } }
    suspend fun setShowOrpColor(show: Boolean)             { context.dataStore.edit { it[Keys.SHOW_ORP_COLOR]       = show } }
    suspend fun setOrpColorIndex(index: Int)               { context.dataStore.edit { it[Keys.ORP_COLOR_IDX]        = index.coerceIn(0, 4) } }
    suspend fun setIsPro(unlocked: Boolean)                { context.dataStore.edit { it[Keys.IS_PRO]               = unlocked } }
    suspend fun setThemeMode(mode: Int)                    { context.dataStore.edit { it[Keys.THEME_MODE]           = mode.coerceIn(0, 2) } }
    suspend fun setFontIndex(index: Int)                   { context.dataStore.edit { it[Keys.FONT_INDEX]           = index.coerceIn(0, 5) } }
    suspend fun setChunkSize(size: Int)                    { context.dataStore.edit { it[Keys.CHUNK_SIZE]           = size.coerceIn(1, 4) } }
    suspend fun setSentencePauseMultiplier(mult: Float)    { context.dataStore.edit { it[Keys.SENTENCE_PAUSE_MULT]  = mult.coerceIn(1.0f, 3.0f) } }
    suspend fun setClausePauseMultiplier(mult: Float)      { context.dataStore.edit { it[Keys.CLAUSE_PAUSE_MULT]    = mult.coerceIn(1.0f, 3.0f) } }
    suspend fun setColorBlindnessMode(mode: Int)           { context.dataStore.edit { it[Keys.COLOR_BLINDNESS_MODE] = mode.coerceIn(0, 4) } }
    suspend fun setHasSeenOnboarding(seen: Boolean)        { context.dataStore.edit { it[Keys.HAS_SEEN_ONBOARDING]  = seen } }
    suspend fun setHasSeenHelp(seen: Boolean)              { context.dataStore.edit { it[Keys.HAS_SEEN_HELP]        = seen } }
    suspend fun setTtsEnabled(enabled: Boolean)            { context.dataStore.edit { it[Keys.TTS_ENABLED]          = enabled } }
    suspend fun setTtsNarrationSpeed(speed: Float)         { context.dataStore.edit { it[Keys.TTS_NARRATION_SPEED]  = speed.coerceIn(TTS_SPEED_MIN, TTS_SPEED_MAX) } }
    suspend fun setTtsDisplayMode(mode: Int)               { context.dataStore.edit { it[Keys.TTS_DISPLAY_MODE]      = mode.coerceIn(TTS_DISPLAY_ORP, TTS_DISPLAY_FLOWING) } }
    suspend fun setTtsVoiceId(voiceId: String?)            { context.dataStore.edit { if (voiceId == null) it.remove(Keys.TTS_VOICE_ID) else it[Keys.TTS_VOICE_ID] = voiceId } }
}
