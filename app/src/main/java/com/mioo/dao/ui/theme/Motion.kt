package com.mioo.dao.ui.theme

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.content.Context

/**
 * Motion tokens.
 *
 * Page navigation matches the platform activity transition: the top page
 * slides the full width (open from the right, close back to the right) on a
 * short [FastOutSlowInEasing] tween. The covered page stays still — a second
 * full-screen spring was what dropped frames on cold start.
 */
object MiooMotion {
    /**
     * Soft ease-out for opacity/scale — cubic-bezier(0.16, 1, 0.3, 1).
     * Long settle, almost no “hit”; pairs with short chrome motion.
     */
    val EaseOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Balanced morph for on-screen changes. */
    val EaseInOut: Easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

    /** Drawer family (kept for call sites). */
    val EaseDrawer: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    // Durations for tween-based chrome (press, chips, modals, tabs)
    const val DurationPress = 160
    const val DurationTooltip = 220
    const val DurationSmall = 300
    const val DurationMedium = 340
    const val DurationModal = 360
    const val DurationExitFast = 260
    const val DurationShimmer = 1100

    /** Near-1 scale so enter never “pops”; silkier than aggressive 0.95. */
    const val ScaleEnterFrom = 0.98f
    const val ScaleExitTo = 0.99f
    const val ScalePress = 0.97f
    const val ScaleCardPress = 0.985f

    /**
     * Platform-like activity transition length.
     * Short enough that cold-start list composition can wait until it ends.
     */
    const val PageDurationMillis = 250

    /** Fixed tween. Springs kept both heavy pages invalidating for 400ms+. */
    fun <T> pageTween(): TweenSpec<T> = tween(
        durationMillis = PageDurationMillis,
        easing = FastOutSlowInEasing
    )

    /** Slightly snappier spring for small chrome (still no bounce). */
    fun <T> softSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f
    )

    fun <T> tweenOut(durationMillis: Int = DurationSmall): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = EaseOut)

    fun <T> tweenExit(durationMillis: Int = DurationExitFast): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = EaseOut)

    // --- Small chrome ---

    fun softEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return fadeIn(tween(0))
        return fadeIn(tweenOut(DurationSmall)) +
            scaleIn(
                initialScale = ScaleEnterFrom,
                animationSpec = softSpring()
            )
    }

    fun softExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return fadeOut(tween(0))
        return fadeOut(tweenExit(DurationExitFast)) +
            scaleOut(
                targetScale = ScaleExitTo,
                animationSpec = softSpring()
            )
    }

    fun softExpandEnter(reducedMotion: Boolean = false): EnterTransition =
        softEnter(reducedMotion)

    fun softExpandExit(reducedMotion: Boolean = false): ExitTransition =
        softExit(reducedMotion)

    fun modalEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return fadeIn(tween(0))
        return fadeIn(tweenOut(DurationModal)) +
            scaleIn(
                initialScale = ScaleEnterFrom,
                animationSpec = softSpring()
            )
    }

    fun modalExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return fadeOut(tween(0))
        return fadeOut(tweenExit(DurationExitFast)) +
            scaleOut(
                targetScale = ScaleExitTo,
                animationSpec = softSpring()
            )
    }

    /**
     * Forward: new page enters from the right, moving left. No fade.
     * Set targetContentZIndex to 1 so this page draws above the one it covers.
     */
    fun pageEnter(reducedMotion: Boolean = false): EnterTransition {
        if (reducedMotion) return EnterTransition.None
        return slideInHorizontally(
            animationSpec = pageTween(),
            initialOffsetX = { full -> full }
        )
    }

    /**
     * Back: top page leaves to the right. Same path and duration as [pageEnter].
     */
    fun pagePopExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return ExitTransition.None
        return slideOutHorizontally(
            animationSpec = pageTween(),
            targetOffsetX = { full -> full }
        )
    }

    /**
     * Covered page stays fully opaque and is not translated.
     * [ExitTransition.None] removes it on the first frame and leaves a hole.
     * Alpha stays at 1 until the slide ends, so the forum list is not moved
     * every frame (that parallax layer was the cold-start hitch).
     */
    fun pageHoldExit(reducedMotion: Boolean = false): ExitTransition {
        if (reducedMotion) return ExitTransition.None
        return fadeOut(animationSpec = snap(delayMillis = PageDurationMillis))
    }

    /** Page revealed by a pop. Already underneath; no fade layer. */
    fun pageRevealEnter(reducedMotion: Boolean = false): EnterTransition {
        return if (reducedMotion) EnterTransition.None else EnterTransition.None
    }

    fun tabEnter(reducedMotion: Boolean = false): EnterTransition = pageEnter(reducedMotion)

    fun tabExit(reducedMotion: Boolean = false): ExitTransition = pageHoldExit(reducedMotion)

    fun pushEnter(reducedMotion: Boolean = false): EnterTransition = pageEnter(reducedMotion)

    fun pushPopExit(reducedMotion: Boolean = false): ExitTransition = pagePopExit(reducedMotion)

    fun pushSourceExit(reducedMotion: Boolean = false): ExitTransition = pageHoldExit(reducedMotion)

    fun pushSourcePopEnter(reducedMotion: Boolean = false): EnterTransition =
        pageRevealEnter(reducedMotion)

    fun secondaryEnter(reducedMotion: Boolean = false): EnterTransition =
        pushEnter(reducedMotion)

    fun secondaryExit(reducedMotion: Boolean = false): ExitTransition =
        pushPopExit(reducedMotion)

    fun threadEnter(reducedMotion: Boolean = false): EnterTransition =
        pushEnter(reducedMotion)

    fun threadExit(reducedMotion: Boolean = false): ExitTransition =
        pushPopExit(reducedMotion)
}

fun isReducedMotionEnabled(context: Context): Boolean {
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

@Composable
@ReadOnlyComposable
fun isReducedMotionEnabled(): Boolean = isReducedMotionEnabled(LocalContext.current)

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
        // Spring press feels silkier under quick taps (retargets mid-flight)
        animationSpec = if (reduced) {
            tween(0)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "pressScale"
    )
    return scale
}

fun Modifier.graphicsPressScale(scale: Float): Modifier = this.graphicsLayer {
    scaleX = scale
    scaleY = scale
}
