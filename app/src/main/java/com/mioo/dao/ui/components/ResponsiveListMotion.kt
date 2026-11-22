package com.mioo.dao.ui.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import kotlin.math.abs
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

val LocalScrollTouchGuardEnabled = staticCompositionLocalOf { false }

/** Use Compose's accumulated touch slop: jitter never starts a drag, while the
 * threshold is subtracted from the first scroll delta so activation cannot jump.
 * No timers, per-move allocations or filtering after a drag has been accepted.
 */
@Composable
fun rememberTouchGuardConfiguration(enabled: Boolean): ViewConfiguration {
    val original = LocalViewConfiguration.current
    return remember(original, enabled) {
        if (!enabled) original else object : ViewConfiguration by original {
            override val touchSlop: Float get() = original.touchSlop * 1.15f
        }
    }
}

@Composable
fun rememberResponsiveListConfiguration(): ViewConfiguration {
    val guarded = LocalScrollTouchGuardEnabled.current
    val original = rememberTouchGuardConfiguration(guarded)
    return remember(original, guarded) {
        // Keep the responsive default while guarding only reading lists.
        if (guarded) original else object : ViewConfiguration by original {
            override val touchSlop: Float get() = original.touchSlop * 0.65f
        }
    }
}

internal fun responsiveFlingVelocityThresholdPx(density: Float, refreshRateHz: Float): Float {
    val safeDensity = density.coerceAtLeast(1f)
    val safeRefresh = refreshRateHz.coerceIn(60f, 144f)
    // LazyListState rounds scroll deltas to integer pixels; below ~1 px/frame
    // frames produce 0-px VSync skips (60Hz/40Hz stepping) at the fling tail.
    return maxOf(12f * safeDensity, safeRefresh * 1.05f)
}

@Composable
fun rememberResponsiveListFling(): ResponsiveListFling {
    val density = LocalDensity.current
    val view = LocalView.current
    val refreshRateHz = remember(view) {
        val display = view.display
        val rate = display?.mode?.refreshRate ?: display?.refreshRate ?: 60f
        if (rate.isFinite() && rate >= 30f) rate else 60f
    }
    val velocityThresholdPx = remember(density.density, refreshRateHz) {
        responsiveFlingVelocityThresholdPx(density.density, refreshRateHz)
    }
    val decay = remember(velocityThresholdPx) {
        exponentialDecay<Float>(
            frictionMultiplier = 0.52f,
            absVelocityThreshold = velocityThresholdPx
        )
    }
    val densityScale = density.density
    return remember(decay, densityScale) {
        ResponsiveListFling(decay, densityScale)
    }
}

class ResponsiveListFling internal constructor(
    private val decay: DecayAnimationSpec<Float>,
    private val density: Float
) : FlingBehavior {
    // Plain fields: updating velocity must not recompose list rows on each frame.
    internal var currentVelocity = 0f
    internal var running = false
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val velocity = scaleResponsiveFlingVelocity(initialVelocity, density)
        if (abs(velocity) <= 1f) return 0f
        var previous = 0f
        var remainingVelocity = velocity
        var hitBoundary = false
        running = true
        currentVelocity = velocity
        try {
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(decay) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            remainingVelocity = this.velocity
            currentVelocity = this.velocity
            if (abs(delta - consumed) > 0.5f) {
                hitBoundary = true
                cancelAnimation()
            }
        }
        return if (hitBoundary) remainingVelocity else 0f
        } finally {
            running = false
            currentVelocity = 0f
        }
    }
}

internal fun scaleResponsiveFlingVelocity(initialVelocity: Float, density: Float): Float {
    val safeDensity = density.coerceAtLeast(1f)
    // Settle very slow positioning releases near the finger without damping quick reverse flicks.
    val speedDp = abs(initialVelocity) / safeDensity
    val blend = ((speedDp - 60f) / (280f - 60f)).coerceIn(0f, 1f)
    val easedBlend = blend * blend * (3f - 2f * blend)
    val momentum = 0.15f + 0.91f * easedBlend
    return (initialVelocity * momentum).coerceIn(-16000f, 16000f)
}

/** A settled-looking fling should not require a separate tap just to stop it. */
fun Modifier.slowFlingClick(fling: ResponsiveListFling, onClick: () -> Unit): Modifier =
    this then SlowFlingClickElement(fling, onClick)

private data class SlowFlingClickElement(
    val fling: ResponsiveListFling,
    val onClick: () -> Unit
) : ModifierNodeElement<SlowFlingClickNode>() {
    override fun create() = SlowFlingClickNode(fling, onClick)

    override fun update(node: SlowFlingClickNode) {
        node.fling = fling
        node.onClick = onClick
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "slowFlingClick"
    }
}

private class SlowFlingClickNode(
    var fling: ResponsiveListFling,
    var onClick: () -> Unit
) : DelegatingNode() {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // Keep fast-fling tap-to-stop and ordinary drag arbitration intact.
                    val allowClick = fling.running && abs(fling.currentVelocity) <= 80.dp.toPx()
                    if (!allowClick) return@awaitEachGesture
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                            event.changes.size > 1) moved = true
                        if (!change.pressed) {
                            if (!moved && !change.isConsumed &&
                                change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                change.consume()
                                onClick()
                            }
                            break
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun ReadingTouchGuard(content: @Composable () -> Unit) {
    val configuration = rememberTouchGuardConfiguration(LocalScrollTouchGuardEnabled.current)
    androidx.compose.runtime.CompositionLocalProvider(LocalViewConfiguration provides configuration, content = content)
}
