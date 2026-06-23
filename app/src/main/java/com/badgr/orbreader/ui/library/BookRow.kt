package com.badgr.orbreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.badgr.orbreader.data.model.Book
import com.badgr.orbreader.ui.theme.ReaderColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BookRow(
    book           : Book,
    onClick        : () -> Unit,
    onDelete       : () -> Unit,
    onSummary      : () -> Unit = {},
    onQuiz         : () -> Unit = {},
    dueCount       : Int        = 0,
    onReview       : () -> Unit = {},
    onEditCategory : () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dateStr = remember(book.createdAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(book.createdAt))
    }

    // ── Delete confirmation dialog ────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "Delete book?",
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "\"${book.title}\" will be permanently removed from your library.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color    = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val coverFile = book.coverPath?.let { File(it) }
            if (coverFile != null && coverFile.exists()) {
                AsyncImage(
                    model              = coverFile,
                    contentDescription = "Cover of ${book.title}",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(width = 48.dp, height = 68.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 68.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = book.title,
                    style      = MaterialTheme.typography.bodyLarge,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = "${book.fileType.name} · ${book.wordCount} words · $dateStr",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                book.category?.let { cat ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text     = cat,
                        fontSize = 12.sp,
                        color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier
                            .clickable(onClick = onEditCategory)
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
            }

            IconButton(onClick = onSummary) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = "Summarize ${book.title}",
                    tint               = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onQuiz) {
                Icon(
                    Icons.Default.Quiz,
                    contentDescription = "Quiz for ${book.title}",
                    tint               = MaterialTheme.colorScheme.tertiary
                )
            }
            if (dueCount > 0) {
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onReview)
                ) {
                    Text(
                        "$dueCount due",
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color      = MaterialTheme.colorScheme.onError,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${book.title}",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
