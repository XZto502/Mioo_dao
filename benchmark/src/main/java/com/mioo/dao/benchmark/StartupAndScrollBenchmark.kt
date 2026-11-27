package com.mioo.dao.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import android.graphics.Rect
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device reading benchmarks:
 * 1. Cold startup to first board viewport
 * 2. Board flings, interrupted scrolling, thread reading and return to board
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

    @Test
    fun interruptBoardFling() = measureReadingFrames {
        val bounds = visibleList(BOARD_LIST_TAG).visibleBounds
        swipe(bounds, steps = 80)
        Thread.sleep(300)
        repeat(3) { swipe(bounds, steps = 8) }
        swipe(bounds, steps = 12, reverse = true)
        device.waitForIdle()
    }

    @Test
    fun scrollThreadDetail() = measureReadingFrames(setup = { openScrollableThread() }) {
        val bounds = visibleList(DETAIL_LIST_TAG).visibleBounds
        swipe(bounds, steps = 80)
        repeat(2) { swipe(bounds, steps = 10) }
        swipe(bounds, steps = 18, reverse = true)
        device.waitForIdle()
    }

    @Test
    fun returnFromThreadAndScroll() = measureReadingFrames(setup = { openScrollableThread() }) {
        device.pressBack()
        swipe(visibleList(BOARD_LIST_TAG).visibleBounds, steps = 10)
        device.waitForIdle()
    }

    private fun measureReadingFrames(
        setup: MacrobenchmarkScope.() -> Unit = {},
        measure: MacrobenchmarkScope.() -> Unit
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            if (device.hasObject(By.res(DETAIL_LIST_TAG))) device.pressBack()
            waitForBoardContentReady()
            setup()
        },
        measureBlock = measure
    )

    private fun MacrobenchmarkScope.visibleList(tag: String) =
        checkNotNull(device.wait(Until.findObject(By.res(tag).scrollable(true)), 8_000)) {
            "Expected scrollable list: $tag"
        }

    private fun MacrobenchmarkScope.openScrollableThread() {
        val card = (0..4).firstNotNullOfOrNull { attempt ->
            val board = visibleList(BOARD_LIST_TAG)
            val candidate = board.findObjects(By.res("forum_long_thread_card")).firstOrNull {
                it.visibleBounds.height() >= board.visibleBounds.height() / 8
            }
            if (candidate == null && attempt < 4) {
                swipe(board.visibleBounds, steps = 32)
                device.waitForIdle()
            }
            candidate
        } ?: error("No visible thread with at least 20 replies")
        // Use the card's inner padding, outside text links and image actions.
        val bounds = card.visibleBounds
        device.click(bounds.left + bounds.width() / 100, bounds.centerY())
        visibleList(DETAIL_LIST_TAG)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.swipe(bounds: Rect, steps: Int, reverse: Boolean = false) {
        val top = bounds.top + bounds.height() / 4
        val bottom = bounds.bottom - bounds.height() / 4
        val start = if (reverse) top else bottom
        val end = if (reverse) bottom else top
        check(device.swipe(bounds.centerX(), start, bounds.centerX(), end, steps))
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
        val TARGET_PACKAGE = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("miooTargetPackage", "com.mioo.dao").also {
                require(it == "com.mioo.dao" || it == "com.mioo.dao.beta")
            }
        const val DETAIL_LIST_TAG = "thread_reading_list"
        const val BOARD_LIST_TAG = "forum_board_list"
    }
}
