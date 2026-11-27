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
            waitForBoardContentReady()
        }
    ) {
        repeat(3) {
            swipeBoardList(scrollDownContent = true)
        }
        repeat(2) {
            swipeBoardList(scrollDownContent = false)
        }
    }

    private fun MacrobenchmarkScope.swipeBoardList(scrollDownContent: Boolean) {
        val boardList = device.wait(Until.findObject(By.res(BOARD_LIST_TAG)), 3_000)
        checkNotNull(boardList) { "Board LazyColumn ($BOARD_LIST_TAG) not found" }
        val bounds = boardList.visibleBounds
        val centerX = bounds.centerX()
        val topY = bounds.top + (bounds.height() * 0.22f).toInt()
        val bottomY = bounds.top + (bounds.height() * 0.80f).toInt()
        // Finger UP (bottomY -> topY) scrolls content down; finger DOWN (topY -> bottomY) scrolls content up.
        if (scrollDownContent) {
            device.swipe(centerX, bottomY, centerX, topY, 10)
        } else {
            device.swipe(centerX, topY, centerX, bottomY, 12)
        }
        Thread.sleep(650)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitForBoardReady() {
        device.wait(Until.hasObject(By.res(BOARD_LIST_TAG)), 8_000)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitForBoardContentReady() {
        waitForBoardReady()
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            dismissFirstLaunchDialogsIfPresent()
            val list = device.findObject(By.res(BOARD_LIST_TAG))
            if (list != null && device.hasObject(By.textContains("No."))) {
                device.waitForIdle()
                return
            }
            Thread.sleep(250)
        }
        val finalCount = device.findObject(By.res(BOARD_LIST_TAG))?.childCount ?: -1
        error("Board list ($BOARD_LIST_TAG) did not load thread cards within 15s (childCount=$finalCount)")
    }

    private fun MacrobenchmarkScope.dismissFirstLaunchDialogsIfPresent() {
        // Prefer declining third-party update checks during benchmarks so GitHub popups don't interrupt.
        val dialogButtons = listOf("拒绝第三方服务", "同意并继续", "忽略")
        for (label in dialogButtons) {
            val button = device.wait(Until.findObject(By.text(label)), 400)
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
