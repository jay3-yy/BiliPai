package com.android.purebilibili.core.plugin.skin

import kotlinx.serialization.Serializable

enum class UiSkinSurface {
    HOME_BOTTOM_BAR,
    HOME_TOP_CHROME,
    HOME_DRAWER,
    PROFILE,
    DYNAMIC_PUBLISH,
    COMMENT_CARD,
    LOADING_INDICATOR,
    LIKE_EFFECT,
    PLAYER_PROGRESS
}

enum class UiSkinAssetType {
    PNG,
    WEBP,
    JPEG,
    MP4,
    JSON
}

@Serializable
data class UiSkinManifest(
    val formatVersion: Int,
    val skinId: String,
    val displayName: String,
    val version: String,
    val apiVersion: Int,
    val author: String? = null,
    val surfaces: Set<UiSkinSurface>,
    val assets: UiSkinAssets = UiSkinAssets(),
    val colors: UiSkinColorTokens = UiSkinColorTokens(),
    val motion: UiSkinMotionTokens = UiSkinMotionTokens(),
    val styleSourceName: String? = null,
    val styleSourceUrl: String? = null,
    val licenseNote: String? = null,
    val communityShareable: Boolean = false,
    val containsOfficialAssets: Boolean = false
)

@Serializable
data class UiSkinAssets(
    val coverImage: String? = null,
    val bottomBarTrim: String? = null,
    val drawerBottomTrim: String? = null,
    val topAtmosphere: String? = null,
    val homeTopTabBackground: String? = null,
    val searchCapsuleBackground: String? = null,
    val homeSideBackground: String? = null,
    val homeProfileBackground: String? = null,
    val homeProfileSquaredBackground: String? = null,
    val homeProfileVideoBackground: String? = null,
    val homeChannelIcon: String? = null,
    val homeChannelSelectedIcon: String? = null,
    val dynamicPublishIcon: String? = null,
    val dynamicPublishSelectedIcon: String? = null,
    val fanCardImage: String? = null,
    val fanShareCardImage: String? = null,
    val commentCardBgImage: String? = null,
    val spaceVideoVertical: String? = null,
    val spaceVideoHorizontal: String? = null,
    val loadingAnimation: String? = null,
    val loadingFrame: String? = null,
    val likeEffectAnimation: String? = null,
    val likeEffectPreview: String? = null,
    val playerProgressIcon: String? = null,
    val playerProgressDraggingIcon: String? = null,
    val playerProgressStaticIcon: String? = null,
    val bottomBarIcons: Map<String, String> = emptyMap(),
    val spaceBackgrounds: List<UiSkinSpaceBackground> = emptyList(),
    val emojiImages: Map<String, String> = emptyMap(),
) {
    fun declaredPaths(): List<String> {
        return buildList {
            coverImage?.let(::add)
            bottomBarTrim?.let(::add)
            drawerBottomTrim?.let(::add)
            topAtmosphere?.let(::add)
            homeTopTabBackground?.let(::add)
            searchCapsuleBackground?.let(::add)
            homeSideBackground?.let(::add)
            homeProfileBackground?.let(::add)
            homeProfileSquaredBackground?.let(::add)
            homeProfileVideoBackground?.let(::add)
            homeChannelIcon?.let(::add)
            homeChannelSelectedIcon?.let(::add)
            dynamicPublishIcon?.let(::add)
            dynamicPublishSelectedIcon?.let(::add)
            fanCardImage?.let(::add)
            fanShareCardImage?.let(::add)
            commentCardBgImage?.let(::add)
            spaceVideoVertical?.let(::add)
            spaceVideoHorizontal?.let(::add)
            loadingAnimation?.let(::add)
            loadingFrame?.let(::add)
            likeEffectAnimation?.let(::add)
            likeEffectPreview?.let(::add)
            playerProgressIcon?.let(::add)
            playerProgressDraggingIcon?.let(::add)
            playerProgressStaticIcon?.let(::add)
            addAll(bottomBarIcons.values)
            spaceBackgrounds.forEach { background ->
                background.portrait?.let(::add)
                background.landscape?.let(::add)
            }
            addAll(emojiImages.values)
        }
    }
}

