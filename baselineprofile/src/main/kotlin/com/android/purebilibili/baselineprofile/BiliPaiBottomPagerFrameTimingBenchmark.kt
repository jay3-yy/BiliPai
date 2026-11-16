package com.android.purebilibili.baselineprofile

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

@RunWith(AndroidJUnit4::class)
class BiliPaiBottomPagerFrameTimingBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun bottomPagerSwitch_compilationPartial() = benchmarkBottomPagerSwitch(
        compilationMode = CompilationMode.Partial()
    )

    @Test
    fun bottomPagerSwitch_compilationFull() = benchmarkBottomPagerSwitch(
        compilationMode = CompilationMode.Full()
    )

    private fun benchmarkBottomPagerSwitch(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = FRAME_TIMING_BENCHMARK_ITERATIONS,
            startupMode = WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForIdle()
                clickBottomTab("首页")
            }
        ) {
            repeat(2) {
                clickBottomTab("动态")
                clickBottomTab("历史")
                clickBottomTab("我的")
                clickBottomTab("首页")
            }
        }

    private fun MacrobenchmarkScope.clickBottomTab(label: String) {
        val tab = device.wait(Until.findObject(By.desc(label)), 2_000)
            ?: device.wait(Until.findObject(By.text(label)), 2_000)
            ?: error("未找到底栏栏目：$label，切页场景未完成")
        tab.click()
        device.waitForIdle()
    }
}
