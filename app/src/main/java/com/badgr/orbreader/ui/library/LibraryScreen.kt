package com.badgr.orbreader.ui.library

import android.net.Uri
import android.content.ContentResolver
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.badgr.orbreader.billing.ProGate
import com.badgr.orbreader.ui.theme.ReaderColors

private data class FormatOption(
    val label   : String,
    val mime    : String,
    val emoji   : String,
    val subtitle: String,
    val enabled : Boolean = true
)

private val FORMAT_OPTIONS = listOf(
    FormatOption("TXT",   "text/plain",                           "📄", "Plain text files"),
    FormatOption("PDF",   "application/pdf",                     "📕", "Documents and articles"),
    FormatOption("EPUB",  "application/epub+zip",                "📗", "Ebooks"),
    FormatOption("DOCX",  "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "📘", "Word documents"),
    FormatOption("IMAGE", "image/*",                             "🖼",  "OCR — Coming soon", enabled = false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (bookId: String) -> Unit,
    viewModel : LibraryViewModel = viewModel()
) {
    val books        by viewModel.books.collectAsState()
    val uiState      by viewModel.uiState.collectAsState()
    val isPro        by ProGate.isProFlow.collectAsState()
    val summaryState   by viewModel.summaryState.collectAsState()
    val quizState      by viewModel.quizState.collectAsState()
    val reviewState    by viewModel.reviewState.collectAsState()
    val dueCountByBook by viewModel.dueCountByBook.collectAsState()

    var showFormatSheet   by remember { mutableStateOf(false) }
    var summaryBookId     by remember { mutableStateOf<String?>(null) }
    var summaryBookTitle  by remember { mutableStateOf("") }
    var quizBookId        by remember { mutableStateOf<String?>(null) }
    var quizBookTitle     by remember { mutableStateOf("") }
    var reviewBookId      by remember { mutableStateOf<String?>(null) }
    var reviewBookTitle   by remember { mutableStateOf("") }

    // One launcher per format
    val txtPicker  = rememberFilePicker("text/plain")           { u, n -> viewModel.importTxt(u, n) }
    val pdfPicker  = rememberFilePicker("application/pdf")      { u, n -> viewModel.importPdf(u, n) }
    val epubPicker = rememberFilePicker("application/epub+zip") { u, n -> viewModel.importEpub(u, n) }
    val docxPicker = rememberFilePicker(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ) { u, n -> viewModel.importDocx(u, n) }
    val imagePicker = rememberFilePicker("image/*")             { u, n -> viewModel.importImage(u, n) }

    val launcherMap = mapOf(
        "TXT"   to txtPicker,
        "PDF"   to pdfPicker,
        "EPUB"  to epubPicker,
        "DOCX"  to docxPicker,
        "IMAGE" to imagePicker
    )

    if (uiState is LibraryUiState.Error) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title            = { Text("Import failed") },
            text             = { Text((uiState as LibraryUiState.Error).message) },
            confirmButton    = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }

    if (uiState is LibraryUiState.BookLimitReached) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            containerColor   = MaterialTheme.colorScheme.surface,
            title = { Text("Library limit reached", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text  = {
                Text(
                    "Free accounts can store up to ${com.badgr.orbreader.billing.ProGate.FREE_BOOK_LIMIT} books. " +
                    "Upgrade to BADGR Bolt Pro for unlimited imports, cloud sync, and full reading analytics."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text("Upgrade", color = com.badgr.orbreader.ui.theme.ReaderColors.orpFocal,
                         fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearError) { Text("Not now") }
            }
        )
    }

    // ── Summary bottom sheet ──────────────────────────────────────────────
    if (summaryBookId != null) {
        ModalBottomSheet(
            onDismissRequest = { summaryBookId = null; viewModel.clearSummary() },
            containerColor   = ReaderColors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp)
            ) {
                Text(
                    summaryBookTitle,
                    color      = ReaderColors.textWarm,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    maxLines   = 2
                )
                Text(
                    "AI Summary",
                    color    = ReaderColors.textDimmed,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))
                when (val s = summaryState) {
                    is SummaryState.Idle -> {
                        Button(
                            onClick  = { viewModel.fetchSummary(summaryBookId!!) },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) { Text("Generate Summary") }
                    }
                    is SummaryState.Loading -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is SummaryState.Ready -> {
                        Text(
                            s.text,
                            color    = ReaderColors.textWarm,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick  = {
                                summaryBookId?.let { onOpenBook(it) }
                                summaryBookId = null
                                viewModel.clearSummary()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) { Text("Open Book") }
                    }
                    is SummaryState.Error -> {
                        Text(
                            s.message,
                            color    = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick  = { viewModel.fetchSummary(summaryBookId!!) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Retry") }
                    }
                }
            }
        }
    }

    // ── Quiz bottom sheet ─────────────────────────────────────────────────
    if (quizBookId != null) {
        ModalBottomSheet(
            onDismissRequest = { quizBookId = null; viewModel.clearQuiz() },
            containerColor   = ReaderColors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp)
            ) {
                Text(
                    quizBookTitle,
                    color      = ReaderColors.textWarm,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    maxLines   = 2
                )
                Text(
                    "Comprehension Quiz",
                    color    = ReaderColors.textDimmed,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))

                when (val qs = quizState) {
                    is QuizState.Idle -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is QuizState.Loading -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is QuizState.Active -> {
                        val q       = qs.questions[qs.currentIndex]
                        val correct = q.answerIndex
                        Text(
                            "Question ${qs.currentIndex + 1} of ${qs.questions.size}",
                            color    = ReaderColors.textDimmed,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            q.question,
                            color      = ReaderColors.textWarm,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        q.options.forEachIndexed { idx, option ->
                            val containerColor = when {
                                qs.picked == null                        -> MaterialTheme.colorScheme.surfaceVariant
                                idx == correct                           -> MaterialTheme.colorScheme.primaryContainer
                                idx == qs.picked && qs.picked != correct -> MaterialTheme.colorScheme.errorContainer
                                else                                     -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Surface(
                                modifier      = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape         = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                color         = containerColor,
                                onClick       = { if (qs.picked == null) viewModel.answerQuestion(idx) }
                            ) {
                                Text(
                                    option,
                                    modifier = Modifier.padding(12.dp),
                                    color    = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        if (qs.picked != null) {
                            Spacer(Modifier.height(16.dp))
                            val correct2 = qs.picked == correct
                            Text(
                                if (correct2) "Correct!" else "Incorrect — see highlighted answer",
                                color      = if (correct2) MaterialTheme.colorScheme.primary
                                             else          MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 14.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick  = { viewModel.nextQuestion() },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(if (qs.currentIndex + 1 < qs.questions.size) "Next Question" else "See Results")
                            }
                        }
                    }
                    is QuizState.Complete -> {
                        val pct = (qs.score * 100) / qs.total.coerceAtLeast(1)
                        Text(
                            "Quiz Complete!",
                            color      = ReaderColors.textWarm,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 20.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${qs.score} / ${qs.total} correct  ($pct%)",
                            color    = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                pct == 100 -> "Perfect score! Outstanding comprehension."
                                pct >= 66  -> "Good job — solid retention."
                                pct >= 33  -> "Keep reading — comprehension will improve."
                                else       -> "Try re-reading this section before moving on."
                            },
                            color    = ReaderColors.textDimmed,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick  = {
                                quizBookId?.let { onOpenBook(it) }
                                quizBookId = null
                                viewModel.clearQuiz()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Open Book") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick  = { viewModel.fetchQuiz(quizBookId!!) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Retry Quiz") }
                    }
                    is QuizState.Error -> {
                        Text(qs.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick  = { viewModel.fetchQuiz(quizBookId!!) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Retry") }
                    }
                }
            }
        }
    }

    // ── Review bottom sheet ───────────────────────────────────────────────
    if (reviewBookId != null) {
        ModalBottomSheet(
            onDismissRequest = { reviewBookId = null; viewModel.clearReview() },
            containerColor   = ReaderColors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp)
            ) {
                Text(
                    reviewBookTitle,
                    color      = ReaderColors.textWarm,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    maxLines   = 2
                )
                Text(
                    "Spaced Repetition Review",
                    color    = ReaderColors.textDimmed,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))

                when (val rs = reviewState) {
                    is ReviewState.Idle -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is ReviewState.NoDue -> {
                        Text(
                            "No cards due for review today.",
                            color    = ReaderColors.textWarm,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Complete a quiz to add cards to your deck.",
                            color    = ReaderColors.textDimmed,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick  = { reviewBookId = null; viewModel.clearReview() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Done") }
                    }
                    is ReviewState.Active -> {
                        val card    = rs.card
                        val correct = card.answerIndex
                        Text(
                            "Card ${rs.cardIndex + 1} of ${rs.totalCards}",
                            color    = ReaderColors.textDimmed,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            card.question,
                            color      = ReaderColors.textWarm,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        card.options.forEachIndexed { idx, option ->
                            val containerColor = when {
                                rs.picked == null                          -> MaterialTheme.colorScheme.surfaceVariant
                                idx == correct                             -> MaterialTheme.colorScheme.primaryContainer
                                idx == rs.picked && rs.picked != correct  -> MaterialTheme.colorScheme.errorContainer
                                else                                       -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Surface(
                                modifier  = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape     = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                color     = containerColor,
                                onClick   = { if (rs.picked == null) viewModel.answerReview(idx) }
                            ) {
                                Text(
                                    option,
                                    modifier   = Modifier.padding(12.dp),
                                    color      = MaterialTheme.colorScheme.onSurface,
                                    fontSize   = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        if (rs.picked != null) {
                            Spacer(Modifier.height(16.dp))
                            val wasCorrect = rs.picked == correct
                            Text(
                                if (wasCorrect) "Correct!" else "Incorrect — see highlighted answer",
                                color      = if (wasCorrect) MaterialTheme.colorScheme.primary
                                             else            MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 14.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick  = { viewModel.nextReview() },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(if (rs.cardIndex + 1 < rs.totalCards) "Next Card" else "See Results")
                            }
                        }
                    }
                    is ReviewState.Complete -> {
                        val pct = (rs.correct * 100) / rs.reviewed.coerceAtLeast(1)
                        Text(
                            "Session Complete!",
                            color      = ReaderColors.textWarm,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 20.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${rs.correct} / ${rs.reviewed} correct  ($pct%)",
                            color      = MaterialTheme.colorScheme.primary,
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                pct == 100 -> "Perfect! All cards pushed further out."
                                pct >= 66  -> "Good session — most cards scheduled for later."
                                pct >= 33  -> "Some cards reset to tomorrow. Keep reviewing."
                                else       -> "Tough session. Cards reset — review again soon."
                            },
                            color    = ReaderColors.textDimmed,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick  = { reviewBookId = null; viewModel.clearReview() },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Done") }
                    }
                }
            }
        }
    }

    // ── Format picker bottom sheet ────────────────────────────────────────
    if (showFormatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFormatSheet = false },
            containerColor   = ReaderColors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
            ) {
                Text(
                    "Import a Book",
                    color      = ReaderColors.textWarm,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
                Text(
                    "Choose a file format to import",
                    color    = ReaderColors.textDimmed,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))

                FORMAT_OPTIONS.forEach { fmt ->
                    Surface(
                        onClick  = {
                            if (fmt.enabled) {
                                showFormatSheet = false
                                launcherMap[fmt.label]?.launch(fmt.mime)
                            }
                        },
                        enabled  = fmt.enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color    = ReaderColors.orpFocal.copy(alpha = 0.06f),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(fmt.emoji, fontSize = 24.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fmt.label, color = ReaderColors.textWarm, fontWeight = FontWeight.SemiBold)
                                Text(fmt.subtitle, color = ReaderColors.textDimmed, fontSize = 12.sp)
                            }
                            if (!fmt.enabled) {
                                Surface(
                                    color = ReaderColors.textDimmed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "SOON",
                                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color      = ReaderColors.textDimmed,
                                        fontSize   = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = ReaderColors.background,
        topBar = {
            TopAppBar(
                title  = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Library", color = ReaderColors.textWarm, fontWeight = FontWeight.Bold)
                        if (!isPro) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = ReaderColors.orpFocal.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "LITE",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = ReaderColors.orpFocal,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReaderColors.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showFormatSheet = true },
                containerColor = ReaderColors.orpFocal,
                contentColor   = ReaderColors.background
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import book")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState is LibraryUiState.Converting) {
                val name = (uiState as LibraryUiState.Converting).fileName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier   = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color      = ReaderColors.orpFocal
                    )
                    Text(
                        text  = "Converting \"$name\"\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReaderColors.textWarm
                    )
                }
                HorizontalDivider(color = ReaderColors.guideLine)
            }

            if (books.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📚", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No books yet",
                            color      = ReaderColors.textWarm,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap + to import TXT, PDF, EPUB, or DOCX",
                            color    = ReaderColors.textDimmed,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(books, key = { it.id }) { book ->
                        BookRow(
                            book      = book,
                            onClick   = { onOpenBook(book.id) },
                            onDelete  = { viewModel.deleteBook(book) },
                            onSummary = {
                                summaryBookId    = book.id
                                summaryBookTitle = book.title
                                viewModel.clearSummary()
                                viewModel.fetchSummary(book.id)
                            },
                            onQuiz    = {
                                quizBookId    = book.id
                                quizBookTitle = book.title
                                viewModel.fetchQuiz(book.id)
                            },
                            dueCount  = dueCountByBook[book.id] ?: 0,
                            onReview  = {
                                reviewBookId    = book.id
                                reviewBookTitle = book.title
                                viewModel.startReview(book.id)
                            }
                        )
                        HorizontalDivider(color = ReaderColors.guideLine)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberFilePicker(
    mimeType: String,
    onPicked: (uri: Uri, fileName: String) -> Unit
): ManagedLauncher {
    val context  = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = resolveFileName(context.contentResolver, uri) ?: "Unknown"
        onPicked(uri, name)
    }
    return remember(launcher) { ManagedLauncher(launcher) }
}

private class ManagedLauncher(private val launcher: ActivityResultLauncher<String>) {
    fun launch(mime: String) = launcher.launch(mime)
}

private fun resolveFileName(resolver: ContentResolver, uri: Uri): String? {
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return cursor.getString(idx)
        }
    }
    return uri.lastPathSegment
}
