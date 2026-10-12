package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.plugin.ExternalPluginInstallDecision
import com.android.purebilibili.core.plugin.PluginCapability
import com.android.purebilibili.core.plugin.PluginCapabilityManifest
import com.android.purebilibili.core.plugin.kotlinpkg.InstalledExternalPluginPackage
import com.android.purebilibili.core.plugin.skin.InstalledUiSkinPackage
import com.android.purebilibili.core.plugin.skin.UiSkinAssets
import com.android.purebilibili.core.plugin.skin.UiSkinManifest
import com.android.purebilibili.core.plugin.skin.UiSkinPackagePreview
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginsScreenPolicyTest {

    @Test
    fun sponsorBlockToggle_routesThroughSettingsPath() = runTest {
        var sponsorBlockToggleCount = 0
        var genericToggleCount = 0

        dispatchBuiltInPluginToggle(
            pluginId = "sponsor_block",
            enabled = true,
            onSponsorBlockToggle = { sponsorBlockToggleCount += 1 },
            onGenericPluginToggle = { _, _ -> genericToggleCount += 1 }
        )

        assertEquals(1, sponsorBlockToggleCount)
        assertEquals(0, genericToggleCount)
    }

    @Test
    fun nonSponsorBlockToggle_routesThroughGenericPluginPath() = runTest {
        var sponsorBlockToggleCount = 0
        var genericToggleCount = 0

        dispatchBuiltInPluginToggle(
            pluginId = "danmaku_enhance",
            enabled = false,
            onSponsorBlockToggle = { sponsorBlockToggleCount += 1 },
            onGenericPluginToggle = { _, _ -> genericToggleCount += 1 }
        )

        assertEquals(0, sponsorBlockToggleCount)
        assertEquals(1, genericToggleCount)
    }

    @Test
    fun capabilityModels_markNetworkAndStorageAsSensitiveAuthorizationItems() {
        val models = resolvePluginCapabilityUiModels(
            setOf(
                PluginCapability.RECOMMENDATION_CANDIDATES,
                PluginCapability.NETWORK,
                PluginCapability.PLUGIN_STORAGE,
                PluginCapability.EXTERNAL_MEDIA_PLAYBACK
            )
        )

        assertTrue(models.first { it.capability == PluginCapability.NETWORK }.requiresExplicitApproval)
        assertTrue(models.first { it.capability == PluginCapability.PLUGIN_STORAGE }.requiresExplicitApproval)
        assertTrue(models.first { it.capability == PluginCapability.EXTERNAL_MEDIA_PLAYBACK }.requiresExplicitApproval)
    }

    @Test
    fun externalInstallPreview_preservesManifestTitle() {
        val manifest = PluginCapabilityManifest(
            pluginId = "dev.example.cloud",
            displayName = "云推荐",
            version = "1.0.0",
            apiVersion = 1,
            entryClassName = "dev.example.CloudPlugin",
            capabilities = setOf(PluginCapability.NETWORK, PluginCapability.LOCAL_HISTORY_READ)
        )
        val preview = buildExternalPluginInstallPreview(
            ExternalPluginInstallDecision.RequiresUserApproval(
                manifest = manifest,
                packageSha256 = "abcdef123456",
                signerTrusted = false,
                sensitiveCapabilities = setOf(PluginCapability.NETWORK, PluginCapability.LOCAL_HISTORY_READ)
            )
        )

        assertEquals("云推荐", preview.title)
    }

    @Test
    fun jsonRulePluginType_mapsToHostCapabilities() {
        assertEquals(
            setOf(PluginCapability.RECOMMENDATION_CANDIDATES),
            resolveJsonRulePluginCapabilities("feed")
        )
        assertEquals(
            setOf(PluginCapability.DANMAKU_STREAM, PluginCapability.DANMAKU_MUTATION),
            resolveJsonRulePluginCapabilities("danmaku")
        )
    }


    @Test
    fun installedExternalPackageUi_preservesManifestTitle() {
        val installed = InstalledExternalPluginPackage(
            manifest = PluginCapabilityManifest(
                pluginId = "dev.example.cloud",
                displayName = "云推荐",
                version = "1.0.0",
                apiVersion = 1,
                entryClassName = "dev.example.CloudPlugin",
                capabilities = setOf(
                    PluginCapability.RECOMMENDATION_CANDIDATES,
                    PluginCapability.NETWORK
                )
            ),
            packageSha256 = "abcdef123456",
            signerSha256 = "trusted",
            grantedCapabilities = setOf(PluginCapability.RECOMMENDATION_CANDIDATES),
            packagePath = "/tmp/cloud.bpplugin",
            installedAtMillis = 1234L,
            enabled = false
        )

        val uiModel = buildInstalledExternalPluginUiModels(listOf(installed)).single()

        assertEquals("云推荐", uiModel.title)
    }

    @Test
    fun uiSkinInstallPreview_preservesManifestTitle() {
        val preview = UiSkinPackagePreview(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "community.pastel",
                displayName = "收藏馆浅彩风",
                version = "1.0.0",
                apiVersion = 1,
                author = "BiliPai",
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR),
                assets = UiSkinAssets(bottomBarTrim = "assets/bottom_trim.png"),
                styleSourceName = "KimmyXYC/bilibili-skin",
                styleSourceUrl = "https://github.com/KimmyXYC/bilibili-skin",
                licenseNote = "原创重绘资源，可作为社区包分享",
                communityShareable = true,
                containsOfficialAssets = false
            ),
            packageSha256 = "abcdef",
            assetEntries = emptyList()
        )

        val uiModel = buildUiSkinPackagePreview(preview)

        assertEquals("收藏馆浅彩风", uiModel.title)
    }


    @Test
    fun installedUiSkinPreview_preservesTitleAndDeleteCapability() {
        val installed = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1,
                skinId = "local.bilibili_skin.preview",
                displayName = "预览皮肤",
                version = "1.0.0",
                apiVersion = 1,
                surfaces = setOf(UiSkinSurface.HOME_BOTTOM_BAR, UiSkinSurface.HOME_TOP_CHROME),
                assets = UiSkinAssets(
                    bottomBarTrim = "assets/tail_bg.png",
                    topAtmosphere = "assets/head_bg.jpg"
                ),
                styleSourceName = "KimmyXYC/bilibili-skin",
                licenseNote = "仅本地使用",
                communityShareable = false,
                containsOfficialAssets = true
            ),
            packageSha256 = "abcdef123456",
            packagePath = "/tmp/preview.bpskin",
            installedAtMillis = 1234L,
            assetFiles = mapOf(
                "assets/tail_bg.png" to "/tmp/tail_bg.png",
                "assets/head_bg.jpg" to "/tmp/head_bg.jpg"
            )
        )

        val uiModel = buildInstalledUiSkinPreview(
            installed = installed,
            isActive = true
        )

        assertEquals("预览皮肤", uiModel.title)
        assertTrue(uiModel.canDelete)
    }

    @Test
    fun uiSkinImagePreviewModels_preserveLocalPathsAndVideoClassification() {
        val models = buildUiSkinImagePreviewItems(
            assetFiles = mapOf(
                "assets/tail_bg.png" to "/tmp/tail_bg.png",
                "assets/head_bg.jpg" to "/tmp/head_bg.jpg",
                "assets/head_myself_mp4_bg.mp4" to "/tmp/head_myself_mp4_bg.mp4",
                "assets/tail_icon_pub_btn_bg.png" to "/tmp/tail_icon_pub_btn_bg.png",
                "assets/unknown.png" to "/tmp/unknown.png"
            )
        )

        assertEquals(
            listOf(
                "/tmp/tail_bg.png",
                "/tmp/head_bg.jpg",
                "/tmp/head_myself_mp4_bg.mp4",
                "/tmp/tail_icon_pub_btn_bg.png",
                "/tmp/unknown.png",
            ),
            models.map { it.localPath }
        )
        assertEquals(listOf(false, false, true, false, false), models.map { it.isVideo })
    }

}