@Serializable
data class UiSkinSpaceBackground(
    val portrait: String? = null,
    val landscape: String? = null,
)

@Serializable
data class UiSkinColorTokens(
    val coverTint: String? = null,
    val bottomBarTrimTint: String? = null,
    val bottomBarIconTint: String? = null,
    val bottomBarIconDarkTint: String? = null,
    val bottomBarSelectedTint: String? = null,
    val bottomBarSelectedDarkTint: String? = null,
    val topAtmosphereTint: String? = null,
    val topForegroundTint: String? = null,
    val topForegroundDarkTint: String? = null,
    val searchCapsuleTint: String? = null,
    val sideBackgroundTint: String? = null,
    val dynamicPublishIconTint: String? = null,
    val dynamicPublishShadeTop: String? = null,
    val dynamicPublishShadeBottom: String? = null,
    val fanNoColor: String? = null,
    val commentFanNoColor: String? = null,
    val playerProgressActiveTint: String? = null,
    val playerProgressBufferedTint: String? = null,
    val playerProgressTrackTint: String? = null,
    val liquidGlassSurfaceTint: String? = null,
    val liquidGlassTintOpacity: Float? = null,
    val colorMode: String? = null
)

@Serializable
data class UiSkinMotionTokens(
    val bottomBarIconAnimated: Boolean = false,
    val bottomBarIconAnimationMode: String? = null,
    val bottomBarIconMode: String? = null,
    val profileVideoPlayMode: String? = null
)

data class UiSkinAssetEntry(
    val path: String,
    val type: UiSkinAssetType,
    val sizeBytes: Long
)

data class UiSkinPackagePreview(
    val manifest: UiSkinManifest,
    val packageSha256: String,
    val assetEntries: List<UiSkinAssetEntry>
)

@Serializable
data class InstalledUiSkinPackage(
    val manifest: UiSkinManifest,
    val packageSha256: String,
    val packagePath: String,
    val installedAtMillis: Long,
    val enabled: Boolean = false,
    val assetFiles: Map<String, String> = emptyMap(),
    val installId: String = buildUiSkinInstallId(manifest.skinId, packageSha256)
) {
    val skinId: String
        get() = manifest.skinId

    val displayName: String
        get() = manifest.displayName

    fun assetFilePath(assetPath: String?): String? {
        return assetPath?.let(assetFiles::get)
    }
}

enum class UiSkinTonePolicy(val label: String) {
    AUTO("自动采样"),
    FORCE_LIGHT_BG("浅底深字"),
    FORCE_DARK_BG("深底浅字"),
}

data class UiSkinSelection(
    val enabled: Boolean = false,
    val selectedSkinId: String? = null,
    val selectedInstallId: String? = null,
    val disabledSurfaces: Set<UiSkinSurface> = emptySet(),
    val customFanNumber: String = "000088",
    val tonePolicy: UiSkinTonePolicy = UiSkinTonePolicy.AUTO,
)

data class UiSkinState(
    val enabled: Boolean = false,
    val activeSkin: InstalledUiSkinPackage? = null,
    val availableSurfaces: Set<UiSkinSurface> = activeSkin?.manifest?.surfaces.orEmpty(),
    val disabledSurfaces: Set<UiSkinSurface> = emptySet(),
    val customFanNumber: String = "000088",
    val tonePolicy: UiSkinTonePolicy = UiSkinTonePolicy.AUTO,
) {
    fun isSurfaceEnabled(surface: UiSkinSurface): Boolean {
        return enabled && activeSkin != null &&
            surface in activeSkin.manifest.surfaces &&
            surface !in disabledSurfaces
    }
}

internal fun buildUiSkinInstallId(
    skinId: String,
    packageSha256: String
): String {
    return "${skinId.safeUiSkinFileSegment()}-${packageSha256.take(16)}"
}

internal fun String.safeUiSkinFileSegment(): String {
    return replace(Regex("[^A-Za-z0-9_.-]"), "_")
}
