package io.github.ultrapg.smartnote.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ultrapg.smartnote.search.SearchDateFilter
import io.github.ultrapg.smartnote.search.SearchMatchField
import io.github.ultrapg.smartnote.search.SmartSearchResultItem
import io.github.ultrapg.smartnote.ui.designsystem.components.expressivePressable
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteCardBorder
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteTonalFilterChipColors
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteMotion
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartSearchScreen(
    uiState: SmartSearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onApplyRecentSearch: (String) -> Unit,
    onDeleteRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onApplyDidYouMean: (String) -> Unit,
    onToggleTagFilter: (String) -> Unit,
    onTogglePinnedFilter: () -> Unit,
    onToggleImageFilter: () -> Unit,
    onToggleOcrFilter: () -> Unit,
    onToggleChecklistFilter: () -> Unit,
    onToggleLinkFilter: () -> Unit,
    onSelectDateFilter: (SearchDateFilter) -> Unit,
    onOpenNote: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.query, uiState.filter, uiState.didYouMeanSuggestion) {
        listState.scrollToItem(0)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search notes, tags, images, links...") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "f_pinned") {
                        FilterChip(
                            selected = uiState.filter.pinnedOnly,
                            onClick = onTogglePinnedFilter,
                            label = { Text("Pinned") },
                            leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            border = null,
                            colors = smartNoteTonalFilterChipColors()
                        )
                    }
                    item(key = "f_ocr") {
                        FilterChip(
                            selected = uiState.filter.hasOcrOnly,
                            onClick = onToggleOcrFilter,
                            label = { Text("Has OCR") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.TextSnippet, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            border = null,
                            colors = smartNoteTonalFilterChipColors()
                        )
                    }
                    item(key = "f_image") {
                        FilterChip(
                            selected = uiState.filter.hasImageOnly,
                            onClick = onToggleImageFilter,
                            label = { Text("Images") },
                            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            border = null,
                            colors = smartNoteTonalFilterChipColors()
                        )
                    }
                    item(key = "f_checklist") {
                        FilterChip(
                            selected = uiState.filter.hasChecklistOnly,
                            onClick = onToggleChecklistFilter,
                            label = { Text("Checklists") },
                            border = null,
                            colors = smartNoteTonalFilterChipColors()
                        )
                    }
                    item(key = "f_link") {
                        FilterChip(
                            selected = uiState.filter.hasLinkOnly,
                            onClick = onToggleLinkFilter,
                            label = { Text("Links") },
                            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            border = null,
                            colors = smartNoteTonalFilterChipColors()
                        )
                    }
                    items(SearchDateFilter.entries.filter { it != SearchDateFilter.ANY }, key = { it.name }) { df ->
                        val label = when (df) {
                            SearchDateFilter.TODAY -> "Today"
                            SearchDateFilter.LAST_7_DAYS -> "Last 7 days"
                            SearchDateFilter.LAST_30_DAYS -> "Last 30 days"
                            SearchDateFilter.ANY -> ""
                        }
                        FilterChip(
                            selected = uiState.filter.dateFilter == df,
                            onClick = {
                                onSelectDateFilter(if (uiState.filter.dateFilter == df) SearchDateFilter.ANY else df)
                            },
                            label = { Text(label) },
                            border = null,
                            colors = smartNoteTonalFilterChipColors()
                        )
                    }
                    items(uiState.availableTags.take(10), key = { "tag_${it.id}" }) { tag ->
                        FilterChip(
                            selected = tag.name in uiState.filter.tagNames,
                            onClick = { onToggleTagFilter(tag.name) },
                            label = { Text("#${tag.name}") },
                            border = null,
                            colors = smartNoteTonalFilterChipColors()
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = maxOf(innerPadding.calculateBottomPadding(), imeBottom) + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.didYouMeanSuggestion != null) {
                item(key = "did_you_mean") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onApplyDidYouMean(uiState.didYouMeanSuggestion) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Did you mean: ${uiState.didYouMeanSuggestion}?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (uiState.query.isBlank() && uiState.recentSearchesEnabled && uiState.recentSearches.isNotEmpty()) {
                item(key = "recent_searches") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Recent searches",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onClearRecentSearches) {
                                Text("Clear")
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            uiState.recentSearches.forEach { recent ->
                                InputChip(
                                    selected = false,
                                    onClick = { onApplyRecentSearch(recent) },
                                    label = { Text(recent) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove recent $recent",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onDeleteRecentSearch(recent) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.query.isNotBlank()) {
                item(key = "result_count") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${uiState.results.size} result${if (uiState.results.size == 1) "" else "s"} (${uiState.durationMs} ms)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.aiExpandedTerms.isNotEmpty() || uiState.usedGeminiNanoMatch) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (uiState.aiExpandedTerms.isNotEmpty()) {
                                        "Smart match: ${uiState.aiExpandedTerms.joinToString(", ")}"
                                    } else {
                                        "Matched by Gemini Nano"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            items(uiState.results, key = { it.note.id }) { item ->
                SearchResultCard(
                    item = item,
                    onClick = { onOpenNote(item.note.id) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = if (reducedMotion) null else SmartNoteMotion.softPlacementSpring(),
                        fadeOutSpec = null
                    )
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: SmartSearchResultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
    val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
    val cardShape = RoundedCornerShape(18.dp)

    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = smartNoteCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .expressivePressable(interactionSource)
            .clip(cardShape)
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple(), onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.note.isPinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = buildHighlightedText(
                        text = item.note.title.ifBlank { "Untitled note" },
                        terms = item.highlightTerms,
                        bg = highlightBg,
                        fg = highlightFg
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (item.note.previewText.isNotBlank()) {
                Text(
                    text = buildHighlightedText(
                        text = item.note.previewText,
                        terms = item.highlightTerms,
                        bg = highlightBg,
                        fg = highlightFg
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!item.ocrPreviewSnippet.isNullOrBlank() && SearchMatchField.OCR in item.matchedFields) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.TextSnippet, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            text = buildHighlightedText(
                                text = "OCR: ${item.ocrPreviewSnippet}",
                                terms = item.highlightTerms,
                                bg = highlightBg,
                                fg = highlightFg
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (item.note.tags.isNotEmpty() || item.matchedFields.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.note.tags.take(3).forEach { tag ->
                        Text(
                            text = "#${tag.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = item.matchedFields.joinToString(" • ") {
                            when (it) {
                                SearchMatchField.TITLE -> "Title"
                                SearchMatchField.CONTENT -> "Text"
                                SearchMatchField.CHECKLIST -> "Checklist"
                                SearchMatchField.TAG -> "Tag"
                                SearchMatchField.OCR -> "OCR"
                                SearchMatchField.URL -> "Link"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun buildHighlightedText(
    text: String,
    terms: List<String>,
    bg: Color,
    fg: Color
): AnnotatedString {
    if (text.isEmpty() || terms.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase(Locale.ROOT)
    return buildAnnotatedString {
        append(text)
        for (term in terms) {
            val clean = term.lowercase(Locale.ROOT).trim()
            if (clean.length < 2) continue
            var start = 0
            while (true) {
                val found = lower.indexOf(clean, startIndex = start)
                if (found < 0) break
                addStyle(
                    SpanStyle(
                        background = bg,
                        color = fg,
                        fontWeight = FontWeight.Bold
                    ),
                    start = found,
                    end = found + clean.length
                )
                start = found + clean.length
            }
        }
    }
}
