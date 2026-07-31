package com.badgr.orbreader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.badgr.orbreader.achievements.AchievementDefinitions
import com.badgr.orbreader.ui.theme.ReaderColors
import kotlinx.coroutines.delay

/**
 * Auto-dismissing achievement unlock banner.
 * Slides down from the top of the screen, holds for 3 seconds, slides back up.
 * Shows one achievement at a time; queues if multiple unlock simultaneously.
 */
@Composable
fun AchievementToastHost(
    newAchievementIds : List<String>,
    onConsumed        : () -> Unit,
    modifier          : Modifier = Modifier
) {
    var currentId by remember { mutableStateOf<String?>(null) }
    var visible   by remember { mutableStateOf(false) }
    val haptic    = LocalHapticFeedback.current

    LaunchedEffect(newAchievementIds) {
        if (newAchievementIds.isEmpty()) return@LaunchedEffect
        for (id in newAchievementIds) {
            currentId = id
            visible   = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(3_200)
            visible   = false
            delay(400)   // wait for exit animation
        }
        onConsumed()
    }

    val def = currentId?.let { AchievementDefinitions.byId[it] }

    AnimatedVisibility(
        visible  = visible && def != null,
        enter    = slideInVertically(
            initialOffsetY = { -it },
            animationSpec  = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(),
        exit     = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .zIndex(10f)
    ) {
        if (def != null) {
            Surface(
                color  = ReaderColors.orpFocal.copy(alpha = 0.95f),
                shape  = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(def.emoji, fontSize = 26.sp)
                    Column {
                        Text(
                            "Achievement Unlocked",
                            fontSize      = 9.sp,
                            fontFamily    = FontFamily.Monospace,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color         = ReaderColors.background.copy(alpha = 0.7f)
                        )
                        Text(
                            def.title,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color      = ReaderColors.background
                        )
                        Text(
                            def.description,
                            fontSize = 11.sp,
                            color    = ReaderColors.background.copy(alpha = 0.80f)
                        )
                    }
                }
            }
        }
    }
}
