package com.mioo.dao.ui.screens.forum

import kotlin.math.abs

/** Axis lock for the board drawer; vertical scrolling never becomes a drawer drag. */
internal class BoardSwipeIntent(private val slop: Float) {
    enum class Result { WAIT, VERTICAL, OPEN }
    private var x = 0f
    private var y = 0f

    fun add(dx: Float, dy: Float): Result {
        x += dx
        y += dy
        return when {
            abs(y) > slop && abs(y) >= abs(x) -> Result.VERTICAL
            x < -slop -> Result.VERTICAL // A leftward gesture cannot open the drawer.
            x > slop * 1.8f && x > abs(y) * 1.8f -> Result.OPEN
            else -> Result.WAIT
        }
    }
}
