package com.mioo.dao.ui.components

import android.widget.OverScroller
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CancellationException

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
fun rememberResponsiveListFling(): FlingBehavior {
    val context = LocalContext.current
    val overScroller = remember(context) { OverScroller(context) }
    return remember(overScroller) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (abs(initialVelocity) < 50f) return initialVelocity
                overScroller.forceFinished(true)
                overScroller.fling(
                    0, 0,
                    0, initialVelocity.toInt(),
                    0, 0,
                    Int.MIN_VALUE, Int.MAX_VALUE
                )
                var lastY = 0
                try {
                    while (overScroller.computeScrollOffset()) {
                        val currentY = overScroller.currY
                        val delta = (currentY - lastY).toFloat()
                        lastY = currentY
                        if (delta != 0f) {
                            val consumed = scrollBy(delta)
                            if (abs(consumed - delta) > 0.5f) {
                                val remainingVelocity = overScroller.currVelocity * sign(initialVelocity)
                                overScroller.forceFinished(true)
                                return remainingVelocity
                            }
                        }
                        withFrameNanos { }
                    }
                    return 0f
                } catch (e: CancellationException) {
                    overScroller.forceFinished(true)
                    throw e
                }
            }
        }
    }
}

@Composable
fun ReadingTouchGuard(content: @Composable () -> Unit) {
    val configuration = rememberTouchGuardConfiguration(LocalScrollTouchGuardEnabled.current)
    androidx.compose.runtime.CompositionLocalProvider(LocalViewConfiguration provides configuration, content = content)
}
