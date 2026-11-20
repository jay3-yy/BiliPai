package com.android.purebilibili.feature.settings

import com.android.purebilibili.navigation3.BiliPaiNavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsItemSearchPolicyTest {
    @Test
    fun animationKeywordsLeadToSpecificSettings() {
        val entries = mapOf(
            "蓝雪女仆" to "animation.maid_lottie_animation_enabled",
            "渐进模糊" to "animation.progressive_top_blur_enabled",
            "顶栏磨砂" to "animation.header_blur_enabled",
            "容器形变" to "animation.app_navigation_settings.settings_container_morph_enabled",
            "页面切换动画" to "animation.page_transition_style",
            "返回预览幅度" to "animation.back_preview_progress",
            "缩放退出方向" to "animation.back_exit_direction",
            "磨砂强度" to "animation.blur_intensity",
        )
        entries.forEach { (query, settingId) ->
            val result = resolveSettingsSearchResults(query).first()
            assertEquals(settingId, result.settingId, query)
            assertEquals(BiliPaiNavKey.AnimationSettings, resolveSettingsSearchNavigation(result))
        }
    }

    @Test
    fun splashAnimationSearchLeadsToItsSwitchInAppearance() {
        listOf("开屏图标动画", "启动动画").forEach { query ->
            val result = resolveSettingsSearchResults(query).first()
            assertEquals("appearance.splash_icon_animation_enabled", result.settingId)
            assertEquals("appearance_splash_icon", result.focusId)
            assertEquals(BiliPaiNavKey.AppearanceSettings, resolveSettingsSearchNavigation(result))
        }
    }

    @Test
    fun textCopySearchOpensTextSettingsAndKeepsItsStableId() {
        val result = resolveSettingsSearchResults("点按文字复制").first()
        assertEquals("animation.global_text_tap_copy_enabled", result.settingId)
        assertEquals(SettingsSearchTarget.APPEARANCE, result.target)
        assertEquals("appearance_text_size", result.focusId)
        assertEquals("设置 / 界面外观 / 文字与大小", result.path)
        assertEquals(BiliPaiNavKey.AppearanceSettings, resolveSettingsSearchNavigation(result))
    }

    @Test
    fun liquidGlassSearchOpensTheIndependentAnimationPage() {
        val result = resolveSettingsSearchResults("液态玻璃").first()
        assertEquals("appearance.effective_liquid_glass_enabled", result.settingId)
        assertEquals("设置 / 动效与反馈 / 液态玻璃与磨砂", result.path)
        assertEquals(BiliPaiNavKey.AnimationSettings, resolveSettingsSearchNavigation(result))
    }

    @Test
    fun directoryIdsAreUniqueAndHaveConcreteDestinations() {
        assertEquals(playbackSettingDirectory.size, playbackSettingDirectory.map { it.settingId }.toSet().size)
        playbackSettingDirectory.forEach { item ->
            val result = resolveSettingsSearchResults(item.title, Int.MAX_VALUE).first { it.settingId == item.settingId }
            assertEquals(item.title, result.title)
            assertEquals(item.sectionKey, result.focusId)
            assertEquals(BiliPaiNavKey.PlaybackSettings, resolveSettingsSearchNavigation(result))
        }
    }

    @Test
    fun performanceEntriesNavigateToTheVisibleHelpCategory() {
        val entries = listOf(
            "性能专项测试" to com.android.purebilibili.core.performance.PERFORMANCE_CAPTURE_SETTING_ID,
            "查看性能摘要" to com.android.purebilibili.core.performance.PERFORMANCE_PREVIEW_SETTING_ID,
        )
        entries.forEach { (query, id) ->
            val result = resolveSettingsSearchResults(query).first { it.settingId == id }
            assertEquals(SettingsRootCategory.SYSTEM_ABOUT, result.page)
            assertEquals(SettingsRootCategory.SYSTEM_ABOUT, canonicalSettingsRootCategory(result.page!!))
            assertEquals(BiliPaiNavKey.SettingsCategory(SettingsRootCategory.SYSTEM_ABOUT), resolveSettingsSearchNavigation(result))
            assertEquals("设置 / 帮助与关于 / 性能排查", result.path)
        }
    }

    @Test
    fun autoplayKeepsMultipleOptionsOnTheSamePage() {
        val results = resolveSettingsSearchResults("自动播放", Int.MAX_VALUE)
        assertTrue(results.any { it.settingId == "playback.click_to_play_enabled" })
        assertTrue(results.any { it.settingId == "playback.auto_play_enabled" })
        assertEquals(results.size, results.map { it.settingId }.toSet().size)
    }

    @Test
    fun exactTitleWinsAndNaturalSentenceKeepsItsItemId() {
        assertEquals("playback.video_note_enabled", resolveSettingsSearchResults("显示视频笔记").first().settingId)
        assertEquals("playback.video_note_default_collapsed", resolveSettingsSearchResults("怎么关闭默认折叠视频笔记").first().settingId)
    }

    @Test
    fun renamedTechnicalAndChineseTermsRemainSearchable() {
        assertEquals("playback.image_preview3d_page_enabled", resolveSettingsSearchResults("图片立体翻页3D").first().settingId)
        assertTrue(resolveSettingsSearchResults("SHA-256").isNotEmpty())
        assertTrue(resolveSettingsSearchResults("sha256").isNotEmpty())
        assertTrue(resolveSettingsSearchResults("CDN").isNotEmpty())
        assertTrue(resolveSettingsSearchResults("zimu").isNotEmpty())
    }

    @Test
    fun hardwareDecoderOpensItsIndependentPage() {
        val result = resolveSettingsSearchResults("启用硬件解码").first()
        assertEquals(SettingsRootCategory.VIDEO_DECODER, result.page)
        assertEquals("设置 / 播放设置 / 视频解码", result.path)
    }

    @Test
    fun focusClearingCannotDiscardANewerSelection() {
        SettingsSearchFocusController.submit(SettingsSearchTarget.PLAYBACK, SettingsSearchFocusIds.PLAYBACK_SPEED, "first")
        val first = SettingsSearchFocusController.request.value!!
        SettingsSearchFocusController.submit(SettingsSearchTarget.PLAYBACK, SettingsSearchFocusIds.PLAYBACK_FULLSCREEN, "second")
        SettingsSearchFocusController.clear(first.token)
        assertEquals("second", SettingsSearchFocusController.request.value?.settingId)
        SettingsSearchFocusController.clear()
    }
    @Test
    fun partResumeSearchOpensTheSameSectionAsItsVisibleSwitch() {
        listOf("定位分P", "上次看到", "显示继续播放分P提示").forEach { query ->
            val result = resolveSettingsSearchResults(query, Int.MAX_VALUE)
                .first { it.settingId == "playback.show_continue_part_notice" }
            assertEquals(SettingsRootCategory.PLAYBACK_QUALITY, result.page)
            assertEquals(SettingsSearchFocusIds.PLAYBACK_INTERACTION, result.focusId)
            assertTrue(result.path.endsWith("字幕与连播"))
        }
    }

}
