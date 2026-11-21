package com.android.purebilibili.baselineprofile

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.StartupMode.WARM
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiliPaiVideoDetailFrameTimingBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun videoDetailContentScroll_compilationPartial() = benchmarkVideoDetailContentScroll(
        compilationMode = CompilationMode.Partial()
    )

    @Test
    fun videoDetailContentScroll_compilationFull() = benchmarkVideoDetailContentScroll(
        compilationMode = CompilationMode.Full()
    )

    @Test
    fun playerSwipeGesture_compilationPartial() = benchmarkPlayerSwipeGesture(
        compilationMode = CompilationMode.Partial()
    )

    @Test
    fun playerSwipeGesture_compilationFull() = benchmarkPlayerSwipeGesture(
        compilationMode = CompilationMode.Full()
    )

    @Test
    fun videoDetailDirectOpen_compilationPartial() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = FRAME_TIMING_BENCHMARK_ITERATIONS,
        startupMode = WARM,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait { intent ->
            intent.setClassName(TARGET_PACKAGE_NAME, "com.android.purebilibili.feature.video.VideoActivity")
            intent.putExtra("bvid", resolveBenchmarkBvid())
        }
        requireNotNull(device.wait(Until.findObject(By.desc("返回")), UI_WAIT_TIMEOUT_MS))
    }

    @Test
    fun videoCardOpen_compilationPartial() = benchmarkVideoCardTransition(
        closeAction = null,
    )

    @Test
    fun videoCardTopBack_compilationPartial() = benchmarkVideoCardTransition(
        closeAction = VideoCardCloseAction.TopBack,
    )

    @Test
    fun videoCardSystemBack_compilationPartial() = benchmarkVideoCardTransition(
        closeAction = VideoCardCloseAction.SystemBack,
    )

    @Test
    fun videoCardPredictiveBackComplete_compilationPartial() = benchmarkVideoCardTransition(
        closeAction = VideoCardCloseAction.PredictiveComplete,
    )

    @Test
    fun videoCardPredictiveBackCancel_compilationPartial() = benchmarkVideoCardTransition(
        closeAction = VideoCardCloseAction.PredictiveCancel,
    )

    /** Run with click-to-play enabled; skips instead of silently measuring the wrong state. */
    @Test
    fun videoCardSettledPlaying_compilationPartial() = benchmarkVideoCardSettledState(
        expectedControlDescription = "暂停",
    )

    /** Run with click-to-play disabled; the cover owns the transition and no player frame runs. */
    @Test
    fun videoCardSettledNeverPlayed_compilationPartial() = benchmarkVideoCardSettledState(
        expectedControlDescription = "播放",
    )

    /** Starts from active playback, pauses it, then measures the retained last-frame steady state. */
    @Test
    fun videoCardSettledPausedAfterPlayback_compilationPartial() = benchmarkVideoCardSettledState(
        expectedControlDescription = "暂停",
        pauseBeforeMeasure = true,
    )

    /** Settled + playing, then top-bar back — primary jank path for this perf track. */
    @Test
    fun videoCardSettledPlayingThenTopBack_compilationPartial() =
        benchmarkVideoCardSettledPlayingReturn(closeAction = VideoCardCloseAction.TopBack)

    @Test
    fun videoCardSettledPlayingThenSystemBack_compilationPartial() =
        benchmarkVideoCardSettledPlayingReturn(closeAction = VideoCardCloseAction.SystemBack)

    @Test
    fun videoCardSettledPlayingThenPredictiveComplete_compilationPartial() =
        benchmarkVideoCardSettledPlayingReturn(closeAction = VideoCardCloseAction.PredictiveComplete)

    @Test
    fun videoCardEightRoundTrips_compilationPartial() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = FRAME_TIMING_BENCHMARK_ITERATIONS,
            startupMode = WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForHomeVideoCard()
            },
        ) {
            repeat(8) {
                openFirstHomeVideoCard()
                device.pressBack()
                waitForHomeVideoCard()
            }
        }

    private fun benchmarkVideoDetailContentScroll(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = FRAME_TIMING_BENCHMARK_ITERATIONS,
            startupMode = WARM,
            setupBlock = {
                pressHome()
                startVideoDetailActivity()
                waitForVideoDetailReady()
            }
        ) {
            repeat(3) {
                swipeDetailContent(up = true)
                swipeDetailContent(up = false)
            }
        }

    private fun benchmarkPlayerSwipeGesture(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = FRAME_TIMING_BENCHMARK_ITERATIONS,
            startupMode = WARM,
            setupBlock = {
                pressHome()
                startVideoDetailActivity()
                waitForVideoDetailReady()
            }
        ) {
            repeat(3) {
                swipePlayerSeek(forward = true)
                swipePlayerSeek(forward = false)
                swipePlayerVertical(up = true)
                swipePlayerVertical(up = false)
            }
        }

    private fun benchmarkVideoCardTransition(closeAction: VideoCardCloseAction?) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = FRAME_TIMING_BENCHMARK_ITERATIONS,
            startupMode = WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForHomeVideoCard()
                if (closeAction != null) {
                    openFirstHomeVideoCard()
                }
            }
        ) {
            when (closeAction) {
                null -> openFirstHomeVideoCard()
                VideoCardCloseAction.TopBack -> {
                    requireNotNull(
                        device.wait(Until.findObject(By.desc("返回")), UI_WAIT_TIMEOUT_MS)
                    ).click()
                    waitForHomeVideoCard()
                }
                VideoCardCloseAction.SystemBack -> {
                    device.pressBack()
                    waitForHomeVideoCard()
                }
                VideoCardCloseAction.PredictiveComplete -> {
                    performPredictiveBackGesture(commit = true)
                    waitForHomeVideoCard()
                }
                VideoCardCloseAction.PredictiveCancel -> {
                    performPredictiveBackGesture(commit = false)
                    requireNotNull(
                        device.wait(Until.findObject(By.desc("返回")), UI_WAIT_TIMEOUT_MS)
                    )
                    device.waitForIdle()
                }
            }
        }

    /**
     * Measure frame timing for: open card → wait until playing → idle → return home.
     * Requires click-to-play / autoplay so the pause control appears (live morph path).
     */
    private fun benchmarkVideoCardSettledPlayingReturn(
        closeAction: VideoCardCloseAction,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = FRAME_TIMING_BENCHMARK_ITERATIONS,
        startupMode = WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForHomeVideoCard()
        },
    ) {
        openFirstHomeVideoCard()
        waitUntilSettledPlaying()
        SystemClock.sleep(SETTLED_PLAYING_IDLE_BEFORE_RETURN_MS)
        when (closeAction) {
            VideoCardCloseAction.TopBack -> {
                requireNotNull(
                    device.wait(Until.findObject(By.desc("返回")), UI_WAIT_TIMEOUT_MS)
                ).click()
            }
            VideoCardCloseAction.SystemBack -> device.pressBack()
            VideoCardCloseAction.PredictiveComplete ->
                performPredictiveBackGesture(commit = true)
            VideoCardCloseAction.PredictiveCancel ->
                error("Settled-playing-return benchmark only measures committed returns")
        }
        waitForHomeVideoCard()
    }

    private fun MacrobenchmarkScope.waitUntilSettledPlaying() {
        revealPlayerControls()
        val pauseControl = device.wait(
            Until.findObject(By.desc("暂停")),
            PLAYER_CONTROL_WAIT_TIMEOUT_MS,
        )
        assumeTrue(
            "Configure playback-on-open so settled playing path is available",
            pauseControl != null,
        )
        device.waitForIdle()
        SystemClock.sleep(SETTLED_PLAYING_READY_EXTRA_MS)
    }

    private fun benchmarkVideoCardSettledState(
        expectedControlDescription: String,
        pauseBeforeMeasure: Boolean = false,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = FRAME_TIMING_BENCHMARK_ITERATIONS,
        startupMode = WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForHomeVideoCard()
            openFirstHomeVideoCard()
            revealPlayerControls()
            val expectedControl = device.wait(
                Until.findObject(By.desc(expectedControlDescription)),
                PLAYER_CONTROL_WAIT_TIMEOUT_MS,
            )
            assumeTrue(
                "Configure the app playback-on-open setting for $expectedControlDescription",
                expectedControl != null,
            )
            if (pauseBeforeMeasure) {
                requireNotNull(expectedControl).click()
                requireNotNull(
                    device.wait(
                        Until.findObject(By.desc("播放")),
                        PLAYER_CONTROL_WAIT_TIMEOUT_MS,
                    )
                )
            }
            device.waitForIdle()
        },
    ) {
        // No transition visual should emit a counter delta in this SettledHidden interval.
        SystemClock.sleep(SETTLED_STATE_SAMPLE_MS)
    }

    private fun MacrobenchmarkScope.startVideoDetailActivity() {
        val benchmarkBvid = resolveBenchmarkBvid()
        val component = "$TARGET_PACKAGE_NAME/.feature.video.VideoActivity"
        device.executeShellCommand("am start -W -n $component --es bvid $benchmarkBvid")
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitForVideoDetailReady() {
        device.wait(Until.findObject(By.textContains("评论")), 8_000)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitForHomeVideoCard() {
        requireNotNull(
            device.wait(
                Until.findObject(By.descStartsWith(HOME_VIDEO_CARD_DESCRIPTION_PREFIX)),
                UI_WAIT_TIMEOUT_MS,
            )
        )
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openFirstHomeVideoCard() {
        requireNotNull(
            device.wait(
                Until.findObject(By.descStartsWith(HOME_VIDEO_CARD_DESCRIPTION_PREFIX)),
                UI_WAIT_TIMEOUT_MS,
            )
        ).click()
        requireNotNull(
            device.wait(Until.findObject(By.desc("返回")), UI_WAIT_TIMEOUT_MS)
        )
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.performPredictiveBackGesture(commit: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val y = device.displayHeight * 0.55f
        val edgeX = 1f
        val peakX = device.displayWidth * if (commit) 0.72f else 0.28f
        val downTime = SystemClock.uptimeMillis()

        fun inject(action: Int, x: Float) {
            val eventTime = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                x,
                y,
                0,
            ).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            automation.injectInputEvent(event, true)
            event.recycle()
            SystemClock.sleep(PREDICTIVE_BACK_STEP_DELAY_MS)
        }

        inject(MotionEvent.ACTION_DOWN, edgeX)
        repeat(PREDICTIVE_BACK_GESTURE_STEPS) { index ->
            val fraction = (index + 1f) / PREDICTIVE_BACK_GESTURE_STEPS
            inject(MotionEvent.ACTION_MOVE, edgeX + (peakX - edgeX) * fraction)
        }
        if (!commit) {
            repeat(PREDICTIVE_BACK_GESTURE_STEPS) { index ->
                val fraction = (index + 1f) / PREDICTIVE_BACK_GESTURE_STEPS
                inject(MotionEvent.ACTION_MOVE, peakX + (edgeX - peakX) * fraction)
            }
        }
        inject(MotionEvent.ACTION_UP, if (commit) peakX else edgeX)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.revealPlayerControls() {
        if (
            device.hasObject(By.desc("播放")) ||
            device.hasObject(By.desc("暂停"))
        ) {
            return
        }
        device.click(device.displayWidth / 2, (device.displayHeight * 18) / 100)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.swipeDetailContent(up: Boolean) {
        val x = device.displayWidth / 2
        val fromY = if (up) (device.displayHeight * 88) / 100 else (device.displayHeight * 45) / 100
        val toY = if (up) (device.displayHeight * 45) / 100 else (device.displayHeight * 88) / 100
        device.swipe(x, fromY, x, toY, 24)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.swipePlayerSeek(forward: Boolean) {
        val y = (device.displayHeight * 22) / 100
        val fromX = if (forward) (device.displayWidth * 22) / 100 else (device.displayWidth * 78) / 100
        val toX = if (forward) (device.displayWidth * 78) / 100 else (device.displayWidth * 22) / 100
        device.swipe(fromX, y, toX, y, 24)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.swipePlayerVertical(up: Boolean) {
        val x = (device.displayWidth * 80) / 100
        val fromY = if (up) (device.displayHeight * 30) / 100 else (device.displayHeight * 14) / 100
        val toY = if (up) (device.displayHeight * 14) / 100 else (device.displayHeight * 30) / 100
        device.swipe(x, fromY, x, toY, 24)
        device.waitForIdle()
    }

    private fun resolveBenchmarkBvid(): String {
        val args = InstrumentationRegistry.getArguments()
        val configured = args.getString("benchmark.bvid").orEmpty().trim()
        return if (configured.isNotBlank()) configured else DEFAULT_BENCHMARK_BVID
    }

    private enum class VideoCardCloseAction {
        TopBack,
        SystemBack,
        PredictiveComplete,
        PredictiveCancel,
    }

    private companion object {
        const val HOME_VIDEO_CARD_DESCRIPTION_PREFIX = "视频标题:"
        const val UI_WAIT_TIMEOUT_MS = 8_000L
        const val PLAYER_CONTROL_WAIT_TIMEOUT_MS = 2_000L
        const val SETTLED_STATE_SAMPLE_MS = 2_500L
        /** Extra settle after pause control is visible before measuring return. */
        const val SETTLED_PLAYING_READY_EXTRA_MS = 1_500L
        /** Brief idle on settled playing detail before committing return. */
        const val SETTLED_PLAYING_IDLE_BEFORE_RETURN_MS = 300L
        const val PREDICTIVE_BACK_GESTURE_STEPS = 12
        const val PREDICTIVE_BACK_STEP_DELAY_MS = 8L
    }
}
