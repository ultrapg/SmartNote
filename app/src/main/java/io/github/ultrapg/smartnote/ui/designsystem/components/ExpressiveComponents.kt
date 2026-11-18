package io.github.ultrapg.smartnote.ui.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import coil.request.ImageRequest
import coil.size.Precision
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteMotion
import java.io.File

fun Modifier.expressivePressable(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.99f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion
    if (reducedMotion) {
        this
    } else {
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec = SmartNoteMotion.softPressSpring(),
            label = "expressive_press_scale"
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
}

@Composable
fun smartNoteCardBorder(): BorderStroke? = null

@Composable
fun smartNoteTonalFilterChipColors(): SelectableChipColors =
    FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

@Composable
fun rememberBoundedImageRequest(
    file: File,
    maxDimensionPx: Int
): ImageRequest {
    val context = LocalContext.current
    val lastModified = if (file.exists()) file.lastModified() else 0L
    return remember(file.absolutePath, lastModified, maxDimensionPx) {
        ImageRequest.Builder(context)
            .data(file)
            .size(maxDimensionPx, maxDimensionPx)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }
}

@Composable
fun VerticalLazyScrollbar(
    listState: LazyListState,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val canScroll = listState.canScrollForward || listState.canScrollBackward
    val isScrolling = listState.isScrollInProgress
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion

    val targetAlpha = when {
        !canScroll -> 0f
        isScrolling -> 0.78f
        else -> 0.38f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = if (reducedMotion) {
            tween(durationMillis = if (isScrolling) 40 else 90)
        } else {
            tween(durationMillis = if (isScrolling) 90 else 260)
        },
        label = "vertical_scrollbar_alpha"
    )
    if (alpha <= 0.01f) return

    val cachedHeights = remember(listState) { mutableMapOf<Int, Int>() }
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val layoutInfo = listState.layoutInfo
                val visible = layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) return@drawBehind
                visible.forEach { item ->
                    cachedHeights[item.index] = item.size
                }

                val topPadPx = topPadding.toPx()
                val bottomPadPx = bottomPadding.toPx()
                val trackTop = topPadPx
                val trackHeight = (size.height - topPadPx - bottomPadPx).coerceAtLeast(0f)
                if (trackHeight <= 48.dp.toPx()) return@drawBehind

                val totalItemsCount = layoutInfo.totalItemsCount
                if (totalItemsCount <= 0) return@drawBehind

                val avgItemHeight = if (cachedHeights.isNotEmpty()) {
                    cachedHeights.values.sum().toFloat() / cachedHeights.size.toFloat()
                } else {
                    visible.sumOf { it.size }.toFloat() / visible.size.toFloat()
                }

                val totalItemsHeightPx = (0 until totalItemsCount).sumOf { idx ->
                    cachedHeights[idx] ?: avgItemHeight.toInt().coerceAtLeast(1)
                }.toFloat() + (totalItemsCount - 1).coerceAtLeast(0) * layoutInfo.mainAxisItemSpacing.toFloat()

                val innerViewportPx = (
                    layoutInfo.viewportEndOffset -
                        layoutInfo.viewportStartOffset -
                        layoutInfo.beforeContentPadding -
                        layoutInfo.afterContentPadding
                    ).toFloat().coerceAtLeast(1f)

                val totalSpanWithPaddingPx =
                    totalItemsHeightPx + layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
                val viewportHeightPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
                val fullScrollablePx = totalSpanWithPaddingPx - viewportHeightPx
                if (fullScrollablePx <= 1f) return@drawBehind

                val firstVisible = visible.first()
                val scrolledPx = (0 until firstVisible.index).sumOf { idx ->
                    (cachedHeights[idx] ?: avgItemHeight.toInt().coerceAtLeast(1)) +
                        layoutInfo.mainAxisItemSpacing
                }.toFloat() - firstVisible.offset.toFloat()

                val scrollFraction = (scrolledPx / fullScrollablePx).coerceIn(0f, 1f)
                val visibleFraction = (innerViewportPx / totalItemsHeightPx.coerceAtLeast(innerViewportPx))
                    .coerceIn(0.08f, 0.85f)
                val thumbHeightPx = (trackHeight * visibleFraction)
                    .coerceAtLeast(36.dp.toPx())
                    .coerceAtMost(trackHeight * 0.85f)
                val thumbTop = trackTop + (trackHeight - thumbHeightPx) * scrollFraction
                val thumbWidthPx = 4.dp.toPx()
                val endMarginPx = 3.dp.toPx()
                val thumbLeft = (size.width - thumbWidthPx - endMarginPx).coerceAtLeast(0f)
                val cornerPx = thumbWidthPx / 2f

                drawRoundRect(
                    color = thumbColor.copy(alpha = alpha),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                )
            }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSplitSaveButton(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    onMenuClick: () -> Unit,
    menuContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onPrimaryClick,
                enabled = enabled
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Text(primaryLabel)
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                onClick = onMenuClick,
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = menuContentDescription
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveConnectedButtonGroup(
    modifier: Modifier = Modifier,
    content: ButtonGroupScope.() -> Unit
) {
    ButtonGroup(
        overflowIndicator = { menuState ->
            IconButton(
                onClick = {
                    if (menuState.isShowing) menuState.dismiss() else menuState.show()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "More options"
                )
            }
        },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        content = content
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveToggleChipButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = ToggleButtonDefaults.tonalToggleButtonColors(),
        contentPadding = contentPadding,
        content = content
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoading(
    modifier: Modifier = Modifier,
    contained: Boolean = false
) {
    if (contained) {
        ContainedLoadingIndicator(modifier = modifier)
    } else {
        LoadingIndicator(modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
enum class ExpressiveBadgeShapeKind {
    SUNNY,
    COOKIE,
    PILL
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveShapeBadge(
    kind: ExpressiveBadgeShapeKind,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    content: @Composable () -> Unit
) {
    val polygonMorph = remember(kind) {
        val polygon = when (kind) {
            ExpressiveBadgeShapeKind.SUNNY -> MaterialShapes.Sunny
            ExpressiveBadgeShapeKind.COOKIE -> MaterialShapes.Cookie9Sided
            ExpressiveBadgeShapeKind.PILL -> MaterialShapes.Pill
        }
        Morph(polygon, polygon)
    }
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val path = Path()
                polygonMorph.toPath(progress = 0f, path = path)
                val matrix = Matrix().apply {
                    scale(x = this@drawBehind.size.width, y = this@drawBehind.size.height)
                }
                path.transform(matrix)
                val bounds = path.getBounds()
                path.translate(this.size.center - bounds.center)
                drawPath(path = path, color = containerColor)
            }
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
