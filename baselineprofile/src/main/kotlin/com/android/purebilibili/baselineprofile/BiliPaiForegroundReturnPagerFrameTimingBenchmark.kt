package com.android.purebilibili.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode.WARM
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「挂后台 → 切回前台 → 立刻切底栏」的帧耗时基线。
 *
 * 这是 [BiliPaiBottomPagerFrameTimingBenchmark] 覆盖不到的路径：后者在 WARM 启动后直接切页，
 * 永远不会触发回前台的缓存收敛、主题重算与封面重解码。而用户报的掉帧恰恰只在这个窗口里
 * 出现——所以必须单独锁一条基线，否则针对它的改动没有回归护栏。
 *
 * 两个停留时长对应两条不同的代码路径：
 * - 短后台（3s）：触发切后台的即时缓存预算收敛，但不到播放器重度后台优化门槛。
 * - 长时间后台（50s）：覆盖后台音频/视频链路优化后的重建与回前台恢复。
 */
@RunWith(AndroidJUnit4::class)
class BiliPaiForegroundReturnPagerFrameTimingBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun foregroundReturnPagerSwitch_shortBackground_compilationPartial() =
        benchmarkForegroundReturnPagerSwitch(
            compilationMode = CompilationMode.Partial(),
            backgroundDwellMillis = SHORT_BACKGROUND_DWELL_MS,
            iterations = FRAME_TIMING_BENCHMARK_ITERATIONS
        )

    @Test
    fun foregroundReturnPagerSwitch_shortBackground_compilationFull() =
        benchmarkForegroundReturnPagerSwitch(
            compilationMode = CompilationMode.Full(),
            backgroundDwellMillis = SHORT_BACKGROUND_DWELL_MS,
            iterations = FRAME_TIMING_BENCHMARK_ITERATIONS
        )

    @Test
    fun foregroundReturnPagerSwitch_sustainedBackground_compilationPartial() =
        benchmarkForegroundReturnPagerSwitch(
            compilationMode = CompilationMode.Partial(),
            backgroundDwellMillis = SUSTAINED_BACKGROUND_DWELL_MS,
            // 每次迭代都要真实等待 50s，故降低迭代数以控制总时长。
            iterations = SUSTAINED_BACKGROUND_BENCHMARK_ITERATIONS
        )

    private fun benchmarkForegroundReturnPagerSwitch(
        compilationMode: CompilationMode,
        backgroundDwellMillis: Long,
        iterations: Int
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = iterations,
        startupMode = WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            clickBottomTab("首页")
        }
    ) {
        // 真实场景：挂后台一段时间再切回，回前台后立刻连点底栏。
        pressHome()
        device.waitForIdle()
        SystemClock.sleep(backgroundDwellMillis)
        startActivityAndWait()
        device.waitForIdle()

        repeat(2) {
            clickBottomTab("动态")
            clickBottomTab("历史")
            clickBottomTab("我的")
            clickBottomTab("首页")
        }
    }

    private fun MacrobenchmarkScope.clickBottomTab(label: String) {
        val byDesc = device.wait(Until.findObject(By.desc(label)), 2_000)
        if (byDesc != null) {
            byDesc.click()
            device.waitForIdle()
            return
        }

        val byText = device.wait(Until.findObject(By.text(label)), 2_000)
        if (byText != null) {
            byText.click()
            device.waitForIdle()
        }
    }

    private companion object {
        /** 触发切后台预算收敛与回前台安定窗口，但不跨过 15s 的播放器优化门槛。 */
        const val SHORT_BACKGROUND_DWELL_MS = 3_000L

        /** 覆盖后台播放器优化与缓存回收后的回前台恢复。 */
        const val SUSTAINED_BACKGROUND_DWELL_MS = 50_000L

        const val SUSTAINED_BACKGROUND_BENCHMARK_ITERATIONS = 4
    }
}
