package com.badgr.orbreader.ui.library

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.badgr.orbreader.billing.ProGate
import com.badgr.orbreader.data.local.BookDatabase
import com.badgr.orbreader.data.local.SrsCardEntity
import com.badgr.orbreader.data.local.SrsEngine
import com.badgr.orbreader.data.model.Book
import com.badgr.orbreader.data.model.FileType
import com.badgr.orbreader.data.repository.BookRepository
import com.badgr.orbreader.data.repository.ImportResult
import com.badgr.orbreader.sync.CloudSyncManager
import com.badgr.orbreader.data.remote.QuizQuestion
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class LibraryUiState {
    object Idle : LibraryUiState()
    data class Converting(val fileName: String) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
    object BookLimitReached : LibraryUiState()
}

sealed class SummaryState {
    object Idle : SummaryState()
    object Loading : SummaryState()
    data class Ready(val text: String) : SummaryState()
    data class Error(val message: String) : SummaryState()
}

sealed class QuizState {
    object Idle : QuizState()
    object Loading : QuizState()
    data class Active(
        val questions:    List<QuizQuestion>,
        val currentIndex: Int,
        val score:        Int,
        val picked:       Int?  // index user tapped, null = not yet answered
    ) : QuizState()
    data class Complete(val score: Int, val total: Int) : QuizState()
    data class Error(val message: String) : QuizState()
}

data class ReviewCardUi(
    val question:    String,
    val options:     List<String>,
    val answerIndex: Int
)

