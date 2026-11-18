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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.expressivePressable(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.975f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "expressive_press_scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun smartNoteCardBorder(): BorderStroke? {
    val meta = LocalSmartNoteThemeMeta.current
    return if (meta.isAmoledDark) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFloatingEditorToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        content = content
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
    val shape: Shape = when (kind) {
        ExpressiveBadgeShapeKind.SUNNY -> MaterialShapes.Sunny.toShape()
        ExpressiveBadgeShapeKind.COOKIE -> MaterialShapes.Cookie9Sided.toShape()
        ExpressiveBadgeShapeKind.PILL -> MaterialShapes.Pill.toShape()
    }
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
