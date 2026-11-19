package io.github.ultrapg.smartnote.ui.designsystem.theme

import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.ultrapg.smartnote.domain.model.DarkModeConfig
import io.github.ultrapg.smartnote.domain.model.ThemePalette

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object SmartNoteSoftMotionScheme : MotionScheme {
    private val defaultSpatial = spring<Any>(dampingRatio = 1.0f, stiffness = 500f)
    private val fastSpatial = spring<Any>(dampingRatio = 1.0f, stiffness = 700f)
    private val slowSpatial = spring<Any>(dampingRatio = 1.0f, stiffness = 380f)
    private val defaultEffects = spring<Any>(dampingRatio = 1.0f, stiffness = 1400f)
    private val fastEffects = spring<Any>(dampingRatio = 1.0f, stiffness = 2400f)
    private val slowEffects = spring<Any>(dampingRatio = 1.0f, stiffness = 800f)

    @Suppress("UNCHECKED_CAST")
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = defaultSpatial as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = fastSpatial as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = slowSpatial as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = defaultEffects as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = fastEffects as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = slowEffects as FiniteAnimationSpec<T>
}

object SmartNoteMotion {
    fun <T> softPlacementSpring(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 520f)
    fun <T> softSizeSpring(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 550f)
    fun <T> expressivePopSpring(): SpringSpec<T> = spring(dampingRatio = 0.78f, stiffness = 520f)
    fun <T> expressiveMorphSpring(): SpringSpec<T> = spring(dampingRatio = 0.84f, stiffness = 460f)
    fun softPressSpring(): SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 700f)
    fun <T> refinedMenuOpenSpring(): SpringSpec<T> = spring(dampingRatio = 0.88f, stiffness = 460f)
    fun <T> refinedMenuCloseSpring(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 620f)
    fun <T> refinedMenuMorphSpring(): SpringSpec<T> = spring(dampingRatio = 0.90f, stiffness = 480f)
    fun <T> refinedIconSpring(): SpringSpec<T> = spring(dampingRatio = 0.88f, stiffness = 540f)
}

@Immutable
data class SmartNoteThemeMeta(
    val palette: ThemePalette = ThemePalette.DYNAMIC,
    val isDark: Boolean = false,
    val isAmoledDark: Boolean = false,
    val isReducedMotion: Boolean = false
)

val LocalSmartNoteThemeMeta = staticCompositionLocalOf { SmartNoteThemeMeta() }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmartNoteTheme(
    palette: ThemePalette = ThemePalette.DYNAMIC,
    darkModeConfig: DarkModeConfig = DarkModeConfig.SYSTEM,
    reducedMotionPreference: Boolean = false,
    syncWindowBackground: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (darkModeConfig) {
        DarkModeConfig.SYSTEM -> systemDark
        DarkModeConfig.LIGHT -> false
        DarkModeConfig.DARK -> true
    }

    val colorScheme = resolveColorScheme(
        context = context,
        palette = palette,
        darkTheme = useDarkTheme
    )

    val animatorScale = rememberAnimatorDurationScale(context)
    val effectiveReducedMotion = reducedMotionPreference || animatorScale <= 0f
    val motionScheme = if (effectiveReducedMotion) {
        MotionScheme.standard()
    } else {
        SmartNoteSoftMotionScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                if (syncWindowBackground) {
                    window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb()))
                }
                @Suppress("DEPRECATION")
                window.statusBarColor = AndroidColor.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = AndroidColor.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching {
                        window.isNavigationBarContrastEnforced = false
                    }
                }
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }
    }

    val meta = SmartNoteThemeMeta(
        palette = palette,
        isDark = useDarkTheme,
        isAmoledDark = palette == ThemePalette.AMOLED && useDarkTheme,
        isReducedMotion = effectiveReducedMotion
    )

    CompositionLocalProvider(LocalSmartNoteThemeMeta provides meta) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = SmartNoteShapes,
            typography = SmartNoteTypography,
            content = content
        )
    }
}

fun resolveColorScheme(
    context: Context,
    palette: ThemePalette,
    darkTheme: Boolean
): ColorScheme = when (palette) {
    ThemePalette.DYNAMIC -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) SmartNotePalettes.VioletDark else SmartNotePalettes.VioletLight
        }
    }
    ThemePalette.VIOLET -> if (darkTheme) SmartNotePalettes.VioletDark else SmartNotePalettes.VioletLight
    ThemePalette.NEUTRAL -> if (darkTheme) SmartNotePalettes.NeutralDark else SmartNotePalettes.NeutralLight
    ThemePalette.AMOLED -> if (darkTheme) SmartNotePalettes.AmoledDark else SmartNotePalettes.AmoledLight
}

@Composable
private fun rememberAnimatorDurationScale(context: Context): Float {
    var scale by remember {
        mutableFloatStateOf(readAnimatorScale(context))
    }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(context)
            }
        }
        runCatching { resolver.registerContentObserver(uri, false, observer) }
        onDispose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }
    return scale
}

private fun readAnimatorScale(context: Context): Float {
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    }.getOrDefault(1f)
}
