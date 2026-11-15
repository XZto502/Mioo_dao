package com.mioo.dao.ui.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import kotlin.math.abs
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

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

@Composable
fun rememberResponsiveListFling(): ResponsiveListFling {
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 0.52f) }
    return remember(decay) {
        ResponsiveListFling(decay)
    }
}

class ResponsiveListFling internal constructor(private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    // Plain fields: updating velocity must not recompose list rows on each frame.
    internal var currentVelocity = 0f
    internal var running = false
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val velocity = if (abs(initialVelocity) > 1200f)
            (initialVelocity * 1.15f).coerceIn(-16000f, 16000f) else initialVelocity
        var previous = 0f
        var remainingVelocity = velocity
        running = true
        currentVelocity = velocity
        try {
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(decay) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            remainingVelocity = this.velocity
            currentVelocity = this.velocity
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return if (abs(remainingVelocity) <= 0.1f) 0f else remainingVelocity
        } finally {
            running = false
            currentVelocity = 0f
        }
    }
}

/** A settled-looking fling should not require a separate tap just to stop it. */
fun Modifier.slowFlingClick(fling: ResponsiveListFling, onClick: () -> Unit): Modifier =
    pointerInput(fling, onClick) {
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

@Composable
fun ReadingTouchGuard(content: @Composable () -> Unit) {
    val configuration = rememberTouchGuardConfiguration(LocalScrollTouchGuardEnabled.current)
    androidx.compose.runtime.CompositionLocalProvider(LocalViewConfiguration provides configuration, content = content)
}
