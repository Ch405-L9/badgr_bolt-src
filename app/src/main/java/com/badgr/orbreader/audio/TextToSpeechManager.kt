package com.badgr.orbreader.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Wraps the Android TTS engine for RSVP-synchronized read-aloud.
 *
 * The reader's TTS mode uses speech as the playback clock: the ViewModel speaks one
 * chunk per utterance via [speakAndAwait] and only advances the display when the
 * engine reports the utterance finished. Utterance callbacks arrive on a binder
 * thread; [speakAndAwait] resumes its continuation there, so callers must not assume
 * main-thread resumption.
 */
class TextToSpeechManager(private val context: Context) {

    companion object {
        private const val TAG = "TtsManager"
        // Android TTS speaks ~175 words/min at rate 1.0. Intelligibility degrades past ~3x.
        const val WORDS_PER_MINUTE_AT_UNIT_RATE = 175f
        const val MAX_RATE = 3.0f
        const val MIN_RATE = 0.5f
        val MAX_EFFECTIVE_WPM = (WORDS_PER_MINUTE_AT_UNIT_RATE * MAX_RATE).toInt()  // ~525
    }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _initFailed = MutableStateFlow(false)
    val initFailed: StateFlow<Boolean> = _initFailed

    /** Fired when the system takes audio focus (phone call, another media app). */
    var onFocusLost: (() -> Unit)? = null

    private val pending = ConcurrentHashMap<String, (Boolean) -> Unit>()

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            onEngineReady()
        } else {
            Log.w(TAG, "TTS engine init failed: $status")
            _initFailed.value = true
        }
    }

    private fun onEngineReady() {
        val langResult = tts.setLanguage(Locale.getDefault())
        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
            langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fall back to whatever default voice the engine has rather than failing hard.
            Log.w(TAG, "Default locale unsupported by TTS engine, using engine default voice")
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.invoke(true) }
            }
            @Deprecated("Deprecated in API 21, still required for older engines")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.invoke(false) }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { pending.remove(it)?.invoke(false) }
            }
        })
        _isReady.value = true
    }

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

    /** Maps reader WPM onto the engine's speech-rate scale, clamped to intelligible range. */
    fun setRateForWpm(wpm: Int) {
        val rate = (wpm / WORDS_PER_MINUTE_AT_UNIT_RATE).coerceIn(MIN_RATE, MAX_RATE)
        tts.setSpeechRate(rate)
    }

    /**
     * Speaks [text] and suspends until the engine finishes it. Returns false on engine
     * error or if the utterance could not be queued. Cancellation stops the utterance,
     * so a cancelled caller never leaves orphan audio playing.
     */
    suspend fun speakAndAwait(text: String, utteranceId: String): Boolean {
        if (!_isReady.value) return false
        return suspendCancellableCoroutine { cont ->
            pending[utteranceId] = { ok -> if (cont.isActive) cont.resume(ok) }
            cont.invokeOnCancellation {
                pending.remove(utteranceId)
                tts.stop()
            }
            val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (queued != TextToSpeech.SUCCESS) {
                pending.remove(utteranceId)
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    fun stop() {
        pending.clear()
        tts.stop()
        abandonFocus()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
        _isReady.value = false
    }
}
