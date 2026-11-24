package com.android.purebilibili.baselineprofile

import android.os.Build
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiliPaiBaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE_NAME,
            includeInStartupProfile = false,
            maxIterations = 8
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            returnToMainTabs()

            // Core startup and navigation hotspots.
            cycleMainTabs()

            // Feed-heavy screens for Compose + Haze + list rendering paths.
            repeat(2) {
                clickBottomTab("动态")
                scrollFeedOnce()
                scrollFeedOnce(reverse = true)

                clickBottomTab("首页")
                scrollFeedOnce()

                clickBottomTab("历史")
                scrollFeedOnce()
            }

            // Non-live image-heavy and navigation paths omitted by the original eight-line
            // baseline profile. These flows never select a live result or enter a live route.
            exerciseSearchResults()
            exerciseAppearanceSettings()
            exerciseSpaceFeed()
            exerciseWebSession()

            exerciseHomeCardSharedTransition(exerciseComments = true)
            exerciseWatchLaterAndFavorites()

            startVideoDetailActivity()
            scrollVideoDetailContent()
            swipeVideoPlayerSeek()
            device.pressBack()
            device.waitForIdle()

            clickBottomTab("首页")
        }
    }

    @Test
    fun generateStartupProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE_NAME,
            includeInStartupProfile = true,
            maxIterations = 8
        ) {
            pressHome()
            startActivityAndWait()
            completeFirstLaunchIfNeeded()
            check(
                device.wait(Until.hasObject(By.desc("推荐")), 4_000) ||
                    device.wait(Until.hasObject(By.text("推荐")), 4_000) ||
                    device.wait(Until.hasObject(By.desc("首页")), 1_000) ||
                    device.wait(Until.hasObject(By.text("首页")), 1_000)
            ) {
                "启动后未找到首页导航"
            }
        }
    }

    @Test
    fun material3NativeMotion() = collectMaterialJourney("Google Material 3", glassEnabled = false)

    @Test
    fun miuixNativeMotion() = collectMaterialJourney("Miuix", glassEnabled = false)

    @Test
    fun material3GlassMotion() = collectMaterialJourney("Google Material 3", glassEnabled = true)

    @Test
    fun miuixGlassMotion() = collectMaterialJourney("Miuix", glassEnabled = true)

    private fun collectMaterialJourney(themeLabel: String, glassEnabled: Boolean) {
        // 折射与玻璃控件从 Android 13 开始可用；旧系统仍采集两套原生路径。
        assumeTrue(!glassEnabled || Build.VERSION.SDK_INT >= 33)
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE_NAME,
            includeInStartupProfile = false,
            maxIterations = 8
        ) {
            pressHome()
            startActivityAndWait()
            returnToMainTabs()
            openSettingsDestination("界面外观", "界面外观")
            val styleEntry = device.findObject(By.text("界面风格"))
                ?: device.findObject(By.text("安卓原生风格"))
                ?: findTextWithScroll("界面风格")
            styleEntry.click()
            device.waitForIdle()
            // 弹层和设置行可能同时显示当前选项，选择弹层中的最后一个匹配项。
            requireObject(By.text(themeLabel), "主题选项 $themeLabel")
            device.findObjects(By.text(themeLabel)).last().click()
            device.waitForIdle()
            requireObject(By.text(themeLabel), "已选择的界面风格")
            returnToMainTabs()
            openSettingsDestination("动效与反馈", "动效与反馈")
            if (Build.VERSION.SDK_INT >= 33) {
                setPreferenceSwitch("液态玻璃", glassEnabled)
            }
            setPreferenceSwitch("一镜到底动画", true)
            returnToMainTabs()

            cycleMainTabs()
            clickBottomTab("首页")
            scrollFeedOnce()
            scrollFeedOnce(reverse = true)
            repeat(2) { exerciseHomeCardSharedTransition() }
            clickBottomTab("动态")
            scrollFeedOnce()
            scrollFeedOnce(reverse = true)
            clickBottomTab("首页")
        }
    }

    private fun MacrobenchmarkScope.openSettingsDestination(category: String, destination: String) {
        returnToMainTabs()
        clickBottomTab("我的")
        device.waitForIdle()
        var clickedSettings = false
        repeat(3) {
            if (clickedSettings) return@repeat
            val settingsEntry = device.wait(Until.findObject(By.desc("设置")), 2_000)
                ?: device.wait(Until.findObject(By.desc("Settings")), 2_000)
                ?: device.wait(Until.findObject(By.text("设置")), 1_500)
            if (settingsEntry != null) {
                try {
                    settingsEntry.click()
                    clickedSettings = true
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    device.waitForIdle()
                }
            }
        }
        check(clickedSettings) { "采集缺少必要界面：设置入口" }
        device.waitForIdle()
        // 手机直接进入子页；大屏可能先显示分类或同屏详情。
        if (!device.hasObject(By.text(destination))) {
            findTextWithScroll(category).click()
            device.waitForIdle()
        } else {
            device.findObject(By.text(destination))?.click()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.completeFirstLaunchIfNeeded() {
        device.waitForIdle()
        // 全新测试安装会先展示用户协议（需滑到底解锁“同意并继续”）与首次引导（可点击“跳过”）。
        if (device.hasObject(By.text("请阅读至协议末尾")) ||
            device.hasObject(By.text("同意并继续")) ||
            device.hasObject(By.text("BiliPai 用户协议与隐私政策"))
        ) {
            val centerX = device.displayWidth / 2
            val startY = (device.displayHeight * 0.65f).toInt()
            val endY = (device.displayHeight * 0.25f).toInt()
            repeat(8) {
                val agreeButton = device.findObject(By.text("同意并继续"))
                if (agreeButton != null && agreeButton.isEnabled) {
                    agreeButton.click()
                    device.waitForIdle()
                    return@repeat
                }
                device.swipe(centerX, startY, centerX, endY, 16)
                device.waitForIdle()
            }
            device.findObject(By.text("同意并继续"))?.takeIf { it.isEnabled }?.let {
                it.click()
                device.waitForIdle()
            }
        }
        // 协议通过后若进入首次外观引导，点击右上角“跳过”直达首页。
        val skipButton = device.wait(Until.findObject(By.text("跳过")), 2_000)
        if (skipButton != null) {
            skipButton.click()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.returnToMainTabs() {
        completeFirstLaunchIfNeeded()
        repeat(8) {
            val hasHome = device.hasObject(By.desc("推荐")) ||
                device.hasObject(By.text("推荐")) ||
                device.hasObject(By.desc("首页")) ||
                device.hasObject(By.text("首页"))
            val hasProfile = device.hasObject(By.desc("我的")) || device.hasObject(By.text("我的"))
            if (hasHome && hasProfile) return
            device.pressBack()
            device.waitForIdle()
        }
        error("无法返回主导航，停止采集以免保存不完整规则")
    }

    private fun MacrobenchmarkScope.requireObject(selector: BySelector, purpose: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(selector), 8_000)) {
            "采集缺少必要界面：$purpose"
        }

    private fun MacrobenchmarkScope.findTextWithScroll(text: String): UiObject2 {
        repeat(8) {
            device.findObject(By.text(text))?.let { return it }
            scrollFeedOnce()
        }
        error("未找到设置项：$text")
    }

    private fun MacrobenchmarkScope.preferenceSwitch(title: String): UiObject2 {
        var node: UiObject2? = findTextWithScroll(title)
        repeat(5) {
            val current = node ?: error("设置项缺少可读取状态的开关：$title")
            if (current.isCheckable) return current
            val controls = current.findObjects(By.checkable(true))
            if (controls.size == 1) return controls.single()
            check(controls.isEmpty()) { "设置项的开关不唯一：$title" }
            node = current.parent
        }
        error("设置项缺少可读取状态的开关：$title")
    }

    private fun MacrobenchmarkScope.setPreferenceSwitch(title: String, checked: Boolean) {
        val control = preferenceSwitch(title)
        check(control.isEnabled) { "采集所需开关不可用：$title" }
        if (control.isChecked != checked) {
            control.click()
            device.waitForIdle()
        }
        check(device.wait(Until.findObject(By.text(title)), 4_000) != null) {
            "切换后设置项消失：$title"
        }
        check(preferenceSwitch(title).isChecked == checked) { "开关状态未生效：$title=$checked" }
    }

    private fun MacrobenchmarkScope.cycleMainTabs() {
        clickBottomTab("首页")
        clickBottomTab("动态")
        clickBottomTab("历史")
        clickBottomTab("我的")
        clickBottomTab("首页")
    }

    private fun MacrobenchmarkScope.clickBottomTab(label: String) {
        val candidates = if (label == "首页" || label == "推荐") {
            listOf("推荐", "首页")
        } else {
            listOf(label)
        }
        for (candidate in candidates) {
            val byDesc = device.wait(Until.findObject(By.desc(candidate)), 1_500)
            if (byDesc != null) {
                byDesc.click()
                device.waitForIdle()
                return
            }
            val byText = device.findObjects(By.text(candidate)).lastOrNull()
                ?: device.wait(Until.findObject(By.text(candidate)), 1_500)
            if (byText != null) {
                byText.click()
                device.waitForIdle()
                return
            }
        }
        error("找不到主导航：$label")
    }

    private fun MacrobenchmarkScope.scrollFeedOnce(reverse: Boolean = false) {
        val x = device.displayWidth / 2
        val fromY = if (reverse) device.displayHeight / 3 else (device.displayHeight * 3) / 4
        val toY = if (reverse) (device.displayHeight * 3) / 4 else device.displayHeight / 3
        device.swipe(x, fromY, x, toY, 24)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.exerciseSearchResults() {
        clickBottomTab("首页")
        val search = device.wait(Until.findObject(By.desc("搜索")), 3_000)
            ?: device.wait(Until.findObject(By.textContains("搜索视频")), 3_000)
            ?: return
        search.click()
        device.waitForIdle()
        // SearchScreen requests focus on entry. ASCII keeps shell input deterministic.
        device.executeShellCommand("input text android")
        device.pressEnter()
        SystemClock.sleep(NETWORK_CONTENT_SETTLE_MS)
        repeat(2) { scrollFeedOnce() }
        returnToMainTabs()
    }

    private fun MacrobenchmarkScope.exerciseAppearanceSettings() {
        openSettingsDestination("界面外观", "界面外观")
        repeat(2) {
            scrollFeedOnce()
            scrollFeedOnce(reverse = true)
        }
        returnToMainTabs()
        clickBottomTab("首页")
    }

    private fun MacrobenchmarkScope.exerciseSpaceFeed() {
        startExplicitDeepLink("https://space.bilibili.com/${resolveBenchmarkMid()}")
        SystemClock.sleep(NETWORK_CONTENT_SETTLE_MS)
        repeat(2) { scrollFeedOnce() }
        returnToMainTabs()
    }

    private fun MacrobenchmarkScope.exerciseWebSession() {
        val configuredUrl = InstrumentationRegistry.getArguments()
            .getString("benchmark.webUrl")
            .orEmpty()
            .trim()
            .ifBlank { DEFAULT_BENCHMARK_WEB_URL }
        startExplicitDeepLink(configuredUrl)
        SystemClock.sleep(WEB_CONTENT_SETTLE_MS)
        scrollFeedOnce()
        device.pressBack()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.startExplicitDeepLink(uri: String) {
        val component = "$TARGET_PACKAGE_NAME/.MainActivity"
        device.executeShellCommand(
            "am start -W -n $component -a android.intent.action.VIEW -d ${shellQuote(uri)}"
        )
        device.wait(Until.findObject(By.pkg(TARGET_PACKAGE_NAME)), 8_000)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.startVideoDetailActivity() {
        val component = "$TARGET_PACKAGE_NAME/.feature.video.VideoActivity"
        device.executeShellCommand("am start -W -n $component --es bvid ${resolveBenchmarkBvid()}")
        device.wait(Until.findObject(By.pkg(TARGET_PACKAGE_NAME)), 8_000)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollVideoDetailContent() {
        val x = device.displayWidth / 2
        val fromY = (device.displayHeight * 88) / 100
        val toY = (device.displayHeight * 45) / 100
        device.swipe(x, fromY, x, toY, 24)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.swipeVideoPlayerSeek() {
        val y = (device.displayHeight * 22) / 100
        val fromX = (device.displayWidth * 24) / 100
        val toX = (device.displayWidth * 76) / 100
        device.swipe(fromX, y, toX, y, 24)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.exerciseHomeCardSharedTransition(
        exerciseComments: Boolean = false,
    ) {
        clickBottomTab("首页")
        device.waitForIdle()
        val videoTitle = requireObject(By.descStartsWith("视频标题: "), "首页视频卡片")
        videoTitle.click()
        device.waitForIdle()
        SystemClock.sleep(NETWORK_CONTENT_SETTLE_MS)
        requireObject(By.text("简介"), "卡片展开后的详情内容")
        if (exerciseComments) {
            device.findObject(By.textStartsWith("评论"))?.let { commentsTab ->
                commentsTab.click()
                device.waitForIdle()
                SystemClock.sleep(NETWORK_CONTENT_SETTLE_MS)
                scrollVideoDetailContent()
            }
        }
        device.pressBack()
        device.waitForIdle()
        requireObject(By.descStartsWith("视频标题: "), "返回后的来源视频列表")
    }

    private fun MacrobenchmarkScope.exerciseWatchLaterAndFavorites() {
        clickBottomTab("我的")
        device.waitForIdle()
        listOf("稍后再看", "收藏").forEach { entry ->
            device.findObject(By.text(entry))?.let { target ->
                target.click()
                device.waitForIdle()
                SystemClock.sleep(NETWORK_CONTENT_SETTLE_MS)
                scrollFeedOnce()
                device.pressBack()
                device.waitForIdle()
            }
        }
        returnToMainTabs()
    }

    private fun resolveBenchmarkBvid(): String {
        val configured = InstrumentationRegistry.getArguments()
            .getString("benchmark.bvid")
            .orEmpty()
            .trim()
        return if (configured.isNotBlank()) configured else DEFAULT_BENCHMARK_BVID
    }

    private fun resolveBenchmarkMid(): Long = InstrumentationRegistry.getArguments()
        .getString("benchmark.mid")
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?: DEFAULT_BENCHMARK_MID

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"

    private companion object {
        const val DEFAULT_BENCHMARK_WEB_URL = "https://example.com"
        const val NETWORK_CONTENT_SETTLE_MS = 1_500L
        const val WEB_CONTENT_SETTLE_MS = 1_000L
    }
}