sealed class ReviewState {
    object Idle   : ReviewState()
    object NoDue  : ReviewState()
    data class Active(
        val card:       ReviewCardUi,
        val cardIndex:  Int,
        val totalCards: Int,
        val correct:    Int,
        val picked:     Int?
    ) : ReviewState()
    data class Complete(val reviewed: Int, val correct: Int) : ReviewState()
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "LibraryViewModel"
    }

    private val db          = BookDatabase.getInstance(application)
    private val repo        = BookRepository(context = application, bookDao = db.bookDao())
    private val srsCardDao  = db.srsCardDao()
    private val gson        = Gson()

    val books: StateFlow<List<Book>> = repo.books
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCountByBook: StateFlow<Map<String, Int>> =
        srsCardDao.getDueCountsFlow(System.currentTimeMillis())
            .map { list -> list.associate { it.bookId to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Idle)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun importTxt(uri: Uri, fileName: String)   = launchImport(fileName) { repo.importTxt(uri, fileName) }
    fun importPdf(uri: Uri, fileName: String)   = launchImport(fileName) { repo.importRemote(uri, fileName, FileType.PDF,  "application/pdf") }
    fun importEpub(uri: Uri, fileName: String)  = launchImport(fileName) { repo.importRemote(uri, fileName, FileType.EPUB, "application/epub+zip") }
    fun importDocx(uri: Uri, fileName: String)  = launchImport(fileName) {
        repo.importRemote(uri, fileName, FileType.DOCX,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    }
    fun importImage(uri: Uri, fileName: String) = launchImport(fileName) { repo.importRemote(uri, fileName, FileType.IMAGE, "image/*") }

    fun deleteBook(book: Book) = viewModelScope.launch {
        repo.deleteBook(book)
        withContext(Dispatchers.IO) { srsCardDao.deleteCardsByBook(book.id) }
    }

    fun updateCategory(bookId: String, category: String) = viewModelScope.launch {
        repo.updateCategory(bookId, category)
    }

    private val _summaryState = MutableStateFlow<SummaryState>(SummaryState.Idle)
    val summaryState: StateFlow<SummaryState> = _summaryState.asStateFlow()

    fun fetchSummary(bookId: String) {
        viewModelScope.launch {
            _summaryState.value = SummaryState.Loading
            val result = repo.fetchAndCacheSummary(bookId)
            _summaryState.value = result.fold(
                onSuccess = { SummaryState.Ready(it) },
                onFailure = { SummaryState.Error(it.localizedMessage ?: "Failed to generate summary.") }
            )
        }
    }

    fun clearSummary() { _summaryState.value = SummaryState.Idle }

    // ── Quiz ─────────────────────────────────────────────────────────────
    private val _quizState      = MutableStateFlow<QuizState>(QuizState.Idle)
    val quizState: StateFlow<QuizState> = _quizState.asStateFlow()

    private var currentQuizBookId: String? = null

    fun fetchQuiz(bookId: String) {
        currentQuizBookId = bookId
        viewModelScope.launch {
            _quizState.value = QuizState.Loading
            val result = repo.fetchQuiz(bookId)
            _quizState.value = result.fold(
                onSuccess = { QuizState.Active(it, currentIndex = 0, score = 0, picked = null) },
                onFailure = { QuizState.Error(it.localizedMessage ?: "Failed to load quiz.") }
            )
        }
    }

    fun answerQuestion(pickedIndex: Int) {
        val s = _quizState.value as? QuizState.Active ?: return
        if (s.picked != null) return
        _quizState.value = s.copy(picked = pickedIndex)
    }

    fun nextQuestion() {
        val s = _quizState.value as? QuizState.Active ?: return
        val correct  = s.questions[s.currentIndex].answerIndex
        val newScore = s.score + if (s.picked == correct) 1 else 0
        val next     = s.currentIndex + 1
        if (next >= s.questions.size) {
            _quizState.value = QuizState.Complete(newScore, s.questions.size)
            currentQuizBookId?.let { bookId ->
                viewModelScope.launch { saveQuizAsCards(bookId, s.questions) }
            }
        } else {
            _quizState.value = QuizState.Active(s.questions, next, newScore, picked = null)
        }
    }

    fun clearQuiz() { _quizState.value = QuizState.Idle }

    private suspend fun saveQuizAsCards(bookId: String, questions: List<QuizQuestion>) =
        withContext(Dispatchers.IO) {
            questions.forEach { q ->
                srsCardDao.insertIfAbsent(
                    SrsCardEntity(
                        id           = "${bookId}_${q.question.hashCode()}",
                        bookId       = bookId,
                        question     = q.question,
                        optionsJson  = gson.toJson(q.options),
                        answerIndex  = q.answerIndex,
                        nextReviewAt = System.currentTimeMillis() + 86_400_000L
                    )
                )
            }
        }

    // ── Spaced Repetition Review ──────────────────────────────────────────
    private val _reviewState = MutableStateFlow<ReviewState>(ReviewState.Idle)
    val reviewState: StateFlow<ReviewState> = _reviewState.asStateFlow()

    private var reviewCards: List<SrsCardEntity> = emptyList()

    fun startReview(bookId: String) {
        viewModelScope.launch {
            val cards = withContext(Dispatchers.IO) {
                srsCardDao.getDueCards(bookId, System.currentTimeMillis())
            }
            if (cards.isEmpty()) {
                _reviewState.value = ReviewState.NoDue
            } else {
                reviewCards = cards
                _reviewState.value = ReviewState.Active(
                    card       = cards[0].toUi(),
                    cardIndex  = 0,
                    totalCards = cards.size,
                    correct    = 0,
                    picked     = null
                )
            }
        }
    }

    fun answerReview(pickedIndex: Int) {
        val s = _reviewState.value as? ReviewState.Active ?: return
        if (s.picked != null) return
        _reviewState.value = s.copy(picked = pickedIndex)
    }

    fun nextReview() {
        val s    = _reviewState.value as? ReviewState.Active ?: return
        val card = reviewCards.getOrNull(s.cardIndex) ?: return
        val isCorrect = s.picked == card.answerIndex
        val newCorrect = s.correct + if (isCorrect) 1 else 0

        viewModelScope.launch(Dispatchers.IO) {
            srsCardDao.updateCard(SrsEngine.schedule(card, isCorrect))
        }

        val next = s.cardIndex + 1
        _reviewState.value = if (next >= reviewCards.size) {
            ReviewState.Complete(reviewed = reviewCards.size, correct = newCorrect)
        } else {
            ReviewState.Active(
                card       = reviewCards[next].toUi(),
                cardIndex  = next,
                totalCards = reviewCards.size,
                correct    = newCorrect,
                picked     = null
            )
        }
    }

    fun clearReview() { _reviewState.value = ReviewState.Idle }

    private fun SrsCardEntity.toUi(): ReviewCardUi {
        val opts: List<String> = try {
            gson.fromJson(optionsJson, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
        } catch (e: Exception) { emptyList() }
        return ReviewCardUi(question = question, options = opts, answerIndex = answerIndex)
    }

    fun clearError() { _uiState.value = LibraryUiState.Idle }

    private fun launchImport(fileName: String, block: suspend () -> ImportResult) {
        viewModelScope.launch {
            // Enforce free book limit before starting import
            if (!ProGate.unlimitedLib) {
                val currentCount = db.bookDao().bookCount()
                if (currentCount >= ProGate.FREE_BOOK_LIMIT) {
                    _uiState.value = LibraryUiState.BookLimitReached
                    return@launch
                }
            }

            _uiState.value = LibraryUiState.Converting(fileName)
            val result = block()
            _uiState.value = when (result) {
                is ImportResult.Success -> LibraryUiState.Idle
                is ImportResult.Error   -> LibraryUiState.Error(result.message)
            }
            if (result is ImportResult.Success) {
                val uid = CloudSyncManager.currentUser?.uid
                if (uid != null) {
                    try {
                        val entity = db.bookDao().getBookById(result.book.id)
                        if (entity != null) CloudSyncManager.pushBook(uid, entity)
                    } catch (e: Exception) {
                        Log.w(TAG, "Firestore push failed: ${e.localizedMessage}")
                    }
                }
            }
        }
    }
}
