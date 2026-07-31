package com.badgr.orbreader.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Wraps the Android TTS engine for natural read-aloud (v3.4.0).
 *
 * Design notes:
 * - Narration speed is a multiplier on the engine's natural cadence (1.0 ≈ 175 wpm),
 *   intentionally decoupled from the reader's RSVP words-per-minute dial. Raising the
 *   RSVP speed no longer speeds up the voice.
 * - Sentences are spoken as whole utterances so the engine applies natural intonation
 *   and punctuation pauses. The display advances mid-sentence via [onRangeStart] word
 *   boundaries when the active engine reports them (Google TTS does; some OEM engines
 *   do not — [rangesObserved] lets the caller fall back to timer-paced advance).
 *
 * Utterance callbacks arrive on a binder thread; continuations and range callbacks
 * resume/fire there, so callers must not assume main-thread delivery.
 */
class TextToSpeechManager(private val context: Context) {

    companion object {
        private const val TAG = "TtsManager"
        // Android TTS speaks ~175 words/min at speech-rate 1.0.
        const val WORDS_PER_MINUTE_AT_UNIT_RATE = 175f
        const val MAX_RATE = 2.0f
        const val MIN_RATE = 0.5f
    }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _initFailed = MutableStateFlow(false)
    val initFailed: StateFlow<Boolean> = _initFailed

    /** True once the active engine has reported at least one word range. */
    private val _rangesObserved = MutableStateFlow(false)
    val rangesObserved: StateFlow<Boolean> = _rangesObserved

    /** Fired when the system takes audio focus (phone call, another media app). */
    var onFocusLost: (() -> Unit)? = null

    private val doneCallbacks  = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private val rangeCallbacks = ConcurrentHashMap<String, (Int, Int) -> Unit>()

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) onEngineReady()
        else {
            Log.w(TAG, "TTS engine init failed: $status")
            _initFailed.value = true
        }
    }

    private fun onEngineReady() {
        val langResult = tts.setLanguage(Locale.getDefault())
        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
            langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Default locale unsupported by TTS engine, using engine default voice")
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                utteranceId?.let {
                    rangeCallbacks.remove(it)
                    doneCallbacks.remove(it)?.invoke(true)
                }
            }
            @Deprecated("Deprecated in API 21, still required for older engines")
            override fun onError(utteranceId: String?) {
                utteranceId?.let {
                    rangeCallbacks.remove(it)
                    doneCallbacks.remove(it)?.invoke(false)
                }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let {
                    rangeCallbacks.remove(it)
                    doneCallbacks.remove(it)?.invoke(false)
                }
            }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (!_rangesObserved.value) {
                    _rangesObserved.value = true
                    Log.i(TAG, "engine reports word ranges — using precise word sync")
                }
                utteranceId?.let { rangeCallbacks[it]?.invoke(start, end) }
            }
        })
        _isReady.value = true
    }

    // ── Voices ────────────────────────────────────────────────────

    /**
     * A short, clean list of usable voices for the current locale, best quality first.
     * Drops network voices, the engine's generic "…-language" locale fallbacks, and
     * (when the device country has enough voices) other-country variants, so the picker
     * shows a handful of real choices instead of dozens of cryptic engine ids.
     */
    fun availableVoices(): List<Voice> {
        val loc = Locale.getDefault()
        return try {
            val usable = tts.voices
                ?.filter {
                    !it.isNetworkConnectionRequired &&
                        !it.name.endsWith("-language", ignoreCase = true) &&
                        it.locale.language == loc.language
                }
                ?.sortedByDescending { it.quality }
                ?: emptyList()
            val sameCountry = usable.filter { it.locale.country.equals(loc.country, ignoreCase = true) }
            (if (sameCountry.size >= 2) sameCountry else usable).take(6)
        } catch (e: Exception) {
            Log.w(TAG, "voices() unavailable: ${e.localizedMessage}")
            emptyList()
        }
    }

    fun setVoice(voiceId: String?) {
        if (voiceId == null) return
        tts.voices?.firstOrNull { it.name == voiceId }?.let { tts.voice = it }
    }

    fun currentVoiceId(): String? = try { tts.voice?.name } catch (e: Exception) { null }

    // ── Audio focus ───────────────────────────────────────────────

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocusLost?.invoke()
        }
    }

    fun requestFocus() {
        if (focusRequest != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        audioManager.requestAudioFocus(req)
        focusRequest = req
    }

    fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // ── Speech ────────────────────────────────────────────────────

    /** Narration speed multiplier on the engine's natural cadence (1.0 ≈ 175 wpm). */
    fun setNarrationSpeed(multiplier: Float) {
        tts.setSpeechRate(multiplier.coerceIn(MIN_RATE, MAX_RATE))
    }

    /**
     * Speaks [text] as one utterance and suspends until the engine finishes it.
     * [onWordRange] fires per word boundary (char offsets into [text]) as the engine
     * speaks, when the active engine supports it. Returns false on engine error or if
     * the utterance could not be queued. Cancellation stops speech immediately.
     */
    suspend fun speakSentenceAwait(
        text: String,
        utteranceId: String,
        onWordRange: (start: Int, end: Int) -> Unit
    ): Boolean {
        if (!_isReady.value) return false
        return suspendCancellableCoroutine { cont ->
            rangeCallbacks[utteranceId] = onWordRange
            doneCallbacks[utteranceId] = { ok -> if (cont.isActive) cont.resume(ok) }
            cont.invokeOnCancellation {
                rangeCallbacks.remove(utteranceId)
                doneCallbacks.remove(utteranceId)
                tts.stop()
            }
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (queued != TextToSpeech.SUCCESS) {
                rangeCallbacks.remove(utteranceId)
                doneCallbacks.remove(utteranceId)
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    fun stop() {
        rangeCallbacks.clear()
        doneCallbacks.clear()
        tts.stop()
        abandonFocus()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
        _isReady.value = false
    }
}
