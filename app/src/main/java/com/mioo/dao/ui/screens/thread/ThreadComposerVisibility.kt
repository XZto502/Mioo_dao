package com.mioo.dao.ui.screens.thread

internal data class ThreadEndObservation(
    val count: Int,
    val lastBottomDistance: Int?,
    val composerHeight: Int,
    val userScrollingUp: Boolean
)

internal fun shouldRevealThreadComposer(
    wasNearEnd: Boolean,
    observation: ThreadEndObservation,
    bufferPx: Float
): Boolean {
    if (observation.count == 0) return false
    val enterDistance = observation.composerHeight + bufferPx
    val distance = observation.lastBottomDistance
    if (distance != null && distance <= enterDistance) return true
    // Viewport shrink can move the last row off screen. Keep the reveal latched until
    // the reader scrolls back away; the wider exit threshold prevents animation chatter.
    return wasNearEnd && (!observation.userScrollingUp ||
        (distance != null && distance <= enterDistance + observation.composerHeight + bufferPx))
}
