package io.github.ultrapg.smartnote.ui.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ripple
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteMotion

fun Modifier.expressivePressable(
    interactionSource: MutableInteractionSource,
    pressedScaleX: Float = 0.965f,
    pressedScaleY: Float = 0.935f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion
    if (reducedMotion) {
        this
    } else {
        val scaleXState = animateFloatAsState(
            targetValue = if (pressed) pressedScaleX else 1f,
            animationSpec = SmartNoteMotion.gooeyPressSpringX(),
            label = "gooey_press_scale_x"
        )
        val scaleYState = animateFloatAsState(
            targetValue = if (pressed) pressedScaleY else 1f,
            animationSpec = SmartNoteMotion.gooeyPressSpringY(),
            label = "gooey_press_scale_y"
        )
        graphicsLayer {
            this.scaleX = scaleXState.value
            this.scaleY = scaleYState.value
        }
    }
}

@Composable
fun rememberGooeyRoundedShape(
    interactionSource: MutableInteractionSource,
    baseRadius: Dp = 22.dp,
    pressedRadius: Dp = 32.dp,
    selectedRadius: Dp = 28.dp,
    isSelected: Boolean = false
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion
    val targetRadius = when {
        pressed && !reducedMotion -> pressedRadius
        isSelected -> selectedRadius
        else -> baseRadius
    }
    if (reducedMotion) {
        return RoundedCornerShape(targetRadius)
    }
    val radius by animateDpAsState(
        targetValue = targetRadius,
        animationSpec = SmartNoteMotion.gooeyCornerSpring(),
        label = "gooey_corner_radius"
    )
    return RoundedCornerShape(radius.coerceAtLeast(0.dp))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun buildMorphedPolygonPath(
    morph: Morph,
    progress: Float,
    size: Size
): Path {
    val path = Path()
    morph.toPath(progress = progress.coerceIn(0f, 1f), path = path)
    val matrix = Matrix().apply {
        scale(x = size.width, y = size.height)
    }
    path.transform(matrix)
    val bounds = path.getBounds()
    path.translate(size.center - bounds.center)
    return path
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveGooeyFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    icon: ImageVector = Icons.Rounded.Add
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion

    val morph = remember {
        Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Cookie9Sided)
    }
    val targetProgress = when {
        expanded -> 1f
        pressed -> 0.85f
        else -> 0f
    }
    val morphProgressState = animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (reducedMotion) {
            spring(stiffness = 1200f)
        } else {
            spring(dampingRatio = 0.48f, stiffness = 200f)
        },
        label = "gooey_fab_morph"
    )
    val iconRotationState = animateFloatAsState(
        targetValue = when {
            reducedMotion -> 0f
            expanded -> 45f
            pressed -> 14f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.50f, stiffness = 210f),
        label = "gooey_fab_rotation"
    )

    Box(
        modifier = modifier
            .size(60.dp)
            .expressivePressable(
                interactionSource = interactionSource,
                pressedScaleX = 0.91f,
                pressedScaleY = 0.87f
            )
            .drawBehind {
                val shadowPath = buildMorphedPolygonPath(
                    morph = morph,
                    progress = morphProgressState.value,
                    size = size
                )
                shadowPath.translate(Offset(0f, 3.dp.toPx()))
                drawPath(path = shadowPath, color = Color.Black.copy(alpha = 0.18f))

                val fillPath = buildMorphedPolygonPath(
                    morph = morph,
                    progress = morphProgressState.value,
                    size = size
                )
                drawPath(path = fillPath, color = containerColor)
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 30.dp),
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { rotationZ = iconRotationState.value }
        )
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
    morphed: Boolean = false,
    content: @Composable () -> Unit
) {
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion
    val morph = remember(kind) {
        when (kind) {
            ExpressiveBadgeShapeKind.SUNNY -> Morph(MaterialShapes.Sunny, MaterialShapes.Cookie9Sided)
            ExpressiveBadgeShapeKind.COOKIE -> Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst)
            ExpressiveBadgeShapeKind.PILL -> Morph(MaterialShapes.Pill, MaterialShapes.Cookie4Sided)
        }
    }
    val progressState = animateFloatAsState(
        targetValue = if (morphed && !reducedMotion) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 190f),
        label = "badge_morph"
    )
    val scaleState = animateFloatAsState(
        targetValue = if (morphed && !reducedMotion) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.46f, stiffness = 210f),
        label = "badge_scale"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .drawBehind {
                val path = buildMorphedPolygonPath(
                    morph = morph,
                    progress = progressState.value,
                    size = this.size
                )
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
