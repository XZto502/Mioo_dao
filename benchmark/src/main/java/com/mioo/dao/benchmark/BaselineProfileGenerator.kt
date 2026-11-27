package com.mioo.dao.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates an ART Baseline Profile covering cold startup and board list scrolling.
 *
 * Run on a physical (or root/userdebug) device:
 *   ./gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "com.mioo.dao",
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.res("forum_board_list")), 8_000)
            device.waitForIdle()

            val boardList = device.findObject(By.res("forum_board_list"))
            if (boardList != null) {
                boardList.setGestureMargin(device.displayWidth / 5)
                boardList.fling(Direction.DOWN)
                device.waitForIdle()
                boardList.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }
}
