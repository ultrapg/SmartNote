package io.github.ultrapg.smartnote.ui.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteMotion

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
