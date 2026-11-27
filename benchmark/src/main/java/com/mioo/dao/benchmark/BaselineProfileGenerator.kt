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
                val bounds = list.visibleBounds
                val centerX = bounds.centerX()
                val topY = bounds.top + (bounds.height() * 0.22f).toInt()
                val bottomY = bounds.top + (bounds.height() * 0.80f).toInt()
                repeat(2) {
                    device.swipe(centerX, bottomY, centerX, topY, 10)
                    device.waitForIdle()
                }
                device.swipe(centerX, topY, centerX, bottomY, 12)
                device.waitForIdle()
            }
        }
    }
}
