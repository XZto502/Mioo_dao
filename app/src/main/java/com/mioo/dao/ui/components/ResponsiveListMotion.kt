package com.mioo.dao.ui.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import kotlin.math.abs

@Composable
fun rememberResponsiveListConfiguration(): ViewConfiguration {
    val original = LocalViewConfiguration.current
    return remember(original) {
        object : ViewConfiguration by original {
            override val touchSlop: Float get() = original.touchSlop * 0.65f
        }
    }
}

@Composable
fun rememberResponsiveListFling(): FlingBehavior {
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 0.52f) }
    return remember(decay) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val velocity = if (abs(initialVelocity) > 1200f)
                    (initialVelocity * 1.15f).coerceIn(-16000f, 16000f) else initialVelocity
                var previous = 0f
                var remainingVelocity = velocity
                AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(decay) {
                    val delta = value - previous
                    val consumed = scrollBy(delta)
                    previous = value
                    remainingVelocity = this.velocity
                    if (abs(delta - consumed) > 0.5f) cancelAnimation()
                }
                return remainingVelocity
            }
        }
    }
}
