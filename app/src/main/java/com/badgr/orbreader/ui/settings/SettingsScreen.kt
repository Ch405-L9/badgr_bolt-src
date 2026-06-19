package com.badgr.orbreader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.res.stringResource
import com.badgr.orbreader.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.badgr.orbreader.billing.ProGate
import com.badgr.orbreader.data.preferences.THEME_DARK
import com.badgr.orbreader.data.preferences.THEME_LIGHT
import com.badgr.orbreader.data.preferences.THEME_SYSTEM
import com.badgr.orbreader.ui.theme.ColorBlindness
import com.badgr.orbreader.ui.theme.ReaderColors
import com.badgr.orbreader.ui.theme.ReaderFonts
import java.util.Locale

private val ORP_COLORS = listOf(
    Color(0xFF00CED1),
    Color(0xFF4CAF50),
    Color(0xFFFFC107),
    Color(0xFFE040FB),
    Color(0xFFE53935),
)

private val THEME_OPTIONS = listOf("System", "Light", "Dark")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAccount: () -> Unit = {},
    vm: SettingsViewModel = viewModel()
) {
    val prefs by vm.prefs.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = ReaderColors.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = ReaderColors.textWarm, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReaderColors.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {

            // ── Default Reading Speed ─────────────────────────────────────
            item {
                SettingSection(title = "Default Reading Speed") {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick  = { vm.setWpm(prefs.defaultWpm - 25) },
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = ReaderColors.orpFocal),
                            modifier = Modifier.semantics { contentDescription = "Decrease WPM" }
                        ) { Text("-") }
                        Text(
                            "${prefs.defaultWpm} WPM",
                            color      = ReaderColors.textWarm,
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        OutlinedButton(
                            onClick  = { vm.setWpm(prefs.defaultWpm + 25) },
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = ReaderColors.orpFocal),
                            modifier = Modifier.semantics { contentDescription = "Increase WPM" }
                        ) { Text("+") }
                    }
                }
            }

            // ── Punctuation Pauses ────────────────────────────────────────
            item {
                SettingSection(title = "Punctuation Pauses") {
                    Column {
                        Text(
                            "Automatically slow down at punctuation for better comprehension.",
                            color    = ReaderColors.textDimmed,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        // Sentence endings (. ? !)
                        Text(
                            "Sentence Endings (. ? !)  —  ${String.format(Locale.US, "%.1f", prefs.sentencePauseMultiplier)}x",
                            color      = ReaderColors.textWarm,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Slider(
                            value         = prefs.sentencePauseMultiplier,
                            onValueChange = { vm.setSentencePauseMultiplier(it) },
                            valueRange    = 1.0f..3.0f,
                            steps         = 19,
                            colors        = SliderDefaults.colors(
                                thumbColor       = ReaderColors.orpFocal,
                                activeTrackColor = ReaderColors.orpFocal
                            )
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Clause separators (, ; :)
                        Text(
                            "Clause Separators (, ; :)  —  ${String.format(Locale.US, "%.1f", prefs.clausePauseMultiplier)}x",
                            color      = ReaderColors.textWarm,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Slider(
                            value         = prefs.clausePauseMultiplier,
                            onValueChange = { vm.setClausePauseMultiplier(it) },
                            valueRange    = 1.0f..3.0f,
                            steps         = 19,
                            colors        = SliderDefaults.colors(
                                thumbColor       = ReaderColors.orpFocal,
                                activeTrackColor = ReaderColors.orpFocal
                            )
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "1.0x = no pause, 2.0x = double the normal word duration",
                            color    = ReaderColors.textDimmed,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── Default Words at a Time ───────────────────────────────────
            item {
                SettingSection(title = "Default Words at a Time") {
                    Column {
                        Text(
                            "Show multiple words per flash. Higher chunks train peripheral vision.",
                            color    = ReaderColors.textDimmed,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2, 3, 4).forEach { n ->
                                val selected = prefs.chunkSize == n
                                OutlinedButton(
                                    onClick  = { vm.setChunkSize(n) },
                                    modifier = Modifier.weight(1f),
                                    colors   = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) ReaderColors.orpFocal.copy(alpha = 0.15f)
                                                         else Color.Transparent,
                                        contentColor   = if (selected) ReaderColors.orpFocal
                                                         else ReaderColors.textDimmed
                                    ),
                                    border   = androidx.compose.foundation.BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) ReaderColors.orpFocal
                                                else ReaderColors.textDimmed.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        "$n",
                                        fontSize   = 16.sp,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "You can also adjust this live in the reader.",
                            color    = ReaderColors.textDimmed,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── Font Size ─────────────────────────────────────────────────
            item {
                SettingSection(title = "Font Size  -  ${prefs.fontSize}pt") {
                    Text(
                        "Reader auto-scales text smaller for multi-word chunks.",
                        color    = ReaderColors.textDimmed,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value         = prefs.fontSize.toFloat(),
                        onValueChange = { vm.setFontSize(it.toInt()) },
                        valueRange    = 16f..52f,
                        colors        = SliderDefaults.colors(
                            thumbColor       = ReaderColors.orpFocal,
                            activeTrackColor = ReaderColors.orpFocal
                        )
                    )
                }
            }

            // ── ORP Color Highlight ───────────────────────────────────────
            item {
                SettingSection(title = "ORP Highlight Color") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ORP_COLORS.forEachIndexed { idx, color ->
                            ColorChip(
                                color      = color,
                                isSelected = prefs.orpColorIndex == idx,
                                onClick    = { vm.setOrpColorIndex(idx) },
                                label      = "ORP color option $idx"
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Show ORP highlight", color = ReaderColors.textWarm, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked         = prefs.showOrpColor,
                            onCheckedChange = { vm.setShowOrpColor(it) },
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor = ReaderColors.background,
                                checkedTrackColor = ReaderColors.orpFocal
                            )
                        )
                    }
                }
            }

            // ── Reader Font ───────────────────────────────────────────────
            item {
                SettingSection(title = "Reader Font") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderFonts.ALL.forEach { option ->
                            val selected = prefs.fontIndex == option.index
                            Surface(
                                onClick  = { vm.setFontIndex(option.index) },
                                modifier = Modifier.fillMaxWidth(),
                                color    = if (selected) ReaderColors.orpFocal.copy(alpha = 0.10f)
                                           else ReaderColors.background,
                                shape    = RoundedCornerShape(10.dp),
                                border   = androidx.compose.foundation.BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) ReaderColors.orpFocal else ReaderColors.guideLine
                                )
                            ) {
                                Row(
                                    modifier              = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text       = option.label,
                                            color      = if (selected) ReaderColors.orpFocal else ReaderColors.textWarm,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = option.family,
                                            fontSize   = 15.sp
                                        )
                                        Text(text = option.subtitle, color = ReaderColors.textDimmed, fontSize = 11.sp)
                                    }
                                    if (option.isFixed) {
                                        Surface(
                                            color = ReaderColors.orpFocal.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "MONO",
                                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color      = ReaderColors.orpFocal,
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

            // ── Theme ─────────────────────────────────────────────────────
            item {
                SettingSection(title = "App Theme") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        THEME_OPTIONS.forEachIndexed { idx, label ->
                            val selected = prefs.themeMode == idx
                            OutlinedButton(
                                onClick  = { vm.setThemeMode(idx) },
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) ReaderColors.orpFocal.copy(alpha = 0.15f) else Color.Transparent,
                                    contentColor   = if (selected) ReaderColors.orpFocal else ReaderColors.textDimmed
                                ),
                                border   = androidx.compose.foundation.BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) ReaderColors.orpFocal else ReaderColors.textDimmed.copy(alpha = 0.4f)
                                )
                            ) {
                                Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            // ── Color Blindness Accessibility ────────────────────────────
            item {
                SettingSection(title = "Color Blindness Accessibility") {
                    Column {
                        Text(
                            "Remaps ORP highlight colors to vision-safe palettes (Bang Wong / IBM).",
                            color    = ReaderColors.textDimmed,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ColorBlindness.LABELS.forEachIndexed { idx, label ->
                                val selected = prefs.colorBlindnessMode == idx
                                val swatch = ColorBlindness.getOrpColors(idx)[0]
                                OutlinedButton(
                                    onClick  = { vm.setColorBlindnessMode(idx) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors   = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) swatch.copy(alpha = 0.15f) else Color.Transparent,
                                        contentColor   = if (selected) swatch else ReaderColors.textDimmed
                                    ),
                                    border   = androidx.compose.foundation.BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) swatch else ReaderColors.textDimmed.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier              = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            label,
                                            fontSize   = 13.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            ColorBlindness.getOrpColors(idx).take(3).forEach { c ->
                                                Surface(
                                                    modifier = Modifier.size(12.dp),
                                                    color    = c,
                                                    shape    = CircleShape
                                                ) {}
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Supported Formats ─────────────────────────────────────────
            item {
                SettingSection(title = "Supported Formats") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("TXT", "PDF", "EPUB", "DOCX", "IMAGE").forEach { fmt ->
                            Surface(color = ReaderColors.orpFocal.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    fmt,
                                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color      = ReaderColors.orpFocal,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ── Pro Status ────────────────────────────────────────────────
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color    = if (ProGate.isPro) ReaderColors.orpFocal.copy(alpha = 0.12f) else Color(0xFF2C2040),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (ProGate.isPro) "BADGR Bolt Pro - Active" else "Upgrade to Bolt Pro",
                                color      = if (ProGate.isPro) ReaderColors.orpFocal else ReaderColors.textWarm,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (ProGate.isPro) "All features unlocked" else "Stats, cloud sync, unlimited library",
                                color    = ReaderColors.textDimmed,
                                fontSize = 12.sp
                            )
                        }
                        if (!ProGate.isPro) {
                            OutlinedButton(
                                onClick = onNavigateToAccount,
                                colors  = ButtonDefaults.outlinedButtonColors(contentColor = ReaderColors.orpFocal)
                            ) { Text("Unlock") }
                        }
                    }
                }
            }

            // ── Support & Feedback ───────────────────────────────────────
            item {
                SettingSection(title = "Support & Feedback") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.badgr.orbreader"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ReaderColors.orpFocal)
                        ) {
                            Text(stringResource(R.string.rate_app))
                        }
                        
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:support@badgr.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "BADGR Bolt Feedback")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ReaderColors.orpFocal)
                        ) {
                            Text(stringResource(R.string.feedback))
                        }

                        OutlinedButton(
                            onClick = {
                                // Placeholder for Help/FAQ
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ReaderColors.orpFocal)
                        ) {
                            Text(stringResource(R.string.help_faq))
                        }
                    }
                }
            }

            // ── App Version ───────────────────────────────────────────────
            item {
                Text(
                    "BADGR Bolt v2.5.2 (build 8)",
                    color    = ReaderColors.textDimmed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, color = ReaderColors.textWarm, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ColorChip(color: Color, isSelected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp).semantics { contentDescription = label },
        onClick  = onClick,
        color    = color,
        shape    = CircleShape,
        border   = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null
    ) {}
}
