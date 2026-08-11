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
 * Motion tokens and helpers:
 * - ease-out for enter/exit UI (never ease-in)
 * - smooth custom curves (not the ultra-snappy "punch" defaults)
 * - enter can sit near ~300–360ms; exit still a bit faster than enter
 * - never enter from scale(0) — start near ~0.96–0.98 + opacity
 * - press feedback ~140–180ms scale(0.97)
 * - respect reduced motion (opacity-only / snap)
 */
object MiooMotion {
    /**
     * Smooth ease-out — cubic-bezier(0.22, 1, 0.36, 1).
     * Slightly gentler first-frame velocity than the old punch curve so longer
     * durations actually read as motion instead of a flash.
     */
    val EaseOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Soft ease-in-out for on-screen morphing — cubic-bezier(0.65, 0, 0.35, 1) */
    val EaseInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** iOS-like drawer curve — cubic-bezier(0.32, 0.72, 0, 1) */
    val EaseDrawer: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    // Durations (ms) — tuned for fluid feel; exit stays shorter than matching enter
    const val DurationPress = 160
    const val DurationTooltip = 200
    const val DurationSmall = 260
    const val DurationMedium = 320
    const val DurationModal = 360
    const val DurationExitFast = 200
    const val DurationTab = 180
    const val DurationSecondaryExit = 240
    const val DurationShimmer = 1100

    /** Initial scale for enter — never 0; keep delta subtle so motion feels calm. */
    const val ScaleEnterFrom = 0.97f
    const val ScaleExitTo = 0.98f
    const val ScalePress = 0.97f
    const val ScaleCardPress = 0.985f
    const val ScaleSecondaryFrom = 0.985f
    const val ScaleThreadFrom = 0.98f

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

    /** Nav: tab switch — still light, but long enough to read as a crossfade. */
    fun tabEnter(reducedMotion: Boolean = false): EnterTransition =
        fadeIn(if (reducedMotion) tween(0) else tweenOut(DurationTab))

    fun tabExit(reducedMotion: Boolean = false): ExitTransition =
        fadeOut(if (reducedMotion) tween(0) else tweenExit((DurationTab * 0.75f).toInt().coerceAtLeast(120)))

    /** Nav: secondary screens (settings, history, search). */
    fun secondaryEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return fadeIn(tween(0))
        return fadeIn(tweenOut(DurationMedium)) +
            scaleIn(initialScale = ScaleSecondaryFrom, animationSpec = tweenOut(DurationMedium))
    }

    fun secondaryExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return fadeOut(tween(0))
        return fadeOut(tweenExit(DurationSecondaryExit)) +
            scaleOut(targetScale = ScaleExitTo, animationSpec = tweenExit(DurationSecondaryExit))
    }

    /**
     * Nav: thread open — frequent in this app (list → detail many times/session).
     * Keep short fade+scale only (no slide; HTML/images must not fight a long transition).
     * Emil: tens+/day → drastically reduce duration; only transform+opacity.
     */
    fun threadEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return fadeIn(tween(0))
        return fadeIn(tweenOut(DurationSmall)) +
            scaleIn(initialScale = ScaleThreadFrom, animationSpec = tweenOut(DurationSmall))
    }

    fun threadExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return fadeOut(tween(0))
        // Exit faster than enter — asymmetric timing feels snappier on back
        return fadeOut(tweenExit(DurationExitFast)) +
            scaleOut(targetScale = ScaleExitTo, animationSpec = tweenExit(DurationExitFast))
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
