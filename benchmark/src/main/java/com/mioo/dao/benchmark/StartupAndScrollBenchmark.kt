package com.mioo.dao.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal Layer-3 Macrobenchmark suite:
 * 1. Cold startup to first board viewport
 * 2. Board list fast vertical fling (P50 / P90 / P99 frame timing & overrun)
 *
 * Run on a physical device:
 *   ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StartupAndScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
        waitForBoardReady()
    }

    @Test
    fun scrollBoardList() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            dismissFirstLaunchDialogsIfPresent()
            waitForBoardReady()
        }
    ) {
        repeat(2) {
            flingBoardList(Direction.DOWN)
        }
        repeat(2) {
            flingBoardList(Direction.UP)
        }
    }

    private fun MacrobenchmarkScope.flingBoardList(direction: Direction) {
        val boardList = device.wait(Until.findObject(By.res(BOARD_LIST_TAG)), 3_000)
        if (boardList != null) {
            // Keep fling inside the center 60% width so horizontal drawer arbitration never interferes.
            boardList.setGestureMargin(device.displayWidth / 5)
            boardList.fling(direction)
        } else {
            val centerX = device.displayWidth / 2
            val topY = device.displayHeight / 4
            val bottomY = (device.displayHeight * 3) / 4
            if (direction == Direction.DOWN) {
                device.swipe(centerX, bottomY, centerX, topY, 12)
            } else {
                device.swipe(centerX, topY, centerX, bottomY, 12)
            }
        }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitForBoardReady() {
        device.wait(Until.hasObject(By.res(BOARD_LIST_TAG)), 8_000)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.dismissFirstLaunchDialogsIfPresent() {
        val acceptText = listOf("同意并继续", "仅浏览", "稍后再说", "关闭")
        for (label in acceptText) {
            val button = device.findObject(By.text(label))
            if (button != null) {
                button.click()
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.mioo.dao"
        const val BOARD_LIST_TAG = "forum_board_list"
    }
}
