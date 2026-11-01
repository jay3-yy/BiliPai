package com.android.purebilibili.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRootCategoryContentStructureTest {

    @Test
    fun rootCategoryContent_usesStateAndActionHolders() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("internal data class SettingsRootCategoryActions("))
        assertTrue(source.contains("internal data class SettingsRootCategoryState("))
        assertTrue(
            source.contains(
                """
                internal fun SettingsRootCategoryContent(
                    category: SettingsRootCategory,
                    actions: SettingsRootCategoryActions,
                    state: SettingsRootCategoryState
                )
                """.trimIndent()
            )
        )
    }

    @Test
    fun detailEntrySection_usesExplicitFocusAndClearsStaleRequests() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val sectionBlock = source
            .substringAfter("internal fun SettingsDetailEntrySection(")
            .substringBefore("internal fun SettingsRootCategoryContent(")

        assertTrue(sectionBlock.contains("entry.openFocus?.let"))
        assertTrue(sectionBlock.contains("SettingsSearchFocusController.submit(detailFocus.target, detailFocus.focusId)"))
        assertTrue(sectionBlock.contains("?: SettingsSearchFocusController.clear()"))
        assertTrue(sectionBlock.contains("entry.onClick()"))
        assertTrue(sectionBlock.contains("subtitle = entry.value"))
    }

    @Test
    fun broadSettingsEntriesOpenAtPageStartWhileSubtopicEntriesUsePreciseFocus() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("SettingsSearchFocusIds.PLAYBACK_DECODER"))
        assertTrue(source.contains("SettingsSearchFocusIds.BOTTOM_BAR_START"))
        assertTrue(source.contains("SettingsSearchFocusIds.ANIMATION_START"))
        assertTrue(source.contains("SettingsSearchFocusIds.PLAYBACK_COMMENTS"))
        assertTrue(source.contains("SettingsSearchFocusIds.PLAYBACK_FULLSCREEN"))
        assertTrue(source.contains("SettingsSearchFocusIds.PLAYBACK_DEBUG"))
    }

    @Test
    fun feedApiSection_usesNativeSwitchAndSliderItems() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val feedApiBlock = source
            .substringAfter("fun FeedApiSection(")
            .substringBefore("@Composable\nprivate fun FeedDynamicTabVisibilityItem(")

        assertTrue(feedApiBlock.contains("SettingSwitchItem("))
        assertTrue(feedApiBlock.contains("SettingSliderItem("))
        assertTrue(feedApiBlock.contains("SettingsAdaptiveDivider()"))
        assertTrue(feedApiBlock.contains("浏览动态时收起顶部栏"))
        assertTrue(feedApiBlock.contains("dynamicTopBarCollapseOnScroll"))
        assertTrue(feedApiBlock.contains("动态详情图片展示"))
        assertTrue(feedApiBlock.contains("dynamicDetailImageLayout"))
        assertFalse(feedApiBlock.contains("private fun FeedSwitchItem("))
    }

    @Test
    fun mobileSettingsRoot_usesOnlySearchAndDirectCategoryList() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("SettingsHomeSearchEntry("))
        assertTrue(source.contains("SettingsRootCategoryListSection("))
        val homeBlock = source
            .substringAfter("SettingsNavDestination.Home -> {")
            .substringBefore("is SettingsNavDestination.Category -> {")
        assertFalse(homeBlock.contains("SettingsAboutHomeSection("))
        assertTrue(source.contains("MobileSettingsNavLayout("))
    }

    @Test
    fun rootCategoryContent_staggersDetailGroupsWithEntranceSections() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("internal fun SettingsRootCategoryEntranceSection("))
        assertTrue(source.contains("Box(modifier = Modifier.entrance())"))
        val contentBlock = source
            .substringAfter("internal fun SettingsRootCategoryContent(")
            .substringBefore("@Composable\nfun SupportToolsSection(")
        assertTrue(contentBlock.contains("SettingsRootCategoryEntranceSection {"))
        assertTrue(
            contentBlock.indexOf("SettingsRootCategoryEntranceSection {") <
                contentBlock.indexOf("SettingsDetailGroup(title = \"动效\")")
        )
    }

    @Test
    fun rootCategoryContent_usesStableDetailGroupsWithoutSceneShortcutRows() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val contentBlock = source
            .substringAfter("internal fun SettingsRootCategoryContent(")
            .substringBefore("@Composable\nfun SupportToolsSection(")

        assertTrue(contentBlock.contains("val resolvedCategory = canonicalSettingsRootCategory(category)"))
        assertTrue(contentBlock.contains("when (resolvedCategory)"))
        assertTrue(contentBlock.contains("SettingsDetailGroup("))
        assertTrue(contentBlock.contains("SettingsDetailEntrySection("))
        assertFalse(contentBlock.contains("SettingsSceneShortcutSection("))
        assertTrue(contentBlock.contains("SettingsRootCategory.PLAYBACK_QUALITY -> {"))
        assertTrue(contentBlock.contains("SettingsRootCategory.PRIVACY_PERMISSION -> {"))
        assertTrue(contentBlock.contains("SettingsRootCategory.PLUGINS_EXTENSIONS -> {"))
        assertTrue(contentBlock.contains("SettingsRootCategory.SYSTEM_ABOUT -> {"))
    }

    @Test
    fun mobileSettingsRootUsesNav3DestinationInsteadOfLocalDrillDown() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("SettingsNavDestination"))
        assertTrue(source.contains("SettingsRootCategoryContent("))
        assertTrue(source.contains("onCategoryClick"))
        assertFalse(source.contains("SettingsRootDrillDownNavigator("))
        assertFalse(source.contains("activeRootCategoryName"))
    }

    @Test
    fun mobileSettingsRootUsesCategoryRowsWithoutAccordionExpansion() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val sectionBlock = source
            .substringAfter("internal fun SettingsRootCategoryListSection(")
            .substringBefore("@Composable\nprivate fun SettingsRootCategoryRow(")

        assertTrue(sectionBlock.contains("SettingsRootCategoryRow("))
        assertTrue(sectionBlock.contains("onCategoryClick(category)"))
        assertFalse(sectionBlock.contains("AnimatedVisibility("), "Nagram-style root should navigate, not accordion-expand")
        assertFalse(sectionBlock.contains("SettingsRootCategoryContent("), "root rows should not inline detail content")
    }

    @Test
    fun mobileSettingsRootCategorySummariesAreNotTruncated() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")
        val listSection = source
            .substringAfter("internal fun SettingsRootCategoryListSection(")
            .substringBefore("@Composable\nprivate fun SettingsRootCategoryRow(")
        val categoryRow = source
            .substringAfter("private fun SettingsRootCategoryRow(")
            .substringBefore("@Composable\ninternal fun SettingsDetailGroup(")

        assertFalse(listSection.contains("maxLines = 2"))
        assertFalse(categoryRow.contains("maxLines = 2"))
    }

    @Test
    fun mobileSettingsRootUsesNav3ScreensInsteadOfInlineDrillDown() {
        val screenSource = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")
        val categoryScreenExists = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsCategoryScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsCategoryScreen.kt")
        ).any { it.exists() }
        val searchScreenExists = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchScreen.kt")
        ).any { it.exists() }

        assertTrue(categoryScreenExists)
        assertTrue(searchScreenExists)
        assertTrue(screenSource.contains("SettingsNavDestination"))
        assertFalse(screenSource.contains("SettingsRootDrillDownNavigator("))
    }

    @Test
    fun settingsSearchSubmitsResultFocusBeforeOpeningDetailPage() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsSearchScreen.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("SettingsSearchFocusController.submit(result.target, result.focusId)"))
        assertTrue(
            source.indexOf("SettingsSearchFocusController.submit(result.target, result.focusId)") <
                source.indexOf("onSearchResultClick(result)")
        )
    }

    @Test
    fun tabletSettingsShell_usesNavDrivenCategoryRail() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsTabletShell.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsTabletShell.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("SettingsHomeSearchEntry(onClick = onSearchOpen)"))
        assertTrue(source.contains("NavigationDrawerItem("))
        assertTrue(source.contains("text = category.subtitle"))
        assertTrue(source.contains("rightPane()"))
        assertTrue(source.contains("if (selectedCategory == null) emptyDetailPane() else detailPane()"))
        assertTrue(source.contains("text = \"选择设置分类\""))
        assertTrue(source.contains("AppTopBar("))
        assertTrue(source.contains(".padding(horizontal = layoutPolicy.detailPanePaddingDp.dp)"))
    }

    @Test
    fun tabletCategoryRail_skipsSingleEntryHubPages() {
        val shellSource = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsTabletRouteShell.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsTabletRouteShell.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")
        val navigationSource = listOf(
            File("app/src/main/java/com/android/purebilibili/navigation/AppNavigation.kt"),
            File("src/main/java/com/android/purebilibili/navigation/AppNavigation.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(shellSource.contains("resolveSettingsCategoryNavKey(category)"))
        assertFalse(shellSource.contains("BiliPaiNavKey.SettingsCategory(category)"))
        assertTrue(navigationSource.contains("pushNavigation3Key(resolveSettingsCategoryNavKey(category))"))
        assertFalse(navigationSource.contains("resolveSettingsCategoryDirectTargetKey(category)"))
    }

    @Test
    fun appNavigation_wrapsSettingsSubtreeRoutesWithTabletShell() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/navigation/AppNavigation.kt"),
            File("src/main/java/com/android/purebilibili/navigation/AppNavigation.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("fun SettingsTabletEntry(content: @Composable () -> Unit)"))
        assertTrue(source.contains("SettingsTabletNavEntryShell("))
        assertTrue(source.contains("forceSinglePaneContent = true"))
        listOf(
            "BiliPaiNavEntryContentRole.SETTINGS ->",
            "BiliPaiNavEntryContentRole.PLAYBACK_SETTINGS ->",
            "BiliPaiNavEntryContentRole.PLUGINS_SETTINGS ->",
            "BiliPaiNavEntryContentRole.TIPS_SETTINGS ->",
        ).forEach { marker ->
            val block = source.substringAfter(marker)
            assertTrue(
                block.contains("SettingsTabletEntry {"),
                "Expected SettingsTabletEntry wrapper after $marker"
            )
        }
    }

    @Test
    fun tipsSettingsScreen_usesSharedPageScaffold() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/TipsSettingsScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/TipsSettingsScreen.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("SettingsPageScaffold("))
        assertFalse(source.contains("SettingsLargeTitleHeader("))
        assertFalse(source.contains("AppScaffold("))
    }

    @Test
    fun aboutSupport_keepsReleaseChannelBelowAboutDetailsWithoutDuplicateAuthorCard() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val aboutBlock = source
            .substringAfter("SettingsRootCategory.SYSTEM_ABOUT -> {")
            .substringBefore("@Composable\nfun SupportToolsSection(")

        assertTrue(aboutBlock.indexOf("AboutSection(") < aboutBlock.indexOf("ReleaseChannelPinnedCard("))
        assertFalse(aboutBlock.contains("FollowAuthorSection("))
    }

    @Test
    fun aboutSectionShowsProjectOverviewAndStaticDevelopersBeforeRows() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val aboutSectionBlock = source
            .substringAfter("fun AboutSection(")
            .substringBefore("@Composable\nprivate fun AboutProjectOverviewCard(")

        assertTrue(aboutSectionBlock.contains("AboutProjectOverviewCard(versionName = versionName)"))
        assertTrue(aboutSectionBlock.indexOf("AboutProjectOverviewCard(") < aboutSectionBlock.indexOf("SettingsCardGroup {"))
        assertTrue(source.contains("internal val AboutDevelopers = listOf("))
        assertTrue(source.contains("AboutDeveloper(\"jay3-yy\", \"jay3-yy\", R.drawable.avatar_jay3_yy)"))
        assertTrue(source.contains("name = \"Piracola\""))
        assertTrue(source.contains("githubLogin = \"Piracola\""))
        assertTrue(source.contains("AboutDeveloper(\"Chenx Dust\""))
        assertTrue(source.contains("AboutDeveloper(\"usontong\""))
        assertTrue(source.contains("AboutDeveloper(\"Leko\", \"lekoOwO\", R.drawable.avatar_lekoowo)"))
        assertTrue(source.contains("AboutDeveloper(\"qyo123oyq\", \"qyo123oyq\", R.drawable.avatar_qyo123oyq)"))
        assertTrue(source.contains("name = \"maxzrb\""))
        assertTrue(source.contains("githubLogin = \"maxzrb\""))
        assertTrue(source.contains("name = \"xiaoniao427\""))
        assertTrue(source.contains("name = \"zensu357\""))
        assertTrue(source.contains("name = \"Kurarion\""))
    }

    @Test
    fun aboutDevelopers_useGithubProfilesAndClickableAvatars() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val developerBlock = source
            .substringAfter("internal data class AboutDeveloper(")
            .substringBefore("// 名单依据仓库的 GitHub 贡献记录维护")
        val overviewBlock = source
            .substringAfter("private fun AboutProjectOverviewCard(")
            .substringBefore("@Composable\nprivate fun AboutDeveloperItem(")
        val itemBlock = source
            .substringAfter("private fun AboutDeveloperItem(")

        assertTrue(developerBlock.contains("val githubLogin: String"))
        assertTrue(developerBlock.contains("val avatarResId: Int?"))
        assertTrue(developerBlock.contains("\"https://github.com/${'$'}githubLogin\""))
        assertTrue(developerBlock.contains("val avatarUrl: String?"))
        assertTrue(source.contains("https://avatars.githubusercontent.com/u/114979598?v=4"))
        assertTrue(overviewBlock.contains("text = \"开发者\""))
        assertFalse(overviewBlock.contains("其他贡献者"))
        assertFalse(overviewBlock.contains("顺手"))
        assertTrue(overviewBlock.contains("remember { AboutSlogans.random() }"))
        assertTrue(source.contains("删繁留简见初心"))
        assertTrue(source.contains("广告退场方显净"))
        assertTrue(source.contains("弱水三千凡君取"))
        assertFalse(source.contains("主题:"))
        assertTrue(overviewBlock.contains("FlowRow("))
        assertFalse(overviewBlock.contains("horizontalScroll("))
        assertTrue(itemBlock.contains("LocalUriHandler.current"))
        assertTrue(itemBlock.contains("uriHandler.openUri(contributor.profileUrl)"))
        assertTrue(itemBlock.contains("Box("))
        assertTrue(itemBlock.contains("AsyncImage("))
        assertTrue(itemBlock.contains("model = avatarUrl"))
        assertTrue(itemBlock.contains("painterResource(id = avatarResId)"))
        assertTrue(itemBlock.contains("contentScale = ContentScale.Crop"))
    }

    @Test
    fun settingsSubpages_useNagramStyleSectionNames() {
        val settingsSections = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")
        val appearance = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/AppearanceSettingsScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/AppearanceSettingsScreen.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")
        val playback = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/PlaybackSettingsScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/PlaybackSettingsScreen.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")
        val animation = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/AnimationSettingsScreen.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/AnimationSettingsScreen.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(settingsSections.contains("SettingsDetailGroup(title = \"显示与交互\")"))
        assertTrue(settingsSections.contains("SettingsDetailGroup(title = \"画质与播放\")"))
        assertTrue(settingsSections.contains("SettingsDetailGroup(title = \"隐私与安全\")"))
        // 外观与动画页的分组标题已走字符串资源,断言标题资源接线而非字面量
        assertTrue(appearance.contains("R.string.appearance_section_interface_effects"))
        assertTrue(appearance.contains("R.string.appearance_section_text_size"))
        assertTrue(appearance.contains("R.string.appearance_section_splash_icon"))
        assertTrue(playback.contains("AppPreferenceSectionTitle(\"小窗与后台\")"))
        assertTrue(playback.contains("AppPreferenceSectionTitle(\"诊断\")"))
        assertTrue(
            playback.indexOf("AppPreferenceSectionTitle(\"网络与画质\")") <
                playback.indexOf("AppPreferenceSectionTitle(\"互动与评论\")")
        )
        assertTrue(animation.contains("R.string.animation_glass_section_title"))
        assertTrue(animation.contains("R.string.animation_title_haptic"))
        assertFalse(animation.contains("AppPreferenceSectionTitle(\"底栏入口\")"))
    }

    @Test
    fun aboutProjectOverview_usesRasterLauncherAssetForComposePainter() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val overviewBlock = source
            .substringAfter("private fun AboutProjectOverviewCard(")
            .substringBefore("@Composable\nprivate fun ContributorAvatarRow(")

        assertFalse(overviewBlock.contains("R.mipmap.ic_launcher_bilipai)"))
        assertTrue(overviewBlock.contains("R.mipmap.ic_launcher_bilipai_foreground"))
    }

    @Test
    fun aboutSection_doesNotRenderDuplicateReleaseChannelDisclaimerEntry() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val aboutSectionBlock = source
            .substringAfter("fun AboutSection(")
            .substringBefore("@Composable\nfun CheckUpdateSection(")

        assertFalse(aboutSectionBlock.contains("title = \"发布渠道声明\""))
        assertFalse(aboutSectionBlock.contains("SettingsSearchTarget.DISCLAIMER"))
    }

    @Test
    fun tabletSettingsCategoryDrawer_showsSubtitle() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsTabletShell.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/screen/SettingsTabletShell.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(source.contains("text = category.subtitle"))
    }

    @Test
    fun categoryEntries_useShortPlaybackNavigationCopy() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")
        val copySource = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/SettingsDestinationCopy.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/SettingsDestinationCopy.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        assertTrue(copySource.contains("title = \"首页与推荐\""))
        assertTrue(copySource.contains("title = \"全屏与手势\""))
        assertTrue(source.contains("value = \"出现黑屏、卡顿或画质切换失败时用于排查问题\""))
    }

    @Test
    fun releaseChannelPinnedCard_keepsActionsInOneLine() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"),
            File("src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt")
        ).first { it.exists() }.readText().replace("\r\n", "\n")

        val pinnedCardBlock = source
            .substringAfter("fun ReleaseChannelPinnedCard(")
            .substringBefore("@Composable\nfun FeedApiSection(")

        assertTrue(pinnedCardBlock.contains("modifier = Modifier.fillMaxWidth()"))
        assertTrue(pinnedCardBlock.contains("modifier = Modifier.weight(1f)"))
        assertTrue(pinnedCardBlock.contains("softWrap = false"))
        assertTrue(pinnedCardBlock.contains("maxLines = 1"))
    }
}
