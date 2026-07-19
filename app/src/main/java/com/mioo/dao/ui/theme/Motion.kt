package com.mioo.dao.ui.theme

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens and helpers aligned with Emil Kowalski's design-engineering rules:
 * - ease-out for enter/exit UI (never ease-in)
 * - strong custom curves (built-in easings are too soft)
 * - UI under ~300ms; exit faster than enter
 * - never enter from scale(0) — start at ~0.95 + opacity
 * - press feedback ~100–160ms scale(0.97)
 * - respect reduced motion (opacity-only / snap)
 */
object MiooMotion {
    /** Strong ease-out for UI enter/exit — cubic-bezier(0.23, 1, 0.32, 1) */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Strong ease-in-out for on-screen morphing — cubic-bezier(0.77, 0, 0.175, 1) */
    val EaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** iOS-like drawer curve — cubic-bezier(0.32, 0.72, 0, 1) */
    val EaseDrawer: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    // Durations (ms) — UI should stay under 300ms
    const val DurationPress = 130
    const val DurationTooltip = 150
    const val DurationSmall = 180
    const val DurationMedium = 220
    const val DurationModal = 260
    const val DurationExitFast = 120
    const val DurationTab = 90
    const val DurationShimmer = 900

    /** Initial scale for enter — never 0. */
    const val ScaleEnterFrom = 0.95f
    const val ScaleExitTo = 0.97f
    const val ScalePress = 0.97f
    const val ScaleCardPress = 0.985f

    fun <T> tweenOut(durationMillis: Int = DurationSmall): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = EaseOut)

    fun <T> tweenExit(durationMillis: Int = DurationExitFast): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = EaseOut)

    // --- Shared enter / exit recipes (GPU-friendly: opacity + scale; height only when needed) ---

    /** Small chrome: quote chip, image thumb, tool panels. */
    fun softEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return fadeIn(tween(0))
        return fadeIn(tweenOut(DurationSmall)) +
            scaleIn(
                initialScale = ScaleEnterFrom,
                animationSpec = tweenOut(DurationSmall)
            )
    }

    fun softExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return fadeOut(tween(0))
        return fadeOut(tweenExit(DurationExitFast)) +
            scaleOut(
                targetScale = ScaleExitTo,
                animationSpec = tweenExit(DurationExitFast)
            )
    }

    /**
     * Soft enter that also expands vertical space (composer chips).
     * Height animation is a deliberate layout tradeoff so content doesn't jump.
     */
    fun softExpandEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return fadeIn(tween(0)) + expandVertically(tween(0), expandFrom = Alignment.Top)
        return fadeIn(tweenOut(DurationSmall)) +
            scaleIn(initialScale = ScaleEnterFrom, animationSpec = tweenOut(DurationSmall)) +
            expandVertically(
                animationSpec = tweenOut(DurationSmall),
                expandFrom = Alignment.Top
            )
    }

    fun softExpandExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return fadeOut(tween(0)) + shrinkVertically(tween(0), shrinkTowards = Alignment.Top)
        return fadeOut(tweenExit(DurationExitFast)) +
            scaleOut(targetScale = ScaleExitTo, animationSpec = tweenExit(DurationExitFast)) +
            shrinkVertically(
                animationSpec = tweenExit(DurationExitFast),
                shrinkTowards = Alignment.Top
            )
    }

    /** Modal / popover: centered scale — modals stay origin-center by design. */
    fun modalEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return fadeIn(tween(0))
        return fadeIn(tweenOut(DurationModal)) +
            scaleIn(
                initialScale = ScaleEnterFrom,
                animationSpec = tweenOut(DurationModal)
            )
    }

    fun modalExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return fadeOut(tween(0))
        return fadeOut(tweenExit(DurationExitFast)) +
            scaleOut(
                targetScale = ScaleExitTo,
                animationSpec = tweenExit(DurationExitFast)
            )
    }

    /** Nav: tab switch — high frequency → short fade only. */
    fun tabEnter(reducedMotion: Boolean = false): EnterTransition =
        fadeIn(if (reducedMotion) tween(0) else tweenOut(DurationTab))

    fun tabExit(reducedMotion: Boolean = false): ExitTransition =
        fadeOut(if (reducedMotion) tween(0) else tweenExit(DurationTab.coerceAtMost(80)))

    /** Nav: secondary screens (settings, history, search). */
    fun secondaryEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return fadeIn(tween(0))
        return fadeIn(tweenOut(DurationMedium)) +
            scaleIn(initialScale = 0.98f, animationSpec = tweenOut(DurationMedium))
    }

    fun secondaryExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return fadeOut(tween(0))
        return fadeOut(tweenExit(DurationExitFast)) +
            scaleOut(targetScale = 0.98f, animationSpec = tweenExit(DurationExitFast))
    }

    /** Nav: thread open — occasional; fade + slight scale (no slide; avoids jank with HTML). */
    fun threadEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return fadeIn(tween(0))
        return fadeIn(tweenOut(DurationMedium)) +
            scaleIn(initialScale = 0.97f, animationSpec = tweenOut(DurationMedium))
    }

    fun threadExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return fadeOut(tween(0))
        return fadeOut(tweenExit(DurationExitFast)) +
            scaleOut(targetScale = 0.98f, animationSpec = tweenExit(DurationExitFast))
    }
}

/** True when system animator duration scale is 0 (accessibility reduced motion). */
@Composable
@ReadOnlyComposable
fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (_: Exception) {
        false
    }
}

/**
 * Press scale feedback for clickable surfaces.
 * Pair with [interactionSource] passed into clickable/combinedClickable.
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = MiooMotion.ScalePress,
    enabled: Boolean = true
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = isReducedMotionEnabled()
    val target = if (enabled && pressed && !reduced) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) tween(0) else MiooMotion.tweenOut(MiooMotion.DurationPress),
        label = "pressScale"
    )
    return scale
}

fun Modifier.graphicsPressScale(scale: Float): Modifier = this.graphicsLayer {
    scaleX = scale
    scaleY = scale
}
