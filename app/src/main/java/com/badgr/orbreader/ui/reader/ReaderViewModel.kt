package com.badgr.orbreader.ui.reader

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.badgr.orbreader.audio.TextToSpeechManager
import com.badgr.orbreader.audio.cwalts.CwaltsNarrationController
import com.badgr.orbreader.audio.cwalts.CwaltsNarrationState
import com.badgr.orbreader.audio.cwalts.CwaltsNarrationStatus
import com.badgr.orbreader.data.local.BookDatabase
import com.badgr.orbreader.data.preferences.TTS_DISPLAY_ORP
import com.badgr.orbreader.data.preferences.TTS_SPEED_MAX
import com.badgr.orbreader.data.preferences.TTS_SPEED_MIN
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
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
    private val cwalts = CwaltsNarrationController(application)
    private val _cwaltsStatus = MutableStateFlow(CwaltsNarrationStatus())
    val cwaltsStatus: StateFlow<CwaltsNarrationStatus> = _cwaltsStatus.asStateFlow()

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

    val ttsNarrationSpeed: StateFlow<Float> = prefsRepo.preferences
        .map { it.ttsNarrationSpeed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val ttsDisplayMode: StateFlow<Int> = prefsRepo.preferences
        .map { it.ttsDisplayMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TTS_DISPLAY_ORP)

    private val _ttsUnavailable = MutableStateFlow(false)
    val ttsUnavailable: StateFlow<Boolean> = _ttsUnavailable.asStateFlow()

    // Engine initialized but has no voice data for the device language.
    private val _ttsLanguageUnavailable = MutableStateFlow(false)
    val ttsLanguageUnavailable: StateFlow<Boolean> = _ttsLanguageUnavailable.asStateFlow()

    // (voiceId, displayName) for the voice picker; populated when TTS is enabled.
    private val _ttsVoices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val ttsVoices: StateFlow<List<Pair<String, String>>> = _ttsVoices.asStateFlow()

    val ttsVoiceId: StateFlow<String?> = prefsRepo.preferences
        .map { it.ttsVoiceId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
            viewModelScope.launch {
                mgr.languageUnavailable.collect { _ttsLanguageUnavailable.value = it }
            }
            // Once ready, apply the saved voice and populate the picker list with
            // friendly sequential labels (engine voice ids are opaque codes).
            viewModelScope.launch {
                mgr.isReady.first { it }
                prefsRepo.preferences.first().ttsVoiceId?.let { mgr.setVoice(it) }
                _ttsVoices.value = mgr.availableVoices().mapIndexed { i, v ->
                    v.name to "Voice ${i + 1}"
                }
            }
            ttsManager = mgr
        }
    }

    fun adjustNarrationSpeed(delta: Float) {
        val next = (ttsNarrationSpeed.value + delta).coerceIn(TTS_SPEED_MIN, TTS_SPEED_MAX)
        viewModelScope.launch { prefsRepo.setTtsNarrationSpeed(next) }
        ttsManager?.setNarrationSpeed(next)
        // Restart the current sentence so the new speed takes effect immediately.
        if (_state.value.isPlaying && ttsEnabled.value) { stopPlayback(); startPlayback() }
    }

    fun cycleDisplayMode() {
        val next = if (ttsDisplayMode.value == TTS_DISPLAY_ORP) 1 else TTS_DISPLAY_ORP
        viewModelScope.launch { prefsRepo.setTtsDisplayMode(next) }
    }

    fun selectVoice(voiceId: String) {
        ttsManager?.setVoice(voiceId)
        viewModelScope.launch { prefsRepo.setTtsVoiceId(voiceId) }
        if (_state.value.isPlaying && ttsEnabled.value) { stopPlayback(); startPlayback() }
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
    private var currentBook: com.badgr.orbreader.data.model.Book? = null
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
            currentBook = entity?.toDomain()
            val words    = repo.loadWords(bookId)
            val savedWpm = prefsRepo.preferences.first().defaultWpm
            _state.update {
                it.copy(words = words, currentIndex = savedIndex, isLoading = false, wpm = savedWpm)
            }
            detectChapters(words)
            sentenceStarts = computeSentences(words)
        }
    }

    fun startCwaltsNarration() {
        val book = currentBook ?: return
        if (_cwaltsStatus.value.state == CwaltsNarrationState.Preparing ||
            _cwaltsStatus.value.state == CwaltsNarrationState.Processing) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _cwaltsStatus.value = CwaltsNarrationStatus(CwaltsNarrationState.Preparing)
                val text = _state.value.words.joinToString(" ")
                val files = cwalts.synthesize(book, text)
                _cwaltsStatus.value = CwaltsNarrationStatus(CwaltsNarrationState.Ready, segmentCount = files.size)
                withContext(Dispatchers.Main) {
                    cwalts.play(files.first()) {
                        _cwaltsStatus.value = CwaltsNarrationStatus(CwaltsNarrationState.Ready, segmentCount = files.size)
                    }
                    _cwaltsStatus.value = CwaltsNarrationStatus(CwaltsNarrationState.Playing, segmentCount = files.size)
                }
            } catch (error: Exception) {
                Log.w("CwaltsNarration", "Narration failed: ${error::class.simpleName}")
                _cwaltsStatus.value = CwaltsNarrationStatus(CwaltsNarrationState.Failed, message = "C.Walts unavailable")
            }
        }
    }

    fun stopCwaltsNarration() {
        cwalts.stop()
        _cwaltsStatus.value = CwaltsNarrationStatus(CwaltsNarrationState.Idle)
    }

    // Word indices where each spoken sentence begins. Also caps very long runs so no
    // single TTS utterance exceeds the engine's input limit.
    private var sentenceStarts: List<Int> = listOf(0)

    private fun computeSentences(words: List<String>): List<Int> {
        val starts = mutableListOf(0)
        var lastBoundary = 0
        for (i in words.indices) {
            val hardEnd = OrpEngine.hasSentenceEndingPunctuation(words[i])
            val tooLong = (i - lastBoundary) >= 40
            if ((hardEnd || tooLong) && i + 1 < words.size) {
                starts.add(i + 1)
                lastBoundary = i + 1
            }
        }
        return starts
    }

    /** Half-open [start, end) word range of the sentence containing [idx]. */
    private fun sentenceRangeAt(idx: Int): Pair<Int, Int> {
        val starts = sentenceStarts
        val si     = starts.indexOfLast { it <= idx }.coerceAtLeast(0)
        val start  = starts[si]
        val end    = starts.getOrElse(si + 1) { _state.value.words.size }
        return start to end
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
     * Natural read-aloud (v3.4.0). Each sentence is spoken as one utterance so the engine
     * applies real intonation and punctuation pauses. Narration speed is decoupled from the
     * RSVP dial — it comes from [ttsNarrationSpeed]. The display follows the voice word by
     * word via engine range callbacks; on engines that don't report ranges, a timer paces
     * the words within each sentence from the same narration speed (minor intra-sentence
     * drift only, resynced at every sentence boundary).
     */
    private fun startTtsPlayback() = viewModelScope.launch {
        val tts = ensureTts()
        val ready = withTimeoutOrNull(4_000L) { tts.isReady.first { it } } != null
        if (!ready) {
            _ttsUnavailable.value = true
            playJob = startTimerPlayback()
            return@launch
        }

        // Re-check language each time playback starts, so the missing-language banner
        // clears if the user installed a voice via system settings and came back.
        tts.applyLanguage()
        tts.setNarrationSpeed(ttsNarrationSpeed.value)
        tts.requestFocus()
        try {
            while (_state.value.isPlaying) {
                val words    = _state.value.words
                val startIdx = _state.value.currentIndex
                if (startIdx >= words.lastIndex) {
                    _state.update { it.copy(isPlaying = false) }
                    sessionActiveMs += System.currentTimeMillis() - lastPlayStartMs
                    break
                }

                // Speak from the current word to the end of its sentence (resume-safe).
                val (_, sentEnd) = sentenceRangeAt(startIdx)
                val speakStart   = startIdx
                val uttWords     = words.subList(speakStart, sentEnd)
                val text         = uttWords.joinToString(" ")

                // Char offset where each word begins in the joined utterance text.
                val offsets = IntArray(uttWords.size)
                var cursor  = 0
                for (i in uttWords.indices) { offsets[i] = cursor; cursor += uttWords[i].length + 1 }

                tts.setNarrationSpeed(ttsNarrationSpeed.value)

                val rangeFired = java.util.concurrent.atomic.AtomicBoolean(false)
                // Fallback pacer: on engines that never report word ranges, advance the
                // display on a timer. Once ranges have EVER been observed this session we
                // trust them and skip the fallback entirely — this avoids a first-utterance
                // race where engine warm-up delays the first range past the probe window and
                // both paths briefly drive the display. The probe window is generous so a
                // slow first callback on a range-capable engine doesn't trip it.
                val pacer = if (tts.rangesObserved.value) null else launch {
                    delay(900)
                    if (rangeFired.get() || tts.rangesObserved.value) return@launch
                    Log.i("ReaderViewModel", "TTS word-range fallback engaged (engine reports no ranges)")
                    val wpm     = (ttsNarrationSpeed.value * TextToSpeechManager.WORDS_PER_MINUTE_AT_UNIT_RATE)
                        .coerceAtLeast(60f)
                    val perWord = (60_000f / wpm).toLong()
                    var wi = 0
                    while (currentCoroutineContext().isActive && !rangeFired.get() && _state.value.isPlaying && speakStart + wi < sentEnd) {
                        val globalIdx = (speakStart + wi).coerceAtMost(words.lastIndex)
                        _state.update { it.copy(currentIndex = globalIdx) }
                        wi++
                        delay(perWord)
                    }
                }

                val uttId = "badgr_utt_${utteranceCounter++}"
                val spoke = tts.speakSentenceAwait(text, uttId) { charStart, _ ->
                    rangeFired.set(true)
                    val wi        = offsets.indexOfLast { it <= charStart }.coerceAtLeast(0)
                    val globalIdx = (speakStart + wi).coerceAtMost(words.lastIndex)
                    _state.update { it.copy(currentIndex = globalIdx) }
                }
                pacer?.cancel()

                if (!_state.value.isPlaying) break
                if (!spoke) {
                    // Engine error mid-book — degrade to visual-only rather than stalling.
                    _ttsUnavailable.value = true
                    playJob = startTimerPlayback()
                    return@launch
                }

                // Sentence finished — land on the next sentence's first word.
                _state.update { it.copy(currentIndex = sentEnd.coerceAtMost(it.words.lastIndex)) }
            }
        } finally {
            tts.stop()
        }
    }

    private fun stopPlayback() { playJob?.cancel() }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        stopPlayback()
        cwalts.stop()
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
