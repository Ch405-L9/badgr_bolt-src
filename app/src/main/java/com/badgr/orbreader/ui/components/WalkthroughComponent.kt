package com.badgr.orbreader.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badgr.orbreader.ui.theme.ReaderColors

data class WalkthroughStep(
    val title: String,
    val description: String,
    val targetRoute: String? = null
)

@Composable
fun WalkthroughOverlay(
    steps: List<WalkthroughStep>,
    onComplete: () -> Unit,
    onStepChange: (Int) -> Unit = {}
) {
    var currentStepIndex by remember { mutableStateOf(0) }
    val currentStep = steps[currentStepIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) {}
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ReaderColors.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step ${currentStepIndex + 1} of ${steps.size}",
                    color = ReaderColors.textDimmed,
                    fontSize = 12.sp
                )
                IconButton(onClick = onComplete) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = ReaderColors.textDimmed)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentStep.title,
                color = ReaderColors.orpFocal,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = currentStep.description,
                color = ReaderColors.textWarm,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (currentStepIndex < steps.size - 1) {
                        currentStepIndex++
                        onStepChange(currentStepIndex)
                    } else {
                        onComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ReaderColors.orpFocal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (currentStepIndex < steps.size - 1) "Next" else "Got it!",
                    color = ReaderColors.background,
                    fontWeight = FontWeight.Bold
                )
                if (currentStepIndex < steps.size - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
