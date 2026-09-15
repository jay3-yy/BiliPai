package com.android.purebilibili.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchPolicyTest {

    @Test
    fun blankQuery_returnsEmptyList() {
        val results = resolveSettingsSearchResults("   ")

        assertTrue(results.isEmpty())
    }

    @Test
    fun queryByChineseKeyword_hitsExpectedSetting() {
        val results = resolveSettingsSearchResults("缓存")

        assertTrue(results.any { it.target == SettingsSearchTarget.CLEAR_CACHE })
    }

    @Test
    fun queryByMessageNotificationTitleOrAlias_hitsMessageNotificationSetting() {
        val byTitle = resolveSettingsSearchResults("消息通知")
        val byAlias = resolveSettingsSearchResults("后台消息")

        assertEquals(
            SettingsSearchTarget.MESSAGE_NOTIFICATION,
            byTitle.firstOrNull()?.target
        )
        assertEquals(
            SettingsSearchTarget.MESSAGE_NOTIFICATION,
            byAlias.firstOrNull()?.target
        )
    }

    @Test
    fun naturalLanguageQueryContainingSettingName_hitsExpectedSetting() {
        val results = resolveSettingsSearchResults("怎么清除应用缓存释放空间")

        assertEquals(SettingsSearchTarget.CLEAR_CACHE, results.firstOrNull()?.target)
    }

    @Test
    fun queryByEnglishAlias_isCaseInsensitive() {
        val results = resolveSettingsSearchResults("gItHuB")

        assertTrue(results.any { it.target == SettingsSearchTarget.OPEN_SOURCE_HOME })
    }

    @Test
    fun prefixMatch_ranksBeforeGenericContains() {
        val results = resolveSettingsSearchResults("检查")

        assertEquals(SettingsSearchTarget.CHECK_UPDATE, results.firstOrNull()?.target)
    }

    @Test
    fun limit_isRespected() {
        val results = resolveSettingsSearchResults("设", maxResults = 3)

        assertEquals(3, results.size)
    }

    @Test
    fun queryByShareKeyword_hitsSettingsShareEntry() {
        val results = resolveSettingsSearchResults("导入")

        assertTrue(results.any { it.target == SettingsSearchTarget.SETTINGS_SHARE })
    }

    @Test
    fun queryByGlassKeyword_hitsAppearanceEntry() {
        val results = resolveSettingsSearchResults("玻璃")

        assertTrue(results.any { it.target == SettingsSearchTarget.APPEARANCE })
    }

    @Test
    fun queryByUpBadgeKeyword_hitsHomeEntry() {
        val results = resolveSettingsSearchResults("UP主标识")

        assertTrue(results.any { it.target == SettingsSearchTarget.HOME_FEED })
    }

    @Test
    fun queryByHomeFeedCardWidth_hitsHomeEntry() {
        val results = resolveSettingsSearchResults("推荐流卡片宽度")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.HOME_FEED &&
                    it.focusId == SettingsSearchFocusIds.HOME_OVERVIEW
            }
        )
    }

    @Test
    fun queryByRetiredHomeGlassBadges_returnsNoSettingsResult() {
        val results = resolveSettingsSearchResults("封面玻璃样式") +
            resolveSettingsSearchResults("信息区玻璃样式")

        assertTrue(
            results.none {
                it.target == SettingsSearchTarget.HOME_FEED &&
                    it.focusId == SettingsSearchFocusIds.HOME_OVERVIEW
            }
        )
    }

    @Test
    fun queryByMd3Alias_hitsAppearanceEntry() {
        val results = resolveSettingsSearchResults("md3")

        assertTrue(results.any { it.target == SettingsSearchTarget.APPEARANCE })
    }

    @Test
    fun queryByCustomMd3Color_focusesAppearanceThemeSection() {
        val results = resolveSettingsSearchResults("自定义md3颜色")

        assertEquals("自定义主题颜色", results.firstOrNull()?.title)
        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.APPEARANCE &&
                    it.focusId == SettingsSearchFocusIds.APPEARANCE_THEME
            }
        )
    }

    @Test
    fun queryByAndroidNativeLiquidGlass_focusesAppearanceThemeSection() {
        val result = resolveSettingsSearchResults("安卓原生液态玻璃").firstOrNull()

        assertEquals(SettingsSearchTarget.APPEARANCE, result?.target)
        assertEquals(SettingsSearchFocusIds.APPEARANCE_THEME, result?.focusId)
    }

    @Test
    fun queryByClearBottomBarGlass_noLongerFocusesBottomBarPreset() {
        val results = resolveSettingsSearchResults("通透玻璃")

        assertTrue(
            results.none {
                it.target == SettingsSearchTarget.ANIMATION &&
                    it.focusId == SettingsSearchFocusIds.ANIMATION_VISUAL_EFFECTS
            }
        )
    }

    @Test
    fun queryByBottomBarLiquidGlass_focusesGlobalAppearanceEntry() {
        val result = resolveSettingsSearchResults("底栏液态玻璃").firstOrNull()

        assertEquals(SettingsSearchTarget.APPEARANCE, result?.target)
        assertEquals(SettingsSearchFocusIds.APPEARANCE_THEME, result?.focusId)
    }

    @Test
    fun queryByTopDockLiquidGlass_focusesGlobalAppearanceEntry() {
        val result = resolveSettingsSearchResults("顶部dock栏液态玻璃").firstOrNull()

        assertEquals(SettingsSearchTarget.APPEARANCE, result?.target)
        assertEquals(SettingsSearchFocusIds.APPEARANCE_THEME, result?.focusId)
    }

    @Test
    fun queryByHomeSearchLiquidGlass_focusesGlobalAppearanceEntry() {
        val result = resolveSettingsSearchResults("首页搜索框液态玻璃").firstOrNull()

        assertEquals(SettingsSearchTarget.APPEARANCE, result?.target)
        assertEquals(SettingsSearchFocusIds.APPEARANCE_THEME, result?.focusId)
    }

    @Test
    fun queryByOldBackdropNativeName_returnsNoSettingsResult() {
        val legacyQuery = listOf("Back", "drop", " 原生").joinToString("")
        val results = resolveSettingsSearchResults(legacyQuery)

        assertTrue(results.none { it.target == SettingsSearchTarget.ANIMATION })
    }

    @Test
    fun queryByPinyin_hitsChineseAlias() {
        val results = resolveSettingsSearchResults("waiguan")

        assertTrue(results.any { it.target == SettingsSearchTarget.APPEARANCE })
    }

    @Test
    fun queryByRemovedBackPreview_noLongerHitsSettingsEntry() {
        val results = resolveSettingsSearchResults("预测性返回")

        assertTrue(results.none { it.target == SettingsSearchTarget.ANIMATION })
    }

    @Test
    fun queryByRemovedBackPreviewAlias_noLongerHitsSettingsEntry() {
        val results = resolveSettingsSearchResults("预测性返回预览")

        assertTrue(results.none { it.target == SettingsSearchTarget.ANIMATION })
    }

    @Test
    fun queryByPictureInPicture_hitsPlaybackEntry() {
        val results = resolveSettingsSearchResults("画中画")

        assertTrue(results.any { it.target == SettingsSearchTarget.PLAYBACK })
    }

    @Test
    fun queryByPlayedVideoLocatePrompt_hitsPlaybackEntry() {
        val results = resolveSettingsSearchResults("刚刚看过")

        assertTrue(results.any { it.target == SettingsSearchTarget.PLAYBACK })
    }

    @Test
    fun queryByAttentionDanmaku_hitsPlaybackInteractionEntry() {
        val results = resolveSettingsSearchResults("关注点赞弹幕")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_INTERACTION
            }
        )
    }

    @Test
    fun queryByDisableEntryAutoplay_hitsPlaybackInteractionEntry() {
        val results = resolveSettingsSearchResults("进入视频不要自动播放")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_INTERACTION
            }
        )
    }

    @Test
    fun queryByVideoInfoDefaultExpanded_hitsPlaybackInteractionEntry() {
        val results = resolveSettingsSearchResults("默认展开视频简介")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_INTERACTION
            }
        )
    }

    @Test
    fun queryByVideoNote_hitsPlaybackInteractionEntry() {
        val results = resolveSettingsSearchResults("默认折叠视频笔记")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_INTERACTION
            }
        )
    }

    @Test
    fun queryBySubReply_hitsPlaybackFullscreenEntry() {
        val results = resolveSettingsSearchResults("楼中楼")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
            }
        )
    }

    @Test
    fun queryByDoubleTapSeek_hitsPlaybackInteractionEntry() {
        val results = resolveSettingsSearchResults("取消双击跳转")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_INTERACTION
            }
        )
    }

    @Test
    fun queryByAutoRotate_hitsPlaybackEntry() {
        val results = resolveSettingsSearchResults("自动横竖屏")

        assertTrue(results.any { it.target == SettingsSearchTarget.PLAYBACK })
    }

    @Test
    fun queryByHideVideoPageStatusBar_hitsPlaybackFullscreenEntry() {
        val results = resolveSettingsSearchResults("播放页隐藏状态栏")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
            }
        )
    }

    @Test
    fun queryByTabletCommentPanelWidth_hitsPlaybackFullscreenEntry() {
        val results = resolveSettingsSearchResults("平板评论区宽度")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
            }
        )
    }

    @Test
    fun queryByCommentFraudDetection_hitsPlaybackFullscreenEntry() {
        val results = resolveSettingsSearchResults("发评反诈")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
            }
        )
    }

    @Test
    fun queryByCommentDecorations_hitsPlaybackFullscreenEntry() {
        val results = resolveSettingsSearchResults("个性装扮")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
            }
        )
    }

    @Test
    fun queryByCommentCollapsedPreviewLimit_hitsPlaybackFullscreenEntry() {
        val results = resolveSettingsSearchResults("评论折叠数量")

        assertTrue(
            results.any {
                it.target == SettingsSearchTarget.PLAYBACK &&
                    it.focusId == SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
            }
        )
    }

    @Test
    fun queryByImagePreviewLongPressSave_hitsPlaybackEntry() {
        val results = resolveSettingsSearchResults("图片长按保存")

        assertTrue(results.any { it.target == SettingsSearchTarget.PLAYBACK })
    }

    @Test
    fun queryByImageSaveLocation_hitsImageSavePathEntry() {
        val results = resolveSettingsSearchResults("图片保存位置")

        assertTrue(results.any { it.target == SettingsSearchTarget.IMAGE_SAVE_PATH })
    }

    @Test
    fun queryByAppScreenshotGesture_hitsPlaybackEntry() {
        val results = resolveSettingsSearchResults("应用内干净截图")

        assertTrue(results.any { it.target == SettingsSearchTarget.FULLSCREEN_GESTURE })
    }

    @Test
    fun queryByRegionScreenshot_hitsPlaybackEntry() {
        val results = resolveSettingsSearchResults("手选区域")

        assertTrue(results.any { it.target == SettingsSearchTarget.FULLSCREEN_GESTURE })
    }

    @Test
    fun queryByQualityDowngradeDialog_hitsPlaybackEntry() {
        val results = resolveSettingsSearchResults("仅弹窗一次")

        assertTrue(results.any { it.target == SettingsSearchTarget.DIAGNOSTICS })
    }

    @Test
    fun sceneQueries_hitDedicatedRootEntries() {
        assertTrue(resolveSettingsSearchResults("顶部标签").any { it.target == SettingsSearchTarget.NAVIGATION })
        assertTrue(resolveSettingsSearchResults("首页壁纸").any { it.target == SettingsSearchTarget.HOME_FEED })
        assertTrue(resolveSettingsSearchResults("评论装扮").any { it.target == SettingsSearchTarget.INTERACTION_COMMENT })
        assertTrue(resolveSettingsSearchResults("WebDAV").any { it.target == SettingsSearchTarget.DATA_BACKUP })
    }

    @Test
    fun queryByAutoCheckUpdate_hitsCheckUpdateEntry() {
        val results = resolveSettingsSearchResults("自动检查更新")

        assertTrue(results.any { it.target == SettingsSearchTarget.CHECK_UPDATE })
    }

    @Test
    fun queryByBottomBar_hitsBottomBarSettingsEntry() {
        assertTrue(resolveSettingsSearchResults("底栏").any { it.target == SettingsSearchTarget.BOTTOM_BAR })
    }

    @Test
    fun queryByHomeTopRightMessage_hitsTopTabManagementEntry() {
        val first = resolveSettingsSearchResults("首页右上角消息").firstOrNull()

        assertEquals(SettingsSearchTarget.BOTTOM_BAR, first?.target)
        assertEquals(SettingsSearchFocusIds.BOTTOM_BAR_TOP_TABS, first?.focusId)
    }

    @Test
    fun queryBySidebarNavigation_hitsNavigationSettingsEntry() {
        val result = resolveSettingsSearchResults("侧边导航栏").firstOrNull {
            it.target == SettingsSearchTarget.BOTTOM_BAR &&
                it.focusId == SettingsSearchFocusIds.BOTTOM_BAR_TABLET
        }

        assertEquals("平板侧边导航栏", result?.title)
        assertEquals("导航设置", result?.section)
    }
}
