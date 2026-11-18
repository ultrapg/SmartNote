package io.github.ultrapg.smartnote.ui.tags

import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.TagInfo
import io.github.ultrapg.smartnote.ui.designsystem.components.ExpressiveGooeyFab
import io.github.ultrapg.smartnote.ui.designsystem.components.expressivePressable
import io.github.ultrapg.smartnote.ui.designsystem.components.rememberGooeyRoundedShape
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteCardBorder
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TagsUiState(
    val filterQuery: String = "",
    val tags: List<TagInfo> = emptyList()
)

class TagsViewModel(
    private val tagRepository: TagRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow("")

    val uiState: StateFlow<TagsUiState> = combine(
        tagRepository.observeAllTags(),
        filterFlow
    ) { tags, query ->
        val clean = query.trim()
        val filtered = if (clean.isBlank()) {
            tags
        } else {
            tags.filter { it.name.contains(clean, ignoreCase = true) }
        }
        TagsUiState(filterQuery = query, tags = filtered)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TagsUiState()
    )

    fun updateFilter(q: String) {
        filterFlow.value = q
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            tagRepository.getOrCreateTag(name)
        }
    }

    fun renameTag(tagId: String, newName: String) {
        viewModelScope.launch {
            tagRepository.renameTag(tagId, newName)
        }
    }

    fun deleteTag(tagId: String) {
        viewModelScope.launch {
            tagRepository.deleteTag(tagId)
        }
    }

    class Factory(
        private val tagRepository: TagRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TagsViewModel(tagRepository) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    uiState: TagsUiState,
    onFilterChange: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (String, String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onOpenTagNotes: (TagInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<TagInfo?>(null) }
    var deletingTag by remember { mutableStateOf<TagInfo?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("Tags") })
        },
        floatingActionButton = {
            ExpressiveGooeyFab(
                onClick = { showCreateDialog = true },
                contentDescription = "Create tag"
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = uiState.filterQuery,
                onValueChange = onFilterChange,
                placeholder = { Text("Filter tags...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.filterQuery.isNotEmpty()) {
                        IconButton(onClick = { onFilterChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            if (uiState.tags.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = innerPadding.calculateBottomPadding() + 72.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No tags yet",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "SmartTags automatically suggests tags as you capture notes and screenshots, or tap + to create your own.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.tags, key = { it.id }) { tag ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val tagShape = rememberGooeyRoundedShape(
                            interactionSource = interactionSource,
                            baseRadius = 18.dp,
                            pressedRadius = 28.dp
                        )
                        Surface(
                            shape = tagShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = smartNoteCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(
                                    fadeInSpec = spring(stiffness = 280f),
                                    placementSpec = SmartNoteMotion.gooeyPlacementSpring(),
                                    fadeOutSpec = spring(stiffness = 320f)
                                )
                                .expressivePressable(interactionSource)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = ripple(),
                                    onClick = { onOpenTagNotes(tag) }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Label,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val sub = buildString {
                                        append("${tag.noteCount} note${if (tag.noteCount == 1) "" else "s"}")
                                        if (tag.suggestedCount > 0) {
                                            append(" • ${tag.suggestedCount} suggested")
                                        }
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editingTag = tag }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Rename ${tag.name}")
                                }
                                IconButton(onClick = { deletingTag = tag }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${tag.name}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Tag") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) onCreateTag(name)
                        showCreateDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    editingTag?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { editingTag = null },
            title = { Text("Rename Tag") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name (merges if already exists)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) onRenameTag(target.id, newName)
                        editingTag = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingTag = null }) { Text("Cancel") }
            }
        )
    }

    deletingTag?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text("Delete #${target.name}?") },
            text = { Text("This removes the tag label from ${target.noteCount} note(s). Notes themselves will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(target.id)
                        deletingTag = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) { Text("Cancel") }
            }
        )
    }
}
