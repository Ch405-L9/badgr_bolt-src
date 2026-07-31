package com.badgr.orbreader.ui.reader

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.badgr.orbreader.audio.TextToSpeechManager
import com.badgr.orbreader.data.preferences.TTS_DISPLAY_FLOWING
import com.badgr.orbreader.data.preferences.TTS_SPEED_MAX
import com.badgr.orbreader.data.preferences.TTS_SPEED_MIN
import com.badgr.orbreader.data.preferences.TTS_SPEED_STEP
import com.badgr.orbreader.ui.components.AchievementToastHost
import com.badgr.orbreader.ui.components.ChunkWordDisplay
import com.badgr.orbreader.ui.theme.ColorBlindness
import com.badgr.orbreader.ui.theme.ReaderColors
import com.badgr.orbreader.ui.theme.ReaderFonts

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    bookId   : String,
    onBack   : () -> Unit,
    viewModel: ReaderViewModel = viewModel()
) {
    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    val state               by viewModel.state.collectAsState()
    val bookTitle           by viewModel.bookTitle.collectAsState()
    val showOrp             by viewModel.showOrpColor.collectAsState()
    val orpColorIndex       by viewModel.orpColorIndex.collectAsState()
    val fontSize            by viewModel.fontSize.collectAsState()
    val fontIndex           by viewModel.fontIndex.collectAsState()
    val chunkSize           by viewModel.chunkSize.collectAsState()
    val newAchievements     by viewModel.newAchievements.collectAsState()
    val colorBlindnessMode  by viewModel.colorBlindnessMode.collectAsState()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()
    val totalChapters       by viewModel.totalChapters.collectAsState()
    val ttsEnabled          by viewModel.ttsEnabled.collectAsState()
    val ttsUnavailable      by viewModel.ttsUnavailable.collectAsState()
    val ttsNarrationSpeed   by viewModel.ttsNarrationSpeed.collectAsState()
    val ttsDisplayMode      by viewModel.ttsDisplayMode.collectAsState()
    val ttsVoices           by viewModel.ttsVoices.collectAsState()
    val ttsVoiceId          by viewModel.ttsVoiceId.collectAsState()
    val ttsLanguageMissing  by viewModel.ttsLanguageUnavailable.collectAsState()

    val context = LocalContext.current

    val ttsActive = ttsEnabled && !ttsUnavailable
    var showVoicePicker by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val orpColorList      = ColorBlindness.getOrpColors(colorBlindnessMode)
    val currentOrpColor   = orpColorList.getOrElse(orpColorIndex) { orpColorList[0] }
    val currentFontFamily = ReaderFonts.fromIndex(fontIndex)

    val currentChunk = remember(state.currentIndex, chunkSize) {
        viewModel.getCurrentChunk()
    }

    // Slider tracks playback position but stays frozen during user drag (isDragging).
    // seekTo() fires only on drag release to avoid continuous state thrashing.
    var sliderPos by remember { mutableStateOf(state.progress) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.progress) {
        if (!isDragging) sliderPos = state.progress
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    BackHandler {
        viewModel.saveProgress()
        onBack()
    }

    Scaffold(
        containerColor = ReaderColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = bookTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color    = ReaderColors.textWarm,
                        style    = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setTtsEnabled(!ttsEnabled) },
                        enabled = !ttsUnavailable
                    ) {
                        Icon(
                            if (ttsEnabled && !ttsUnavailable) Icons.AutoMirrored.Filled.VolumeUp
                            else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (ttsEnabled) "Turn off read aloud" else "Turn on read aloud",
                            tint = when {
                                ttsUnavailable                -> ReaderColors.guideLine
                                ttsEnabled                    -> currentOrpColor
                                else                          -> ReaderColors.textWarm
                            }
                        )
                    }
                    IconButton(onClick = { viewModel.saveProgress(); onBack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ReaderColors.textWarm)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReaderColors.background)
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ReaderColors.orpFocal)
            }
            return@Scaffold
        }

        val wordContent: @Composable () -> Unit = {
            if (ttsActive && ttsDisplayMode == TTS_DISPLAY_FLOWING) {
                FlowingTextDisplay(
                    words          = state.words,
                    currentIndex   = state.currentIndex,
                    highlightColor = currentOrpColor,
                    dimColor       = ReaderColors.textDimmed,
                    fontFamily     = currentFontFamily
                )
            } else {
                Canvas(modifier = Modifier.size(width = 40.dp, height = 120.dp)) {
                    val sw = 2.dp.toPx()
                    val ll = 15.dp.toPx()
                    drawLine(currentOrpColor, Offset(size.width / 2, 0f), Offset(size.width / 2, ll), sw)
                    drawLine(currentOrpColor, Offset(size.width / 2, size.height - ll), Offset(size.width / 2, size.height), sw)
                }
                ChunkWordDisplay(
                    words        = currentChunk,
                    fontSize     = fontSize.sp,
                    showOrpColor = showOrp,
                    orpColor     = currentOrpColor,
                    fontFamily   = currentFontFamily
                )
            }
        }

        val controlsContent: @Composable ColumnScope.() -> Unit = {
            // Read-aloud enabled but the active engine has no voice data for this language.
            if (ttsEnabled && !ttsUnavailable && ttsLanguageMissing) {
                Surface(
                    color    = ReaderColors.orpFocal.copy(alpha = 0.12f),
                    shape    = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Read-aloud needs a voice for your language",
                            color = ReaderColors.textWarm,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Your text-to-speech engine has no voice installed for this language. Install one or switch engines in system settings.",
                            color = ReaderColors.textDimmed,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { TextToSpeechManager.openSystemTtsSettings(context) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Open TTS settings", color = currentOrpColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Progress info row: word count + chapter indicator
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "${state.currentIndex + 1} / ${state.words.size}",
                    color = ReaderColors.textDimmed,
                    style = MaterialTheme.typography.bodySmall
                )
                if (totalChapters > 1) {
                    Text(
                        "Ch ${currentChapterIndex + 1} / $totalChapters",
                        color      = currentOrpColor.copy(alpha = 0.8f),
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value    = sliderPos,
                onValueChange = { sliderPos = it; isDragging = true },
                onValueChangeFinished = {
                    isDragging = false
                    viewModel.seekTo(
                        (sliderPos * (state.words.size - 1).coerceAtLeast(1)).toInt()
                    )
                },
                colors   = SliderDefaults.colors(
                    thumbColor         = currentOrpColor,
                    activeTrackColor   = currentOrpColor,
                    inactiveTrackColor = ReaderColors.guideLine
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
            Spacer(Modifier.height(20.dp))

            // ── WPM row ───────────────────────────────────────
            // Tap skip buttons = ±10 words  |  Long-press = ±1 chapter
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .combinedClickable(
                            onClick     = { viewModel.skipWords(-10) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.skipChapter(-1)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Back 10 words (hold: prev chapter)",
                        tint = ReaderColors.textWarm
                    )
                }
                if (ttsActive) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.adjustNarrationSpeed(-TTS_SPEED_STEP)
                        },
                        enabled = ttsNarrationSpeed > TTS_SPEED_MIN
                    ) {
                        Text("−", color = if (ttsNarrationSpeed > TTS_SPEED_MIN) currentOrpColor else ReaderColors.guideLine,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.adjustWpm(-25)
                    }) {
                        Text("−25", color = ReaderColors.textDimmed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                FloatingActionButton(
                    onClick        = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.togglePlayPause()
                    },
                    containerColor = currentOrpColor,
                    contentColor   = ReaderColors.background
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play"
                    )
                }
                if (ttsActive) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.adjustNarrationSpeed(TTS_SPEED_STEP)
                        },
                        enabled = ttsNarrationSpeed < TTS_SPEED_MAX
                    ) {
                        Text("+", color = if (ttsNarrationSpeed < TTS_SPEED_MAX) currentOrpColor else ReaderColors.guideLine,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.adjustWpm(25)
                    }) {
                        Text("+25", color = ReaderColors.textDimmed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .combinedClickable(
                            onClick     = { viewModel.skipWords(10) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.skipChapter(1)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Forward 10 words (hold: next chapter)",
                        tint = ReaderColors.textWarm
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            if (ttsActive) {
                Text(
                    "Narration ${"%.2f".format(ttsNarrationSpeed)}×",
                    color = currentOrpColor,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "≈ ${(ttsNarrationSpeed * 175).toInt()} wpm · voice speed (independent of RSVP)",
                    color    = ReaderColors.textDimmed,
                    fontSize = 10.sp
                )
            } else {
                Text(
                    "${state.wpm} WPM",
                    color = currentOrpColor,
                    style = MaterialTheme.typography.labelMedium
                )
                val wordsLeft = (state.words.size - state.currentIndex).coerceAtLeast(0)
                val minsLeft  = wordsLeft / state.wpm.coerceAtLeast(1)
                val timeLeft  = when {
                    minsLeft < 1  -> "< 1 min"
                    minsLeft < 60 -> "$minsLeft min"
                    else          -> "${minsLeft / 60}h ${minsLeft % 60}m"
                }
                Text(
                    "~$timeLeft left",
                    color = ReaderColors.textDimmed,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = ReaderColors.guideLine)
            Spacer(Modifier.height(12.dp))

            if (ttsActive) {
                // ── TTS: display-mode toggle + voice picker ──────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Display",
                        color    = ReaderColors.textDimmed,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    SegmentedToggle(
                        left        = "Focus word",
                        right       = "Flowing",
                        rightActive = ttsDisplayMode == TTS_DISPLAY_FLOWING,
                        color       = currentOrpColor,
                        onToggle    = { viewModel.cycleDisplayMode() }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Voice",
                        color    = ReaderColors.textDimmed,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { showVoicePicker = true },
                        enabled = ttsVoices.isNotEmpty()
                    ) {
                        Text(
                            if (ttsVoices.isEmpty()) "default" else "Choose voice",
                            color = if (ttsVoices.isEmpty()) ReaderColors.guideLine else currentOrpColor,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Engine & voices",
                        color    = ReaderColors.textDimmed,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { TextToSpeechManager.openSystemTtsSettings(context) }) {
                        Text("System TTS settings", color = currentOrpColor, fontSize = 13.sp)
                    }
                }
            } else {
                // ── Chunk size row ────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Words at a time",
                        color    = ReaderColors.textDimmed,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick  = { viewModel.adjustChunkSize(-1) },
                        enabled  = chunkSize > 1
                    ) {
                        Text(
                            "−",
                            color      = if (chunkSize > 1) currentOrpColor else ReaderColors.guideLine,
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color  = currentOrpColor.copy(alpha = 0.12f),
                        shape  = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "$chunkSize",
                            color      = currentOrpColor,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Black,
                            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick  = { viewModel.adjustChunkSize(1) },
                        enabled  = chunkSize < 4
                    ) {
                        Text(
                            "+",
                            color      = if (chunkSize < 4) currentOrpColor else ReaderColors.guideLine,
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ReaderColors.background)
                ) {
                    Box(
                        modifier         = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) { wordContent() }

                    Surface(
                        modifier       = Modifier
                            .width(340.dp)
                            .fillMaxHeight(),
                        color          = ReaderColors.background,
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier            = Modifier
                                .verticalScroll(scrollState)
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            content             = controlsContent
                        )
                    }
                }
            } else {
                Column(
                    modifier            = Modifier
                        .fillMaxSize()
                        .background(ReaderColors.background),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier         = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) { wordContent() }

                    Surface(
                        modifier       = Modifier.fillMaxWidth(),
                        color          = ReaderColors.background,
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier            = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            content             = controlsContent
                        )
                    }
                }
            }

            AchievementToastHost(
                newAchievementIds = newAchievements,
                onConsumed        = viewModel::consumeAchievements,
                modifier          = Modifier.align(Alignment.TopCenter)
            )
        }

        if (showVoicePicker) {
            VoicePickerDialog(
                voices     = ttsVoices,
                selectedId = ttsVoiceId,
                accent     = currentOrpColor,
                onSelect   = { viewModel.selectVoice(it); showVoicePicker = false },
                onDismiss  = { showVoicePicker = false }
            )
        }
    }
}

/** A2 read-along: flowing text with the spoken word highlighted; recenters as it advances. */
@Composable
private fun FlowingTextDisplay(
    words: List<String>,
    currentIndex: Int,
    highlightColor: Color,
    dimColor: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily
) {
    val windowStart = (currentIndex - 24).coerceAtLeast(0)
    val windowEnd   = (currentIndex + 60).coerceAtMost(words.size)
    val text = androidx.compose.ui.text.buildAnnotatedString {
        for (i in windowStart until windowEnd) {
            if (i == currentIndex) {
                withStyle(androidx.compose.ui.text.SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(words[i])
                }
            } else {
                withStyle(androidx.compose.ui.text.SpanStyle(color = dimColor)) { append(words[i]) }
            }
            append(" ")
        }
    }
    Text(
        text       = text,
        fontSize   = 20.sp,
        lineHeight = 32.sp,
        fontFamily = fontFamily,
        textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    )
}

@Composable
private fun SegmentedToggle(
    left: String,
    right: String,
    rightActive: Boolean,
    color: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
    ) {
        listOf(false, true).forEach { isRight ->
            val selected = isRight == rightActive
            Text(
                text       = if (isRight) right else left,
                color      = if (selected) ReaderColors.background else color,
                fontSize   = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier   = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) color else Color.Transparent)
                    .clickable { if (!selected) onToggle() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun VoicePickerDialog(
    voices: List<Pair<String, String>>,
    selectedId: String?,
    accent: Color,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Default to the first voice when none is explicitly chosen yet.
    val effectiveSelected = selectedId ?: voices.firstOrNull()?.first
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = ReaderColors.background,
        title = { Text("Choose a voice", color = ReaderColors.textWarm) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                voices.forEach { (id, label) ->
                    val isSel = id == effectiveSelected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = label,
                            color      = if (isSel) accent else ReaderColors.textWarm,
                            fontSize   = 15.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            modifier   = Modifier.weight(1f)
                        )
                        if (isSel) Text("●", color = accent, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = ReaderColors.guideLine)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = accent) }
        }
    )
}
