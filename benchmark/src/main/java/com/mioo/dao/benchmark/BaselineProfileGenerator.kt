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

            for (label in listOf("拒绝第三方服务", "同意并继续", "忽略")) {
                device.wait(Until.findObject(By.text(label)), 1_500)?.let {
                    it.click()
                    device.waitForIdle()
                }
            }

            device.wait(Until.hasObject(By.res("forum_board_list")), 8_000)
            device.waitForIdle()

            device.wait(Until.findObject(By.res("forum_board_list")), 8_000)?.let { list ->
                list.setGestureMargin(device.displayWidth / 5)
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
            device.wait(Until.findObject(By.res("forum_board_list")), 3_000)?.let { list ->
                list.setGestureMargin(device.displayWidth / 5)
                list.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }
}
