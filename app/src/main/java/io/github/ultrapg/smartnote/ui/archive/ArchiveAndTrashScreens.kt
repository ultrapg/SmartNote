package io.github.ultrapg.smartnote.ui.archive

import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ultrapg.smartnote.domain.model.DuplicatePairInfo
import io.github.ultrapg.smartnote.domain.model.NoteSummary
import io.github.ultrapg.smartnote.ui.designsystem.components.expressivePressable
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteCardBorder
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteMotion
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archivedNotes: List<NoteSummary>,
    onOpenNote: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onMoveToTrash: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Archive") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (archivedNotes.isEmpty()) {
            EmptyStateMessage(
                icon = Icons.Rounded.Archive,
                title = "Archive is empty",
                subtitle = "Archived notes stay searchable and keep their tags and attachments.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(archivedNotes, key = { it.id }) { note ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val shape = RoundedCornerShape(18.dp)
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = smartNoteCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = tween(160),
                                placementSpec = SmartNoteMotion.softPlacementSpring(),
                                fadeOutSpec = tween(140)
                            )
                            .expressivePressable(interactionSource)
                            .clip(shape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                onClick = { onOpenNote(note.id) }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title.ifBlank { "Untitled note" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (note.previewText.isNotBlank()) {
                                    Text(
                                        text = note.previewText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { onUnarchive(note.id) }) {
                                Icon(Icons.Rounded.Unarchive, contentDescription = "Unarchive note")
                            }
                            IconButton(onClick = { onMoveToTrash(note.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Move to Recycle Bin")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    deletedNotes: List<NoteSummary>,
    onRestore: (String) -> Unit,
    onDeletePermanently: (String) -> Unit,
    onEmptyRecycleBin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var confirmEmptyAll by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (deletedNotes.isNotEmpty()) {
                        TextButton(onClick = { confirmEmptyAll = true }) {
                            Text("Empty bin")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (deletedNotes.isEmpty()) {
            EmptyStateMessage(
                icon = Icons.Rounded.DeleteOutline,
                title = "Recycle Bin is empty",
                subtitle = "Deleted notes are kept safely here for 30 days before permanent removal.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "trash_retention_banner") {
                    Text(
                        text = "Notes in the Recycle Bin are automatically purged after 30 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(deletedNotes, key = { it.id }) { note ->
                    val elapsedDays = note.deletedAtMs?.let {
                        ((System.currentTimeMillis() - it) / (24L * 60L * 60L * 1000L)).toInt()
                    } ?: 0
                    val daysLeft = max(0, 30 - elapsedDays)

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = smartNoteCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title.ifBlank { "Untitled note" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$daysLeft day${if (daysLeft == 1) "" else "s"} left • ${note.previewText.take(60)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onRestore(note.id) }) {
                                Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Restore note")
                            }
                            IconButton(onClick = { confirmDeleteId = note.id }) {
                                Icon(
                                    Icons.Rounded.DeleteForever,
                                    contentDescription = "Delete permanently",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDeleteId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Delete permanently?") },
            text = { Text("This permanently deletes the note and any unreferenced attachments. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePermanently(targetId)
                        confirmDeleteId = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmEmptyAll) {
        AlertDialog(
            onDismissRequest = { confirmEmptyAll = false },
            title = { Text("Empty Recycle Bin?") },
            text = { Text("Permanently delete all ${deletedNotes.size} note(s) in the Recycle Bin? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEmptyRecycleBin()
                        confirmEmptyAll = false
                    }
                ) { Text("Empty all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmptyAll = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateReviewScreen(
    duplicates: List<DuplicatePairInfo>,
    onOpenNote: (String) -> Unit,
    onDismissPair: (DuplicatePairInfo) -> Unit,
    onRecycleOlderCopy: (DuplicatePairInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("SmartDuplicates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (duplicates.isEmpty()) {
            EmptyStateMessage(
                icon = Icons.Rounded.ContentCopy,
                title = "No duplicate notes detected",
                subtitle = "SmartNote never deletes duplicates automatically; potential matches appear here for review.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(duplicates, key = { it.pairKey }) { pair ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = smartNoteCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = pair.matchReason,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            DuplicateCardLine("Newer", pair.noteA) { onOpenNote(pair.noteA.id) }
                            DuplicateCardLine("Older", pair.noteB) { onOpenNote(pair.noteB.id) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onDismissPair(pair) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Keep Both")
                                }
                                TextButton(
                                    onClick = { onRecycleOlderCopy(pair) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Recycle Older")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateCardLine(
    label: String,
    note: NoteSummary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "$label: ${note.title.ifBlank { "Untitled" }}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (note.previewText.isNotBlank()) {
                Text(
                    text = note.previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
