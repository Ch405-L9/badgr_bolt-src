package com.badgr.orbreader.ui.reader

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.badgr.orbreader.audio.TextToSpeechManager
import com.badgr.orbreader.data.local.BookDatabase
import com.badgr.orbreader.data.preferences.UserPreferencesRepository
import com.badgr.orbreader.data.repository.BookRepository
import com.badgr.orbreader.data.repository.ReadingSessionRepository
import com.badgr.orbreader.sync.CloudSyncManager
import com.badgr.orbreader.util.OrpEngine
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

data class ReaderUiState(
    val words        : List<String> = emptyList(),
    val currentIndex : Int          = 0,
    val wpm          : Int          = 150,
    val isPlaying    : Boolean      = false,
    val isLoading    : Boolean      = true
) {
    val currentWord: String get() = words.getOrElse(currentIndex) { "" }
    val progress: Float     get() = if (words.isEmpty()) 0f
                                    else currentIndex.toFloat() / words.size
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val db          = BookDatabase.getInstance(application)
    private val repo        = BookRepository(context = application, bookDao = db.bookDao())
    private val sessionRepo = ReadingSessionRepository(
        dao            = db.readingSessionDao(),
        achievementDao = db.achievementDao()
    )
    private val prefsRepo = UserPreferencesRepository(application)

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _bookTitle = MutableStateFlow("")
    val bookTitle: StateFlow<String> = _bookTitle.asStateFlow()

    val showOrpColor: StateFlow<Boolean> = prefsRepo.preferences
        .map { it.showOrpColor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val orpColorIndex: StateFlow<Int> = prefsRepo.preferences
        .map { it.orpColorIndex }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val fontIndex: StateFlow<Int> = prefsRepo.preferences
        .map { it.fontIndex }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val fontSize: StateFlow<Int> = prefsRepo.preferences
        .map { it.fontSize }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 36)

    val chunkSize: StateFlow<Int> = prefsRepo.preferences
        .map { it.chunkSize }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    val sentencePauseMultiplier: StateFlow<Float> = prefsRepo.preferences
        .map { it.sentencePauseMultiplier }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2.0f)

    val clausePauseMultiplier: StateFlow<Float> = prefsRepo.preferences
        .map { it.clausePauseMultiplier }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.5f)

    val colorBlindnessMode: StateFlow<Int> = prefsRepo.preferences
        .map { it.colorBlindnessMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val ttsEnabled: StateFlow<Boolean> = prefsRepo.preferences
        .map { it.ttsEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _ttsUnavailable = MutableStateFlow(false)
    val ttsUnavailable: StateFlow<Boolean> = _ttsUnavailable.asStateFlow()

    // Engine spins up only for users who actually turn TTS on.
    private var ttsManager: TextToSpeechManager? = null
    private var utteranceCounter = 0L

    private fun ensureTts(): TextToSpeechManager {
        ttsManager?.let { return it }
        return TextToSpeechManager(getApplication()).also { mgr ->
            mgr.onFocusLost = {
                if (_state.value.isPlaying) {
                    viewModelScope.launch { togglePlayPause() }
                }
            }
            viewModelScope.launch {
                mgr.initFailed.collect { failed -> if (failed) _ttsUnavailable.value = true }
            }
            ttsManager = mgr
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        if (enabled) ensureTts()
        viewModelScope.launch { prefsRepo.setTtsEnabled(enabled) }
        if (_state.value.isPlaying) {
            stopPlayback()
            // Restart once the pref flow emits so startPlayback sees the new mode.
            viewModelScope.launch {
                ttsEnabled.first { it == enabled }
                if (_state.value.isPlaying) startPlayback()
            }
        }
    }

    // Chapter navigation — word indices where each chapter/part/section begins
    private val _chapterStarts = MutableStateFlow<List<Int>>(listOf(0))

    val totalChapters: StateFlow<Int> = _chapterStarts
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    val currentChapterIndex: StateFlow<Int> = combine(_state, _chapterStarts) { s, chapters ->
        chapters.indexOfLast { it <= s.currentIndex }.coerceAtLeast(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _newAchievements = MutableStateFlow<List<String>>(emptyList())
    val newAchievements: StateFlow<List<String>> = _newAchievements.asStateFlow()

    private var currentBookId     : String  = ""
    private var sessionStartIndex : Int     = -1
    private var sessionHasStarted : Boolean = false
    private var sessionActiveMs   : Long    = 0L
    private var lastPlayStartMs   : Long    = 0L
    private var sessionRewindCount: Int     = 0

    private var playJob: Job? = null

    fun loadBook(bookId: String) {
        currentBookId = bookId
        viewModelScope.launch {
            val entity     = db.bookDao().getBookById(bookId)
            val savedIndex = entity?.currentWordIndex ?: 0
            _bookTitle.value = entity?.title ?: ""
            val words    = repo.loadWords(bookId)
            val savedWpm = prefsRepo.preferences.first().defaultWpm
            _state.update {
                it.copy(words = words, currentIndex = savedIndex, isLoading = false, wpm = savedWpm)
            }
            detectChapters(words)
        }
    }

    private fun detectChapters(words: List<String>) {
        val starts = mutableListOf(0)
        val markers = setOf("chapter", "part", "section", "book", "epilogue", "prologue", "afterword")
        var i = 0
        while (i < words.size) {
            val w = words[i].lowercase().trimEnd('.', ',', ':', ';')
            if (w in markers && i > 0) {
                if (starts.last() < i - 50) starts.add(i)
                i += 2
            } else {
                i++
            }
        }
        _chapterStarts.value = starts
    }

    fun getCurrentChunk(): List<String> {
        val s     = _state.value
        val chunk = chunkSize.value
        return (0 until chunk).map { offset ->
            s.words.getOrElse(s.currentIndex + offset) { "" }
        }
    }

    fun saveProgress() {
        if (currentBookId.isEmpty()) return
        val index = _state.value.currentIndex

        if (_state.value.isPlaying && sessionHasStarted) {
            sessionActiveMs += System.currentTimeMillis() - lastPlayStartMs
        }

        val wordsRead     = if (sessionStartIndex >= 0)
            (index - sessionStartIndex).coerceAtLeast(0) else 0
        val activeSeconds = (sessionActiveMs / 1000L).toInt()
        val effectiveWpm  = if (activeSeconds > 0)
            ((wordsRead * 60f) / activeSeconds).toInt() else 0

        viewModelScope.launch {
            db.bookDao().updateProgress(currentBookId, index)

            if (wordsRead >= 100 && activeSeconds >= 60) {
                try {
                    val bookCount     = db.bookDao().bookCount()
                    val newlyUnlocked = sessionRepo.recordSession(
                        bookId          = currentBookId,
                        bookTitle       = _bookTitle.value,
                        wordsRead       = wordsRead,
                        durationSeconds = activeSeconds,
                        wpm             = effectiveWpm,
                        rewindCount     = sessionRewindCount,
                        booksImported   = bookCount
                    )
                    if (newlyUnlocked.isNotEmpty()) {
                        _newAchievements.value = newlyUnlocked
                    }
                } catch (e: Exception) {
                    Log.w("ReaderViewModel", "Session record failed: ${e.localizedMessage}")
                }
            }

            sessionStartIndex  = -1
            sessionHasStarted  = false
            sessionActiveMs    = 0L
            lastPlayStartMs    = 0L
            sessionRewindCount = 0

            try {
                CloudSyncManager.pushProgress(currentBookId, index)
            } catch (e: Exception) {
                Log.w("ReaderViewModel", "Progress sync failed: ${e.localizedMessage}")
            }
        }
    }

    fun consumeAchievements() { _newAchievements.value = emptyList() }

    fun togglePlayPause() {
        val playing = !_state.value.isPlaying
        _state.update { it.copy(isPlaying = playing) }
        if (playing) {
            if (!sessionHasStarted) {
                sessionStartIndex = _state.value.currentIndex
                sessionHasStarted = true
            }
            lastPlayStartMs = System.currentTimeMillis()
            startPlayback()
        } else {
            sessionActiveMs += System.currentTimeMillis() - lastPlayStartMs
            stopPlayback()
        }
    }

    fun adjustWpm(delta: Int) {
        val newWpm = (_state.value.wpm + delta).coerceIn(60, 1200)
        _state.update { it.copy(wpm = newWpm) }
        viewModelScope.launch { prefsRepo.setDefaultWpm(newWpm) }
        if (_state.value.isPlaying) { stopPlayback(); startPlayback() }
    }

    fun adjustChunkSize(delta: Int) {
        val newSize = (chunkSize.value + delta).coerceIn(1, 4)
        viewModelScope.launch { prefsRepo.setChunkSize(newSize) }
    }

    fun skipSeconds(seconds: Int) {
        if (seconds < 0) sessionRewindCount++
        val wpm         = _state.value.wpm
        val chunk       = chunkSize.value
        val wordsToSkip = (wpm * abs(seconds) / 60f).roundToInt().coerceAtLeast(chunk)
        val delta       = if (seconds > 0) wordsToSkip else -wordsToSkip
        val newIndex    = (_state.value.currentIndex + delta)
            .coerceIn(0, (_state.value.words.size - 1).coerceAtLeast(0))
        _state.update { it.copy(currentIndex = newIndex) }
        if (_state.value.isPlaying) { stopPlayback(); startPlayback() }
    }

    fun skipWords(count: Int) {
        if (count < 0) sessionRewindCount++
        val newIndex = (_state.value.currentIndex + count)
            .coerceIn(0, (_state.value.words.size - 1).coerceAtLeast(0))
        _state.update { it.copy(currentIndex = newIndex) }
        if (_state.value.isPlaying) { stopPlayback(); startPlayback() }
    }

    fun skipChapter(direction: Int) {
        val chapters = _chapterStarts.value
        if (chapters.size <= 1) return
        val currentChapterIdx = chapters.indexOfLast { it <= _state.value.currentIndex }.coerceAtLeast(0)
        val targetIdx = (currentChapterIdx + direction).coerceIn(0, chapters.lastIndex)
        seekTo(chapters[targetIdx])
    }

    fun seekTo(index: Int) {
        _state.update { it.copy(currentIndex = index.coerceIn(0, (it.words.size - 1).coerceAtLeast(0))) }
    }

    private fun startPlayback() {
        playJob?.cancel()
        val useTts = ttsEnabled.value && !_ttsUnavailable.value
        playJob = if (useTts) startTtsPlayback() else startTimerPlayback()
    }

    private fun startTimerPlayback() = viewModelScope.launch {
        while (_state.value.isPlaying) {
            val s     = _state.value
            val chunk = chunkSize.value
            if (s.currentIndex >= s.words.lastIndex) {
                _state.update { it.copy(isPlaying = false) }
                sessionActiveMs += System.currentTimeMillis() - lastPlayStartMs
                break
            }

            val baseDelay = (60_000L * chunk) / s.wpm

            val lastWordInChunk = s.words.getOrNull(s.currentIndex + chunk - 1) ?: ""
            val pauseMultiplier = when {
                OrpEngine.hasSentenceEndingPunctuation(lastWordInChunk) -> sentencePauseMultiplier.value
                OrpEngine.hasClausePunctuation(lastWordInChunk)         -> clausePauseMultiplier.value
                else                                                    -> 1.0f
            }

            delay((baseDelay * pauseMultiplier).toLong())
            _state.update { it.copy(currentIndex = (it.currentIndex + chunk).coerceAtMost(it.words.lastIndex)) }
        }
    }

    /**
     * TTS mode uses speech as the playback clock: each chunk is one utterance and the
     * display advances only when the engine finishes speaking it, so audio and display
     * cannot drift. The engine tops out around rate 3.0 (~525 WPM) — above that the
     * dial still climbs but speech is clamped, and the UI shows a cap hint.
     */
    private fun startTtsPlayback() = viewModelScope.launch {
        val tts = ensureTts()
        // Engine init is async; give it a moment on first use, fall back if it never readies.
        val ready = withTimeoutOrNull(4_000L) { tts.isReady.first { it } } != null
        if (!ready) {
            _ttsUnavailable.value = true
            startTimerPlayback().also { playJob = it }
            return@launch
        }

        tts.requestFocus()
        try {
            while (_state.value.isPlaying) {
                val s     = _state.value
                val chunk = chunkSize.value
                if (s.currentIndex >= s.words.lastIndex) {
                    _state.update { it.copy(isPlaying = false) }
                    sessionActiveMs += System.currentTimeMillis() - lastPlayStartMs
                    break
                }

                tts.setRateForWpm(s.wpm)
                val text = (0 until chunk)
                    .mapNotNull { s.words.getOrNull(s.currentIndex + it) }
                    .joinToString(" ")

                val spoke = tts.speakAndAwait(text, "badgr_utt_${utteranceCounter++}")
                if (!_state.value.isPlaying) break
                if (!spoke) {
                    // Engine error mid-book — degrade to visual-only rather than stalling.
                    _ttsUnavailable.value = true
                    playJob = startTimerPlayback()
                    return@launch
                }

                // TTS already pauses briefly at punctuation; add only the *extra* pause
                // beyond 1x that the user configured, to avoid doubling up.
                val lastWordInChunk = s.words.getOrNull(s.currentIndex + chunk - 1) ?: ""
                val extraMultiplier = when {
                    OrpEngine.hasSentenceEndingPunctuation(lastWordInChunk) -> sentencePauseMultiplier.value - 1f
                    OrpEngine.hasClausePunctuation(lastWordInChunk)         -> clausePauseMultiplier.value - 1f
                    else                                                    -> 0f
                }
                if (extraMultiplier > 0f) {
                    val baseDelay = (60_000L * chunk) / s.wpm
                    delay((baseDelay * extraMultiplier).toLong())
                }

                _state.update { it.copy(currentIndex = (it.currentIndex + chunk).coerceAtMost(it.words.lastIndex)) }
            }
        } finally {
            tts.stop()
        }
    }

    private fun stopPlayback() { playJob?.cancel() }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        stopPlayback()
        ttsManager?.shutdown()
        // viewModelScope is cancelled before onCleared() runs, so coroutines launched there die
        // immediately. Use GlobalScope + NonCancellable to guarantee the word index reaches Room
        // when the ViewModel is cleared without a BackHandler call (swipe-from-recents, system nav).
        // Session recording and Firestore sync are intentionally omitted here — BackHandler handles
        // those while the scope is still alive. This is a position-save safety net only.
        val bookId = currentBookId
        if (bookId.isEmpty()) return
        val index = _state.value.currentIndex
        GlobalScope.launch(Dispatchers.IO + NonCancellable) {
            try { db.bookDao().updateProgress(bookId, index) } catch (_: Exception) {}
        }
    }
}
