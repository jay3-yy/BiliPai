package com.android.purebilibili.feature.home.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.plugin.skin.InstalledUiSkinPackage
import com.android.purebilibili.core.plugin.skin.UiSkinAssets
import com.android.purebilibili.core.plugin.skin.UiSkinColorTokens
import com.android.purebilibili.core.plugin.skin.UiSkinManifest
import com.android.purebilibili.core.plugin.skin.UiSkinMotionTokens
import com.android.purebilibili.core.plugin.skin.UiSkinState
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BottomBarUiSkinDecorationTest {

    @Test
    fun illustratedNavigationRequiresArtworkAndKeepsTabletNavigation() {
        assertTrue(shouldUseIllustratedSkinBottomBar(true, true, false))
        assertTrue(!shouldUseIllustratedSkinBottomBar(false, true, false))
        assertTrue(!shouldUseIllustratedSkinBottomBar(true, false, false))
        assertTrue(!shouldUseIllustratedSkinBottomBar(true, true, true))
    }

    @Test
    fun illustratedIconsFitNarrowSlotsWithoutCroppingCharacters() {
        assertEquals(52.dp, resolveIllustratedSkinIconSize(80.dp))
        assertEquals(40.dp, resolveIllustratedSkinIconSize(48.dp))
        assertEquals(24.dp, resolveIllustratedSkinIconSize(24.dp))
    }

    @Test
    fun bottomSkinIconSizesMatchScreenshotLevelCharacterAssets() {
        assertEquals(32.dp, resolveBottomBarSkinDockIconSize())
        assertEquals(32.dp, resolveBottomBarMiuixSkinDockIconSize())
        assertEquals(32.dp, resolveBottomBarCompactSkinHomeIconSize())
    }

    @Test
    fun skinIconScalePolicyUsesFitForBalancedAspectAndCropForImbalanced() {
        val balanced = resolveSkinIconScalePolicy(1.0f)
        assertEquals(androidx.compose.ui.layout.ContentScale.Fit, balanced.contentScale)

        val nearSquare = resolveSkinIconScalePolicy(1.2f)
        assertEquals(androidx.compose.ui.layout.ContentScale.Fit, nearSquare.contentScale)

        val wideWithPadding = resolveSkinIconScalePolicy(2.0f)
        assertEquals(androidx.compose.ui.layout.ContentScale.Crop, wideWithPadding.contentScale)

        val tallWithPadding = resolveSkinIconScalePolicy(0.4f)
        assertEquals(androidx.compose.ui.layout.ContentScale.Crop, tallWithPadding.contentScale)

        val invalid = resolveSkinIconScalePolicy(-1f)
        assertEquals(androidx.compose.ui.layout.ContentScale.Fit, invalid.contentScale)
    }

    @Test
    fun bottomSkinDockLayoutKeepsHostGeometryAndLabelVisible() {
        val padding = resolveBottomBarSkinDockContentPadding()

        assertEquals(64.dp, resolveBottomBarSkinDockHeight())
        assertEquals(64.dp, resolveMiuixDockedBottomBarItemHeight(hasUiSkinDecoration = false))
        assertEquals(
            resolveBottomBarSkinDockHeight(),
            resolveMiuixDockedBottomBarItemHeight(hasUiSkinDecoration = true)
        )
        assertEquals(32.dp, resolveBottomBarSkinDockIconSize())
        assertEquals(0.dp, padding.calculateTopPadding())
        assertEquals(0.dp, padding.calculateBottomPadding())
        assertEquals(2.dp, resolveBottomBarSkinIconLabelGap())
        assertEquals(4.dp, resolveBottomBarSkinDockIconTopPadding())
        assertEquals(4.dp, resolveBottomBarSkinDockLabelBottomPadding())
        assertEquals(12.sp, resolveBottomBarSkinDockLabelFontSize())
        assertEquals(18.sp, resolveBottomBarSkinDockLabelLineHeight())
    }

    @Test
    fun activeExternalSkinUsesExtractedBottomTrimImagePath() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.cloud",
                displayName = "云朵底栏",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR),
                assets = UiSkinAssets(bottomBarTrim = "assets/bottom_trim.png")
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/cloud.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf("assets/bottom_trim.png" to "/tmp/bottom_trim.png")
        )

        val decoration = resolveBottomBarUiSkinDecoration(
            UiSkinState(enabled = true, activeSkin = installed)
        )

        assertEquals("dev.example.cloud", decoration?.skinId)
        assertEquals("/tmp/bottom_trim.png", decoration?.bottomTrimImagePath)
    }

    @Test
    fun illustratedBottomBarUsesProvidedSelectedCharacterArtwork() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.illustrated",
                displayName = "插画底栏",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR),
                assets = UiSkinAssets(
                    bottomBarTrim = "assets/tail_bg.png",
                    bottomBarIcons = mapOf(
                        "home" to "assets/tail_icon_main.png",
                        "home_selected" to "assets/tail_icon_selected_main.png",
                    ),
                ),
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/illustrated.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf(
                "assets/tail_bg.png" to "/tmp/tail_bg.png",
                "assets/tail_icon_main.png" to "/tmp/tail_icon_main.png",
                "assets/tail_icon_selected_main.png" to "/tmp/tail_icon_selected_main.png",
            ),
        )
        val decoration = resolveBottomBarUiSkinDecoration(
            UiSkinState(enabled = true, activeSkin = installed)
        )

        assertEquals("/tmp/tail_icon_main.png", decoration?.illustratedIconPathFor(BottomNavItem.HOME, selected = false))
        assertEquals(
            "/tmp/tail_icon_selected_main.png",
            decoration?.illustratedIconPathFor(BottomNavItem.HOME, selected = true),
        )
    }

    @Test
    fun activeExternalSkinKeepsEachBottomDestinationOnItsStableUnselectedArtwork() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.tail-icons",
                displayName = "底栏图标",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR),
                assets = UiSkinAssets(
                    bottomBarIcons = mapOf(
                        "home" to "assets/tail_icon_main.png",
                        "home_selected" to "assets/tail_icon_selected_main.png",
                        "following" to "assets/tail_icon_dynamic.png",
                        "following_selected" to "assets/tail_icon_selected_dynamic.png",
                        "member" to "assets/tail_icon_shop.png",
                        "member_selected" to "assets/tail_icon_selected_shop.png",
                        "channel" to "assets/tail_icon_channel.png",
                        "channel_selected" to "assets/tail_icon_selected_channel.png",
                        "profile" to "assets/tail_icon_myself.png"
                    ),
                    homeChannelIcon = "assets/tail_icon_channel.png",
                    homeChannelSelectedIcon = "assets/tail_icon_selected_channel.png"
                )
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/tail-icons.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf(
                "assets/tail_icon_main.png" to "/tmp/tail_icon_main.png",
                "assets/tail_icon_selected_main.png" to "/tmp/tail_icon_selected_main.png",
                "assets/tail_icon_dynamic.png" to "/tmp/tail_icon_dynamic.png",
                "assets/tail_icon_selected_dynamic.png" to "/tmp/tail_icon_selected_dynamic.png",
                "assets/tail_icon_shop.png" to "/tmp/tail_icon_shop.png",
                "assets/tail_icon_selected_shop.png" to "/tmp/tail_icon_selected_shop.png",
                "assets/tail_icon_myself.png" to "/tmp/tail_icon_myself.png",
                "assets/tail_icon_channel.png" to "/tmp/tail_icon_channel.png",
                "assets/tail_icon_selected_channel.png" to "/tmp/tail_icon_selected_channel.png"
            )
        )

        val decoration = resolveBottomBarUiSkinDecoration(
            UiSkinState(enabled = true, activeSkin = installed)
        )

        assertEquals("/tmp/tail_icon_main.png", decoration?.iconPathFor(BottomNavItem.HOME))
        assertEquals("/tmp/tail_icon_selected_main.png", decoration?.iconPathFor(BottomNavItem.HOME, selected = true))
        assertEquals("/tmp/tail_icon_dynamic.png", decoration?.iconPathFor(BottomNavItem.DYNAMIC))
        assertEquals(
            "/tmp/tail_icon_selected_dynamic.png",
            decoration?.iconPathFor(BottomNavItem.DYNAMIC, selected = true)
        )
        assertEquals("/tmp/tail_icon_shop.png", decoration?.iconPathFor(BottomNavItem.HISTORY))
        assertEquals(
            "/tmp/tail_icon_selected_shop.png",
            decoration?.iconPathFor(BottomNavItem.HISTORY, selected = true)
        )
        assertEquals("/tmp/tail_icon_channel.png", decoration?.iconPathFor(BottomNavItem.LISTEN_VIDEO))
        assertEquals(
            "/tmp/tail_icon_selected_channel.png",
            decoration?.iconPathFor(BottomNavItem.LISTEN_VIDEO, selected = true)
        )
        assertEquals("/tmp/tail_icon_myself.png", decoration?.iconPathFor(BottomNavItem.PROFILE))
        assertEquals("/tmp/tail_icon_myself.png", decoration?.iconPathFor(BottomNavItem.SETTINGS))
        assertEquals("/tmp/tail_icon_shop.png", decoration?.iconPathFor(BottomNavItem.STORY))
        assertEquals("/tmp/tail_icon_channel.png", decoration?.iconPathFor(BottomNavItem.LIVE))

        // 可见自定义槽位发生资源重名冲突时自动分配尚未出场的角色贴图，避免相邻 Tab 撞脸
        val customVisible = listOf(
            BottomNavItem.HOME,
            BottomNavItem.DYNAMIC,
            BottomNavItem.PROFILE,
            BottomNavItem.SETTINGS,
        )
        val distinctResolved = customVisible.mapNotNull { nav ->
            decoration?.iconPathFor(nav, selected = false, visibleItems = customVisible)
        }.toSet()
        assertEquals(4, distinctResolved.size)
    }

    @Test
    fun customBottomDestinationsAlwaysUseAnAvailableSkinIcon() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.tail-icons",
                displayName = "底栏图标",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR),
                assets = UiSkinAssets(
                    bottomBarIcons = mapOf(
                        "home" to "assets/tail_icon_main.png",
                        "following" to "assets/tail_icon_dynamic.png",
                        "member" to "assets/tail_icon_shop.png",
                        "profile" to "assets/tail_icon_myself.png",
                        "profile_selected" to "assets/tail_icon_selected_myself.png"
                    )
                )
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/tail-icons.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf(
                "assets/tail_icon_main.png" to "/tmp/tail_icon_main.png",
                "assets/tail_icon_dynamic.png" to "/tmp/tail_icon_dynamic.png",
                "assets/tail_icon_shop.png" to "/tmp/tail_icon_shop.png",
                "assets/tail_icon_myself.png" to "/tmp/tail_icon_myself.png",
                "assets/tail_icon_selected_myself.png" to "/tmp/tail_icon_selected_myself.png"
            )
        )

        val decoration = resolveBottomBarUiSkinDecoration(
            UiSkinState(enabled = true, activeSkin = installed)
        )

        assertEquals("/tmp/tail_icon_myself.png", decoration?.iconPathFor(BottomNavItem.SETTINGS))
        assertEquals("/tmp/tail_icon_main.png", decoration?.iconPathFor(BottomNavItem.LISTEN_VIDEO))
        assertTrue(BottomNavItem.entries.all { decoration?.iconPathFor(it) != null })
    }

    @Test
    fun legacyChannelFieldsStillPopulateListenVideoWithoutReimport() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.legacy-channel",
                displayName = "旧版频道图标",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR),
                assets = UiSkinAssets(
                    homeChannelIcon = "assets/tail_icon_channel.png",
                    homeChannelSelectedIcon = "assets/tail_icon_selected_channel.png"
                )
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/legacy-channel.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf(
                "assets/tail_icon_channel.png" to "/tmp/tail_icon_channel.png",
                "assets/tail_icon_selected_channel.png" to "/tmp/tail_icon_selected_channel.png"
            )
        )

        val decoration = resolveBottomBarUiSkinDecoration(
            UiSkinState(enabled = true, activeSkin = installed)
        )

        assertEquals("/tmp/tail_icon_channel.png", decoration?.iconPathFor(BottomNavItem.LISTEN_VIDEO))
        assertEquals(
            "/tmp/tail_icon_selected_channel.png",
            decoration?.iconPathFor(BottomNavItem.LISTEN_VIDEO, selected = true)
        )
    }

    @Test
    fun selectedBottomSkinIconAlwaysUsesStableUnselectedAsset() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.tail-icons",
                displayName = "底栏图标",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR),
                assets = UiSkinAssets(
                    bottomBarIcons = mapOf("home" to "assets/tail_icon_main.png")
                )
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/tail-icons.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf("assets/tail_icon_main.png" to "/tmp/tail_icon_main.png")
        )

        val decoration = resolveBottomBarUiSkinDecoration(
            UiSkinState(enabled = true, activeSkin = installed)
        )

        assertEquals("/tmp/tail_icon_main.png", decoration?.iconPathFor(BottomNavItem.HOME, selected = true))
    }

    @Test
    fun disabledSkinDoesNotProduceBottomBarDecoration() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.cloud",
                displayName = "云朵底栏",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR),
                assets = UiSkinAssets(bottomBarTrim = "assets/bottom_trim.png")
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/cloud.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf("assets/bottom_trim.png" to "/tmp/bottom_trim.png")
        )

        assertNull(
            resolveBottomBarUiSkinDecoration(
                UiSkinState(enabled = false, activeSkin = installed)
            )
        )
    }

    @Test
    fun activeExternalSkinUsesExtractedHomeAtmosphereImagePath() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.atmosphere",
                displayName = "顶部氛围",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(
                    UiSkinSurface.HOME_TOP_CHROME,
                    UiSkinSurface.HOME_DRAWER,
                    UiSkinSurface.PROFILE,
                ),
                assets = UiSkinAssets(
                    topAtmosphere = "assets/head_bg.jpg",
                    homeTopTabBackground = "assets/head_tab_bg.jpg",
                    searchCapsuleBackground = "assets/search_bg.png",
                    homeSideBackground = "assets/side_bg.jpg",
                    drawerBottomTrim = "assets/side_bg_bottom.png",
                    homeProfileBackground = "assets/head_myself_bg.jpg",
                    homeProfileSquaredBackground = "assets/head_myself_squared_bg.jpg",
                    homeProfileVideoBackground = "assets/head_myself_mp4_bg.mp4",
                    homeChannelIcon = "assets/tail_icon_channel.png",
                    homeChannelSelectedIcon = "assets/tail_icon_selected_channel.png",
                    bottomBarIcons = mapOf(
                        "home" to "assets/tail_icon_main.png",
                        "home_selected" to "assets/tail_icon_selected_main.png",
                        "following" to "assets/tail_icon_dynamic.png",
                        "following_selected" to "assets/tail_icon_selected_dynamic.png",
                        "member" to "assets/tail_icon_shop.png",
                        "member_selected" to "assets/tail_icon_selected_shop.png",
                        "profile" to "assets/tail_icon_myself.png"
                    )
                ),
                colors = UiSkinColorTokens(
                    topAtmosphereTint = "#DFF5FF",
                    searchCapsuleTint = "#FFFFFF",
                    sideBackgroundTint = "#336699",
                    colorMode = "light",
                ),
                motion = UiSkinMotionTokens(profileVideoPlayMode = "once"),
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/atmosphere.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf(
                "assets/head_bg.jpg" to "/tmp/head_bg.jpg",
                "assets/head_tab_bg.jpg" to "/tmp/head_tab_bg.jpg",
                "assets/search_bg.png" to "/tmp/search_bg.png",
                "assets/side_bg.jpg" to "/tmp/side_bg.jpg",
                "assets/side_bg_bottom.png" to "/tmp/side_bg_bottom.png",
                "assets/head_myself_bg.jpg" to "/tmp/head_myself_bg.jpg",
                "assets/head_myself_squared_bg.jpg" to "/tmp/head_myself_squared_bg.jpg",
                "assets/head_myself_mp4_bg.mp4" to "/tmp/head_myself_mp4_bg.mp4",
                "assets/tail_icon_channel.png" to "/tmp/tail_icon_channel.png",
                "assets/tail_icon_selected_channel.png" to "/tmp/tail_icon_selected_channel.png",
                "assets/tail_icon_main.png" to "/tmp/tail_icon_main.png",
                "assets/tail_icon_selected_main.png" to "/tmp/tail_icon_selected_main.png",
                "assets/tail_icon_dynamic.png" to "/tmp/tail_icon_dynamic.png",
                "assets/tail_icon_selected_dynamic.png" to "/tmp/tail_icon_selected_dynamic.png",
                "assets/tail_icon_shop.png" to "/tmp/tail_icon_shop.png",
                "assets/tail_icon_selected_shop.png" to "/tmp/tail_icon_selected_shop.png",
                "assets/tail_icon_myself.png" to "/tmp/tail_icon_myself.png"
            )
        )

        val decoration = resolveHomeUiSkinDecoration(
            UiSkinState(enabled = true, activeSkin = installed)
        )

        assertEquals("dev.example.atmosphere", decoration?.skinId)
        assertEquals("/tmp/head_bg.jpg", decoration?.topAtmosphereImagePath)
        assertEquals("/tmp/head_tab_bg.jpg", decoration?.topTabBackgroundImagePath)
        assertEquals("/tmp/search_bg.png", decoration?.searchCapsuleImagePath)
        assertEquals("/tmp/side_bg.jpg", decoration?.sideBackgroundImagePath)
        assertEquals("/tmp/side_bg_bottom.png", decoration?.sideBottomTrimImagePath)
        assertEquals("/tmp/head_myself_bg.jpg", decoration?.profileBackgroundImagePath)
        assertEquals("/tmp/head_myself_squared_bg.jpg", decoration?.profileSquaredBackgroundImagePath)
        assertEquals("/tmp/head_myself_mp4_bg.mp4", decoration?.profileVideoBackgroundPath)
        assertEquals("once", decoration?.profileVideoPlayMode)
        assertEquals("light", decoration?.colorMode)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF336699), decoration?.sideBackgroundTint)
        assertTrue(decoration?.topTabSkinIconPaths?.isEmpty() == true)
        assertNull(decoration?.topTabPartitionIconPath())
    }

    @Test
    fun activeSkinMapsSelectedTintPublishAssetsAndIconMotion() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.full-bottom",
                displayName = "完整底栏",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(
                    UiSkinSurface.HOME_BOTTOM_BAR,
                    UiSkinSurface.DYNAMIC_PUBLISH,
                ),
                assets = UiSkinAssets(
                    dynamicPublishIcon = "assets/tail_icon_pub_btn_bg.png",
                    dynamicPublishSelectedIcon = "assets/tail_icon_selected_pub_btn_bg.png",
                ),
                colors = UiSkinColorTokens(
                    bottomBarIconTint = "#112233",
                    bottomBarIconDarkTint = "#AABBCC",
                    bottomBarSelectedTint = "#123456",
                    bottomBarSelectedDarkTint = "#FEDCBA",
                    dynamicPublishIconTint = "#FFFFFF",
                    dynamicPublishShadeTop = "#446688",
                    dynamicPublishShadeBottom = "#224466",
                ),
                motion = UiSkinMotionTokens(
                    bottomBarIconAnimated = true,
                    bottomBarIconAnimationMode = "once",
                    bottomBarIconMode = "img",
                ),
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/full-bottom.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf(
                "assets/tail_icon_pub_btn_bg.png" to "/tmp/tail_icon_pub_btn_bg.png",
                "assets/tail_icon_selected_pub_btn_bg.png" to "/tmp/tail_icon_selected_pub_btn_bg.png",
            ),
        )

        val state = UiSkinState(enabled = true, activeSkin = installed)
        val decoration = resolveBottomBarUiSkinDecoration(state)
        val darkDecoration = resolveBottomBarUiSkinDecoration(state, isDark = true)
        val publishDecoration = resolveDynamicPublishSkinDecoration(
            state
        )

        assertEquals(androidx.compose.ui.graphics.Color(0xFF112233), decoration?.bottomUnselectedTint)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF123456), decoration?.bottomSelectedTint)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFAABBCC), darkDecoration?.bottomUnselectedTint)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFEDCBA), darkDecoration?.bottomSelectedTint)
        assertEquals("/tmp/tail_icon_pub_btn_bg.png", publishDecoration?.iconPaths?.pathFor(false))
        assertEquals(
            "/tmp/tail_icon_selected_pub_btn_bg.png",
            publishDecoration?.iconPaths?.pathFor(true)
        )
        assertEquals(true, decoration?.iconMotion?.enabled)
        assertEquals("once", decoration?.iconMotion?.mode)
        assertEquals(androidx.compose.ui.graphics.Color.White, publishDecoration?.iconTint)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF446688), publishDecoration?.shadeTop)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF224466), publishDecoration?.shadeBottom)
    }

    @Test
    fun bottomOnlySkinDoesNotProduceHomeAtmosphereDecoration() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "dev.example.bottom",
                displayName = "仅底栏",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR),
                assets = UiSkinAssets(bottomBarTrim = "assets/bottom_trim.png")
            ),
            packageSha256 = "sha",
            packagePath = "/tmp/bottom.bpskin",
            installedAtMillis = 42L,
            assetFiles = mapOf("assets/bottom_trim.png" to "/tmp/bottom_trim.png")
        )

        assertNull(
            resolveHomeUiSkinDecoration(
                UiSkinState(enabled = true, activeSkin = installed)
            )
        )
    }
}
