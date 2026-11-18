package com.android.purebilibili.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.purebilibili.feature.agreement.UserAgreementStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {

    private val packageName = "com.android.purebilibili"

    private fun runShell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
    }

    private fun prepareHomeEntry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        UserAgreementStore.accept(context)
    }

    private fun resolveDisplaySize(): Pair<Int, Int> {
        val output = runShell("wm size")
        val match = Regex("(\\d+)x(\\d+)").find(output)
        val width = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1080
        val height = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 2400
        return width to height
    }

    @Test
    fun homeSwipeScenario_recordsFrameAndPss() {
        prepareHomeEntry()
        runShell("input keyevent 3")
        runShell("am start -W -n $packageName/.MainActivity")
        Thread.sleep(10_000)

        runShell("dumpsys gfxinfo $packageName reset")
        val (width, height) = resolveDisplaySize()
        val x = width / 2
        val yStart = height * 7 / 10
        val yEnd = height * 3 / 10

        repeat(20) {
            runShell("input swipe $x $yStart $x $yEnd 220")
            Thread.sleep(100)
        }

        Thread.sleep(2_000)

        val gfx = runShell("dumpsys gfxinfo $packageName")
        val mem = runShell("dumpsys meminfo $packageName")

        val totalFrames = Regex("Total frames rendered:\\s*(\\d+)").find(gfx)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val jankPercent = Regex("Janky frames:\\s*\\d+\\s*\\(([0-9.]+)%\\)").find(gfx)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
        val pssKb = Regex("TOTAL PSS:\\s*([0-9,]+)").find(mem)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0

        assertTrue("Total frames should be >= 300 for valid scenario. frames=$totalFrames", totalFrames >= 300)
        assertTrue("Jank percent should be parsable. jank=$jankPercent", jankPercent >= 0f)
        assertTrue("TOTAL PSS should be parsable. pss=$pssKb", pssKb > 0)
    }
}
