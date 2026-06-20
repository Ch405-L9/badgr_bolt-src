package com.badgr.orbreader.ui.stats

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.badgr.orbreader.achievements.AchievementDef
import com.badgr.orbreader.achievements.AchievementDefinitions
import com.badgr.orbreader.achievements.BoltRank
import com.badgr.orbreader.billing.ProGate
import com.badgr.orbreader.data.local.ReadingSessionEntity
import com.badgr.orbreader.ui.theme.ReaderColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateToAccount: () -> Unit = {},
    vm: StatsViewModel = viewModel()
) {
    val snapshot             by vm.snapshot.collectAsState()
    val sessions             by vm.sessions.collectAsState()
    val unlockedAchievements by vm.unlockedAchievements.collectAsState()
    val isPro                by ProGate.isProFlow.collectAsState()

    val unlockedIds = remember(unlockedAchievements) {
        unlockedAchievements.map { it.id }.toSet()
    }

    // Track which IDs were newly unlocked in the last session for pulse anim
    val recentlyUnlocked = remember(unlockedAchievements) {
        val cutoff = System.currentTimeMillis() - 10_000L  // last 10 seconds
        unlockedAchievements.filter { it.unlockedAt >= cutoff }.map { it.id }.toSet()
    }

    val achievementRows = remember { AchievementDefinitions.ALL.chunked(4) }

    // Bottom sheet state for achievement detail
    var selectedDef   by remember { mutableStateOf<AchievementDef?>(null) }
    var showDetail    by remember { mutableStateOf(false) }

    if (showDetail && selectedDef != null) {
        AchievementDetailSheet(
            def        = selectedDef!!,
            isUnlocked = selectedDef!!.id in unlockedIds,
            onDismiss  = { showDetail = false }
        )
    }

    Scaffold(
        containerColor = ReaderColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Reading Stats", color = ReaderColors.textWarm, fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReaderColors.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Pro gate — upgrade CTA for free users ──────────────────
            if (!isPro) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color    = ReaderColors.orpFocal.copy(alpha = 0.08f),
                        shape    = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        border   = androidx.compose.foundation.BorderStroke(
                            1.dp, ReaderColors.orpFocal.copy(alpha = 0.30f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Unlock Full Analytics",
                                color      = ReaderColors.textWarm,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 16.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "BADGR Bolt Pro unlocks your complete reading history, " +
                                "WPM charts, Bolt Rank, achievements, and cloud sync across devices.",
                                color    = ReaderColors.textDimmed,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick        = onNavigateToAccount,
                                colors         = ButtonDefaults.buttonColors(
                                    containerColor = ReaderColors.orpFocal,
                                    contentColor   = ReaderColors.background
                                )
                            ) {
                                Text("Upgrade to Pro", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── BOLT RANK ─────────────────────────────────────────────────
            if (isPro) {
                item {
                    Spacer(Modifier.height(4.dp))
                    BoltRankCard(rank = snapshot.boltRank)
                }
            }

            // ── Achievement header (Pro only) ─────────────────────────────
            if (isPro) item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Achievements",
                        color      = ReaderColors.textWarm,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp
                    )
                    Text(
                        "${unlockedIds.size} / ${AchievementDefinitions.ALL.size}",
                        color      = ReaderColors.orpFocal,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp
                    )
                }
            }

            if (isPro) items(achievementRows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { def ->
                        AchievementChip(
                            def             = def,
                            isUnlocked      = def.id in unlockedIds,
                            isPulsing       = def.id in recentlyUnlocked,
                            modifier        = Modifier.weight(1f),
                            onClick         = {
                                selectedDef = def
                                showDetail  = true
                            }
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // ── Performance stats ─────────────────────────────────────────
            if (snapshot.totalSessions > 0) {
                item {
                    Text(
                        "Performance",
                        color      = ReaderColors.textWarm,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        modifier   = Modifier.padding(top = 4.dp)
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Best WPM",   "${snapshot.bestWpm}",    "wpm", Modifier.weight(1f))
                        StatCard("Avg WPM",    "${snapshot.averageWpm}", "wpm", Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Sessions",   "${snapshot.totalSessions}",    "",    Modifier.weight(1f))
                        StatCard("Total Time", "${snapshot.totalReadingMins}", "min", Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Streak",     "${snapshot.currentStreakDays}", "days",  Modifier.weight(1f))
                        StatCard("Total Words", formatLargeNumber(snapshot.totalWordsRead), "words", Modifier.weight(1f))
                    }
                }
                item {
                    Text(
                        "Recent Sessions",
                        color      = ReaderColors.textWarm,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        modifier   = Modifier.padding(top = 4.dp)
                    )
                }
                items(sessions.take(20)) { session -> SessionRow(session) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOLT RANK CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BoltRankCard(rank: BoltRank) {
    val rankColor = Color(rank.colorHex)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = rankColor.copy(alpha = 0.10f),
        shape    = RoundedCornerShape(14.dp),
        border   = BorderStroke(1.dp, rankColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "BOLT RANK",
                    fontFamily    = FontFamily.Monospace,
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = rankColor,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(rank.label,    fontSize = 30.sp, fontWeight = FontWeight.Black, color = ReaderColors.textWarm)
                Text(rank.subtitle, fontSize = 12.sp, color = ReaderColors.textDimmed)
            }
            Text(rank.emoji, fontSize = 44.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ACHIEVEMENT CHIP — with pulse animation for newly unlocked
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AchievementChip(
    def        : AchievementDef,
    isUnlocked : Boolean,
    isPulsing  : Boolean,
    modifier   : Modifier,
    onClick    : () -> Unit
) {
    val scale by if (isPulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_${def.id}")
        infiniteTransition.animateFloat(
            initialValue   = 1f,
            targetValue    = 1.08f,
            animationSpec  = infiniteRepeatable(
                animation  = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale_${def.id}"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .clickable(onClick = onClick),
        color    = if (isUnlocked) ReaderColors.orpFocal.copy(alpha = 0.10f)
                   else ReaderColors.background,
        shape    = RoundedCornerShape(10.dp),
        border   = BorderStroke(
            width = if (isPulsing) 1.5.dp else 1.dp,
            color = when {
                isPulsing  -> ReaderColors.orpFocal
                isUnlocked -> ReaderColors.orpFocal.copy(alpha = 0.45f)
                else       -> ReaderColors.guideLine
            }
        )
    ) {
        Column(
            modifier            = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                def.emoji,
                fontSize = 22.sp,
                modifier = Modifier.alpha(if (isUnlocked) 1f else 0.25f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                def.title,
                fontSize   = 9.sp,
                color      = if (isUnlocked) ReaderColors.textWarm else ReaderColors.textDimmed,
                textAlign  = TextAlign.Center,
                fontWeight = if (isUnlocked) FontWeight.SemiBold else FontWeight.Normal,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.alpha(if (isUnlocked) 1f else 0.45f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ACHIEVEMENT DETAIL BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementDetailSheet(
    def        : AchievementDef,
    isUnlocked : Boolean,
    onDismiss  : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = ReaderColors.background
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                def.emoji,
                fontSize = 56.sp,
                modifier = Modifier.alpha(if (isUnlocked) 1f else 0.3f)
            )
            Spacer(Modifier.height(8.dp))

            // Unlocked / Locked badge
            Surface(
                color = if (isUnlocked) ReaderColors.orpFocal.copy(alpha = 0.15f)
                        else ReaderColors.guideLine.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text     = if (isUnlocked) "✓  UNLOCKED" else "LOCKED",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    fontSize      = 9.sp,
                    fontFamily    = FontFamily.Monospace,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = if (isUnlocked) ReaderColors.orpFocal else ReaderColors.textDimmed
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                def.title,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = ReaderColors.textWarm,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                def.description,
                fontSize  = 14.sp,
                color     = ReaderColors.textDimmed,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Category tag
            Surface(
                color = ReaderColors.orpFocal.copy(alpha = 0.08f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, ReaderColors.orpFocal.copy(alpha = 0.2f))
            ) {
                Text(
                    def.category,
                    modifier      = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    fontSize      = 11.sp,
                    fontFamily    = FontFamily.Monospace,
                    color         = ReaderColors.orpFocal,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EXISTING COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color    = ReaderColors.orpFocal.copy(alpha = 0.08f),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, color = ReaderColors.textDimmed, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = ReaderColors.textWarm, fontSize = 24.sp, fontWeight = FontWeight.Black)
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(unit, color = ReaderColors.orpFocal, fontSize = 12.sp,
                         modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: ReadingSessionEntity) {
    val dateStr = remember(session.timestamp) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(session.timestamp))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = ReaderColors.orpFocal.copy(alpha = 0.04f),
        shape    = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(session.bookTitle, color = ReaderColors.textWarm, fontSize = 13.sp,
                     fontWeight = FontWeight.Medium, maxLines = 1)
                Text(dateStr, color = ReaderColors.textDimmed, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${session.avgWpm} WPM", color = ReaderColors.orpFocal,
                     fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${session.wordsRead} words", color = ReaderColors.textDimmed, fontSize = 11.sp)
            }
        }
    }
}

private fun formatLargeNumber(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fK".format(n / 1_000.0)
    else           -> n.toString()
}
