package com.android.purebilibili.core.plugin.skin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val BILIBILI_SKIN_SOURCE_NAME = "Rovniced/bilibili-skin"
private const val BILIBILI_SKIN_SOURCE_URL = "https://github.com/Rovniced/bilibili-skin"
private const val BILIBILI_SUIT_COLLECTION_SOURCE_URL =
    "https://github.com/sjh8130/BilibiliSuitCollection"
private const val BILIBILI_SKIN_LICENSE_NOTE =
    "由用户本地 Rovniced/bilibili-skin 主题或官方装扮存档转换，输出包包含原存档装扮素材；" +
        "仅供本地私用或在已获得授权时分享，不要将官方付费主题原图、角色立绘、图标原件或动效资源作为社区包分发。"
private const val MAX_THEME_ENTRY_COUNT = 256
private const val MAX_THEME_TOTAL_BYTES = 128 * 1024 * 1024

enum class UiSkinImportSource {
    BP_SKIN,
    BILIBILI_SKIN_ARCHIVE
}

data class UiSkinImportPackage(
    val source: UiSkinImportSource,
    val packageBytes: ByteArray
)

enum class UiSkinImportMode {
    FULL_SKIN,
    PERSONAL_BACKGROUND_ONLY,
}

object UiSkinImportPackageResolver {
    private val json = Json { ignoreUnknownKeys = true }
    private val iconMapping = mapOf(
        "tail_icon_main" to "home",
        "tail_icon_dynamic" to "following",
        "tail_icon_shop" to "member",
        "tail_icon_vip" to "member",
        "tail_icon_mall" to "member",
        "tail_icon_channel" to "channel",
        "tail_icon_myself" to "profile"
    )
    private val selectedIconMapping = mapOf(
        "tail_icon_selected_main" to "home_selected",
        "tail_icon_selected_dynamic" to "following_selected",
        "tail_icon_selected_shop" to "member_selected",
        "tail_icon_selected_vip" to "member_selected",
        "tail_icon_selected_mall" to "member_selected",
        "tail_icon_selected_channel" to "channel_selected",
        "tail_icon_selected_myself" to "profile_selected"
    )

    fun resolve(
        inputBytes: ByteArray,
        remotePackageFetcher: ((String) -> ByteArray)? = null
    ): Result<UiSkinImportPackage> {
        return runCatching {
            if (UiSkinPackageReader.preview(inputBytes).isSuccess) {
                return@runCatching UiSkinImportPackage(
                    source = UiSkinImportSource.BP_SKIN,
                    packageBytes = inputBytes
                )
            }
            UiSkinImportPackage(
                source = UiSkinImportSource.BILIBILI_SKIN_ARCHIVE,
                packageBytes = convertBilibiliThemeArchive(inputBytes, remotePackageFetcher)
            )
        }
    }

    /** Creates a minimal package that retains only profile & space background assets. */
    fun restrictToPersonalBackground(packageBytes: ByteArray): Result<ByteArray> = runCatching {
        val preview = UiSkinPackageReader.preview(packageBytes).getOrThrow()
        val profileAssets = preview.manifest.assets.copy(
            bottomBarTrim = null,
            drawerBottomTrim = null,
            topAtmosphere = null,
            homeTopTabBackground = null,
            searchCapsuleBackground = null,
            homeSideBackground = null,
            homeChannelIcon = null,
            homeChannelSelectedIcon = null,
            dynamicPublishIcon = null,
            dynamicPublishSelectedIcon = null,
            fanCardImage = null,
            fanShareCardImage = null,
            commentCardBgImage = null,
            loadingAnimation = null,
            loadingFrame = null,
            likeEffectAnimation = null,
            likeEffectPreview = null,
            playerProgressIcon = null,
            playerProgressDraggingIcon = null,
            playerProgressStaticIcon = null,
            bottomBarIcons = emptyMap(),
            emojiImages = emptyMap(),
        )
        require(
            profileAssets.homeProfileBackground != null ||
                profileAssets.homeProfileSquaredBackground != null ||
                profileAssets.homeProfileVideoBackground != null ||
                profileAssets.spaceBackgrounds.isNotEmpty() ||
                profileAssets.spaceVideoVertical != null ||
                profileAssets.spaceVideoHorizontal != null
        ) { "皮肤包不包含个人或空间背景图" }
        val entries = scanZip(packageBytes, "皮肤包包含非法路径")
            .filterKeys { it != "skin-manifest.json" }
            .filterKeys { it in profileAssets.declaredPaths() }
        buildBpskinPackage(
            preview.manifest.copy(
                surfaces = setOf(UiSkinSurface.PROFILE),
                assets = profileAssets,
            ),
            entries,
        )
    }

    fun resolveBilibiliPackageWithMetadata(
        packageBytes: ByteArray,
        themeJsonBytes: ByteArray,
        remotePackageFetcher: ((String) -> ByteArray)? = null,
    ): Result<UiSkinImportPackage> {
        return runCatching {
            val theme = parseThemeJson(themeJsonBytes)
            val packageEntries = scanZip(
                inputBytes = packageBytes,
                illegalPathMessage = "装扮资源包包含非法路径"
            )
            val remoteSuitAssets = fetchRemoteSuitSupplementalAssets(themeJsonBytes, remotePackageFetcher)
            val assetBytesByPath = buildAssetBytes(packageEntries + remoteSuitAssets)
            if (assetBytesByPath.isEmpty()) {
                throw IllegalArgumentException("装扮资源包缺少可转换资源")
            }
            val manifest = buildManifest(
                theme = theme,
                assetPaths = assetBytesByPath.keys.toSet()
            )
            UiSkinImportPackage(
                source = UiSkinImportSource.BILIBILI_SKIN_ARCHIVE,
                packageBytes = buildBpskinPackage(manifest, assetBytesByPath),
            )
        }
    }

    fun mergeSupplementalEffectAssets(
        packageBytes: ByteArray,
        supplementalAssets: Map<String, ByteArray>,
    ): Result<ByteArray> = runCatching {
        if (supplementalAssets.isEmpty()) return@runCatching packageBytes
        val preview = UiSkinPackageReader.preview(packageBytes).getOrThrow()
        val packageEntries = scanZip(packageBytes, "皮肤包包含非法路径")
            .filterKeys { it != "skin-manifest.json" }
            .toMutableMap()
        packageEntries.putAll(supplementalAssets)
        val oldAssets = preview.manifest.assets
        val mergedAssets = oldAssets.copy(
            loadingAnimation = supplementalAssets.keys.firstOrNull {
                it == "assets/loading.json" || it == "assets/loading.webp" || it == "assets/loading.png"
            } ?: oldAssets.loadingAnimation,
            loadingFrame = supplementalAssets.keys.firstOrNull { it == "assets/loading_frame.png" }
                ?: oldAssets.loadingFrame,
            likeEffectAnimation = supplementalAssets.keys.firstOrNull {
                it == "assets/like_effect.json" || it == "assets/like_effect.webp" ||
                    it == "assets/like_effect.png"
            } ?: oldAssets.likeEffectAnimation,
            likeEffectPreview = supplementalAssets.keys.firstOrNull {
                it == "assets/like_effect_preview.png" || it == "assets/like_effect_preview.jpg"
            } ?: oldAssets.likeEffectPreview,
            playerProgressIcon = supplementalAssets.keys.firstOrNull {
                it == "assets/progress_icon.json" || it == "assets/progress_icon.webp" ||
                    it == "assets/progress_icon.png"
            } ?: oldAssets.playerProgressIcon,
            playerProgressDraggingIcon = supplementalAssets.keys.firstOrNull {
                it == "assets/progress_drag_icon.json" || it == "assets/progress_drag_icon.webp" ||
                    it == "assets/progress_drag_icon.png"
            } ?: oldAssets.playerProgressDraggingIcon,
            playerProgressStaticIcon = supplementalAssets.keys.firstOrNull {
                it == "assets/progress_static_icon.png" || it == "assets/progress_static_icon.webp"
            } ?: oldAssets.playerProgressStaticIcon,
        )
        val mergedSurfaces = buildSet {
            addAll(preview.manifest.surfaces)
            if (mergedAssets.loadingAnimation != null) add(UiSkinSurface.LOADING_INDICATOR)
            if (mergedAssets.likeEffectAnimation != null || mergedAssets.likeEffectPreview != null) {
                add(UiSkinSurface.LIKE_EFFECT)
            }
            if (
                mergedAssets.playerProgressIcon != null ||
                mergedAssets.playerProgressDraggingIcon != null ||
                mergedAssets.playerProgressStaticIcon != null
            ) {
                add(UiSkinSurface.PLAYER_PROGRESS)
            }
        }
        val mergedManifest = preview.manifest.copy(
            surfaces = mergedSurfaces,
            assets = mergedAssets,
        )
        buildBpskinPackage(mergedManifest, packageEntries).also {
            UiSkinPackageReader.preview(it).getOrThrow()
        }
    }

    private fun convertBilibiliThemeArchive(
        inputBytes: ByteArray,
        remotePackageFetcher: ((String) -> ByteArray)?
    ): ByteArray {
        if (looksLikeJson(inputBytes)) {
            return convertDirectThemeJson(inputBytes, remotePackageFetcher)
        }
        val rawOuterEntries = scanZip(
            inputBytes = inputBytes,
            illegalPathMessage = "装扮存档包含非法路径"
        )
        val outerEntries = stripSingleRootFolderIfPresent(rawOuterEntries)
        val allThemeJsons = selectOrderedThemeJsons(outerEntries)
        val primaryThemeJson = allThemeJsons.firstOrNull()
        val suitDataJson = outerEntries["data.json"] ?: primaryThemeJson
        var packageZip = selectPackageZipOrNull(outerEntries)
        if (packageZip == null && primaryThemeJson != null) {
            val packageUrl = primaryThemeJson.packageUrlOrNull() ?: suitDataJson?.packageUrlOrNull()
            if (packageUrl != null && remotePackageFetcher != null) {
                packageZip = runCatching { fetchRemotePackage(packageUrl, remotePackageFetcher) }.getOrNull()
            }
        }
        val theme = parseMergedThemeMetadata(allThemeJsons, suitDataJson)
        val packageEntries = if (packageZip == null) {
            outerEntries
        } else {
            scanZip(
                inputBytes = packageZip,
                illegalPathMessage = "装扮资源包包含非法路径"
            )
        }
        val supplementalOuter = outerSuitAssets(
            outerEntries = outerEntries,
            packageEntries = packageEntries,
            suitName = theme.name
        )
        val assetBytesByPath = buildAssetBytes(packageEntries + supplementalOuter)
        if (assetBytesByPath.isEmpty()) {
            throw IllegalArgumentException("装扮资源包缺少可转换资源")
        }
        val manifest = buildManifest(
            theme = theme,
            assetPaths = assetBytesByPath.keys.toSet()
        )
        return buildBpskinPackage(manifest, assetBytesByPath)
    }

    private fun convertDirectThemeJson(
        inputBytes: ByteArray,
        remotePackageFetcher: ((String) -> ByteArray)?
    ): ByteArray {
        convertStandaloneSuitItemOrNull(inputBytes, remotePackageFetcher)?.let { return it }
        val packageUrl = inputBytes.packageUrlOrNull()
        val packageEntries = if (packageUrl != null) {
            val packageZip = fetchRemotePackage(packageUrl, remotePackageFetcher)
            scanZip(
                inputBytes = packageZip,
                illegalPathMessage = "装扮资源包包含非法路径"
            )
        } else {
            emptyMap()
        }
        val remoteSuitAssets = fetchRemoteSuitSupplementalAssets(inputBytes, remotePackageFetcher)
        val theme = parseThemeJson(inputBytes)
        val assetBytesByPath = buildAssetBytes(packageEntries + remoteSuitAssets)
        if (assetBytesByPath.isEmpty()) {
            if (packageUrl == null) {
                throw IllegalArgumentException("皮肤 JSON 缺少 package_url，无法导入资源包")
            }
            throw IllegalArgumentException("装扮资源包缺少可转换资源")
        }
        val manifest = buildManifest(
            theme = theme,
            assetPaths = assetBytesByPath.keys.toSet()
        )
        return buildBpskinPackage(manifest, assetBytesByPath)
    }

    /**
     * Fetches auxiliary official suit assets declared in Rovniced/bilibili-skin JSON
     * (e.g. `space_bg.images`, `user_equip.preview`) or full Suit API `data.json`
     * (`suit_items.{space_bg, card, card_bg, emoji_package, loading, thumbup, play_icon}`).
     */
    private fun fetchRemoteSuitSupplementalAssets(
        jsonBytes: ByteArray,
        remotePackageFetcher: ((String) -> ByteArray)?
    ): Map<String, ByteArray> {
        if (remotePackageFetcher == null) return emptyMap()
        val root = parseJsonObjectOrFirstElement(jsonBytes) ?: return emptyMap()
        val dataObj = root.objectOrNull("data")
        val suitItems = dataObj?.objectOrNull("suit_items") ?: root.objectOrNull("suit_items")
        val fetched = linkedMapOf<String, ByteArray>()

        fun downloadSafe(url: String?, targetKey: String) {
            val clean = url?.trim()?.takeIf { it.isNotBlank() } ?: return
            if (targetKey in fetched) return
            runCatching { fetchRemotePackage(clean, remotePackageFetcher) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { fetched[targetKey] = it }
        }

        fun extOf(url: String, defaultExt: String = "png"): String {
            val pathPart = url.substringBefore('?').lowercase()
            return when {
                pathPart.endsWith(".jpg") || pathPart.endsWith(".jpeg") -> "jpg"
                pathPart.endsWith(".webp") -> "webp"
                pathPart.endsWith(".mp4") -> "mp4"
                pathPart.endsWith(".json") -> "json"
                else -> defaultExt
            }
        }

        // 1. Cover / Preview image
        val coverUrl = dataObj?.objectOrNull("properties")?.stringOrNull("image_cover")
            ?: root.objectOrNull("user_equip")?.stringOrNull("preview")
            ?: root.stringOrNull("preview")
        if (coverUrl != null) {
            downloadSafe(coverUrl, "cover_image.${extOf(coverUrl)}")
        }

        // 2. Rovniced/bilibili-skin top-level `space_bg.images: [{landscape, portrait}]`
        root.objectOrNull("space_bg")?.get("images")
            ?.runCatching { jsonArray }?.getOrNull()
            ?.forEachIndexed { idx, elem ->
                val obj = elem as? JsonObject ?: return@forEachIndexed
                val n = idx + 1
                obj.stringOrNull("portrait")?.let { url ->
                    downloadSafe(url, "space_bg/image${n}_portrait.${extOf(url, "jpg")}")
                }
                obj.stringOrNull("landscape")?.let { url ->
                    downloadSafe(url, "space_bg/image${n}_landscape.${extOf(url, "jpg")}")
                }
            }

        // 3. Full Suit `suit_items`
        suitItems?.get("space_bg")?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull()?.let { it as? JsonObject }?.objectOrNull("properties")?.let { props ->
                for (n in 1..8) {
                    props.stringOrNull("image${n}_portrait")?.let { url ->
                        downloadSafe(url, "space_bg/image${n}_portrait.${extOf(url, "jpg")}")
                    }
                    props.stringOrNull("image${n}_landscape")?.let { url ->
                        downloadSafe(url, "space_bg/image${n}_landscape.${extOf(url, "jpg")}")
                    }
                }
                props.stringOrNull("space_1_mp4_vertical")?.let { url ->
                    downloadSafe(url, "space_video_vertical.mp4")
                }
                props.stringOrNull("space_1_mp4_horizontal")?.let { url ->
                    downloadSafe(url, "space_video_horizontal.mp4")
                }
            }

        suitItems?.get("card")?.runCatching { jsonArray }?.getOrNull()?.forEach { elem ->
            val cardObj = elem as? JsonObject ?: return@forEach
            val cardName = cardObj.stringOrNull("name").orEmpty()
            val imgUrl = cardObj.objectOrNull("properties")?.stringOrNull("image") ?: return@forEach
            if (cardName.endsWith("粉丝")) {
                downloadSafe(imgUrl, "fan_share_card.${extOf(imgUrl)}")
            } else {
                downloadSafe(imgUrl, "fan_card.${extOf(imgUrl)}")
            }
        }

        suitItems?.get("card_bg")?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull()?.let { it as? JsonObject }?.objectOrNull("properties")
            ?.stringOrNull("image")?.let { url ->
                downloadSafe(url, "comment_card_bg.${extOf(url)}")
            }

        suitItems?.get("emoji_package")?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull()?.let { it as? JsonObject }?.get("items")
            ?.runCatching { jsonArray }?.getOrNull()?.take(40)?.forEach { elem ->
                val item = elem as? JsonObject ?: return@forEach
                val name = item.stringOrNull("name")?.trim().orEmpty()
                val imgUrl = item.objectOrNull("properties")?.stringOrNull("image") ?: return@forEach
                if (name.startsWith("[") && name.endsWith("]")) {
                    downloadSafe(imgUrl, "emoji_package/$name.${extOf(imgUrl)}")
                }
            }

        suitItems?.get("loading")?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull()?.let { it as? JsonObject }?.objectOrNull("properties")?.let { props ->
                downloadSafe(props.stringOrNull("loading_url"), "loading.webp")
                downloadSafe(props.stringOrNull("loading_frame_url"), "loading_frame.png")
            }

        suitItems?.get("thumbup")?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull()?.let { it as? JsonObject }?.objectOrNull("properties")?.let { props ->
                props.stringOrNull("image_ani")?.let { url ->
                    downloadSafe(url, "like_effect.${extOf(url, "json")}")
                }
                props.stringOrNull("image_preview")?.let { url ->
                    downloadSafe(url, "like_effect_preview.${extOf(url, "png")}")
                }
            }

        suitItems?.get("play_icon")?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull()?.let { it as? JsonObject }?.objectOrNull("properties")?.let { props ->
                downloadSafe(props.stringOrNull("icon"), "progress_icon.json")
                downloadSafe(props.stringOrNull("drag_icon"), "progress_drag_icon.json")
                downloadSafe(props.stringOrNull("static_icon_image"), "progress_static_icon.png")
            }

        return fetched
    }

    private fun convertStandaloneSuitItemOrNull(
        inputBytes: ByteArray,
        remotePackageFetcher: ((String) -> ByteArray)?
    ): ByteArray? {
        val root = parseJsonObjectOrFirstElement(inputBytes) ?: return null
        val partId = root.stringOrNull("part_id")?.toIntOrNull() ?: return null
        if (partId !in setOf(3, 10, 11)) return null
        val properties = root.objectOrNull("properties") ?: return null
        val itemId = root.stringOrNull("item_id") ?: "local"
        val displayName = root.stringOrNull("name") ?: root.stringOrNull("group_name") ?: "Bilibili Suit"
        val assetBytes = linkedMapOf<String, ByteArray>()

        fun fetchProperty(property: String, targetPath: String) {
            val url = properties.stringOrNull(property) ?: return
            assetBytes[targetPath] = fetchRemotePackage(url, remotePackageFetcher)
        }

        val assets = when (partId) {
            3 -> {
                val animationUrl = properties.stringOrNull("image_ani")
                when {
                    animationUrl?.substringBefore('?')?.endsWith(".json", true) == true ->
                        fetchProperty("image_ani", "assets/like_effect.json")
                    animationUrl?.substringBefore('?')?.endsWith(".webp", true) == true ->
                        fetchProperty("image_ani", "assets/like_effect.webp")
                    animationUrl?.substringBefore('?')?.endsWith(".png", true) == true ->
                        fetchProperty("image_ani", "assets/like_effect.png")
                }
                val previewUrl = properties.stringOrNull("image_preview")
                if (previewUrl != null) {
                    val extension = if (previewUrl.substringBefore('?').endsWith(".png", true)) "png" else "jpg"
                    fetchProperty("image_preview", "assets/like_effect_preview.$extension")
                }
                UiSkinAssets(
                    likeEffectAnimation = assetBytes.keys.firstOrNull {
                        it.substringAfterLast('/').startsWith("like_effect.")
                    },
                    likeEffectPreview = assetBytes.keys.firstOrNull {
                        it.substringAfterLast('/').startsWith("like_effect_preview.")
                    },
                )
            }
            10 -> {
                fetchProperty("loading_url", "assets/loading.webp")
                fetchProperty("loading_frame_url", "assets/loading_frame.png")
                UiSkinAssets(
                    loadingAnimation = "assets/loading.webp".takeIf(assetBytes::containsKey),
                    loadingFrame = "assets/loading_frame.png".takeIf(assetBytes::containsKey),
                )
            }
            else -> {
                fetchProperty("icon", "assets/progress_icon.json")
                fetchProperty("drag_icon", "assets/progress_drag_icon.json")
                fetchProperty("static_icon_image", "assets/progress_static_icon.png")
                UiSkinAssets(
                    playerProgressIcon = "assets/progress_icon.json".takeIf(assetBytes::containsKey),
                    playerProgressDraggingIcon = "assets/progress_drag_icon.json".takeIf(assetBytes::containsKey),
                    playerProgressStaticIcon = "assets/progress_static_icon.png".takeIf(assetBytes::containsKey),
                )
            }
        }
        if (assets.declaredPaths().isEmpty()) {
            throw IllegalArgumentException("该装扮效果没有当前版本可渲染的资源")
        }
        val surface = when (partId) {
            3 -> UiSkinSurface.LIKE_EFFECT
            10 -> UiSkinSurface.LOADING_INDICATOR
            else -> UiSkinSurface.PLAYER_PROGRESS
        }
        val manifest = UiSkinManifest(
            formatVersion = 1,
            skinId = "local.bilibili_suit.part_${partId}_${itemId}".safeUiSkinFileSegment(),
            displayName = displayName,
            version = properties.stringOrNull("ver") ?: "1.0.0",
            apiVersion = 1,
            author = "BiliPai local converter",
            surfaces = setOf(surface),
            assets = assets,
            styleSourceName = "sjh8130/BilibiliSuitCollection",
            styleSourceUrl = BILIBILI_SUIT_COLLECTION_SOURCE_URL,
            licenseNote = BILIBILI_SKIN_LICENSE_NOTE,
            communityShareable = false,
            containsOfficialAssets = true,
        )
        return buildBpskinPackage(manifest, assetBytes)
    }

    private fun fetchRemotePackage(
        packageUrl: String,
        remotePackageFetcher: ((String) -> ByteArray)?
    ): ByteArray {
        val normalizedUrl = normalizeSkinPackageUrl(packageUrl)
        return remotePackageFetcher?.invoke(normalizedUrl)
            ?: throw IllegalArgumentException("皮肤 JSON 需要下载 package_url，请检查网络后重试")
    }

    private fun selectOrderedThemeJsons(entries: Map<String, ByteArray>): List<ByteArray> {
        return entries
            .filterKeys { path ->
                val name = path.substringAfterLast("/")
                path.endsWith(".json") && name != "个性装扮-套装.json" && name != "原始.json"
            }
            .toList()
            .sortedWith(compareBy(
                { (path, _) ->
                    val parent = path.parentName()
                    if (parent.isNotEmpty() && path.substringAfterLast("/") == "$parent.json") 0 else 1
                },
                { (path, _) -> if (path.substringAfterLast("/") in setOf("data.json", "skin_suit.json")) 1 else 0 },
                { (path, _) -> if (path.substringAfterLast("/") == "个性装扮.json") 1 else 0 },
                { (path, _) -> path }
            ))
            .map { it.second }
    }

    private fun selectPackageZipOrNull(entries: Map<String, ByteArray>): ByteArray? {
        return entries
            .filterKeys { path ->
                val name = path.substringAfterLast("/")
                name.endsWith("_package.zip") ||
                    (name.startsWith("package_url") && name.endsWith(".zip"))
            }
            .toList()
            .sortedWith(compareBy(
                { (path, _) -> if (path.substringAfterLast("/").endsWith("_package.zip")) 0 else 1 },
                { (path, _) -> path }
            ))
            .map { (_, bytes) -> bytes }
            .firstOrNull()
    }

    private fun parseMergedThemeMetadata(
        themeJsons: List<ByteArray>,
        suitDataJson: ByteArray?,
    ): BilibiliSkinTheme {
        val primary = themeJsons.firstNotNullOfOrNull { bytes ->
            runCatching { parseThemeJson(bytes) }.getOrNull()
        } ?: BilibiliSkinTheme(
            id = "local_package",
            name = "本地装扮资源包",
            version = "1.0.0",
        )
        if (suitDataJson == null) return primary
        val suitParsed = runCatching { parseThemeJson(suitDataJson) }.getOrNull() ?: return primary
        return primary.copy(
            id = primary.id ?: suitParsed.id,
            name = primary.name.takeIf { it != "Bilibili Skin" } ?: suitParsed.name,
            imageCoverColor = primary.imageCoverColor ?: suitParsed.imageCoverColor,
            fanNoColor = primary.fanNoColor ?: suitParsed.fanNoColor,
            commentFanNoColor = primary.commentFanNoColor ?: suitParsed.commentFanNoColor,
            color = primary.color ?: suitParsed.color,
            colorSecondPage = primary.colorSecondPage ?: suitParsed.colorSecondPage,
            tailColor = primary.tailColor ?: suitParsed.tailColor,
            tailSelectedColor = primary.tailSelectedColor ?: suitParsed.tailSelectedColor,
            tailIconColor = primary.tailIconColor ?: suitParsed.tailIconColor,
            tailIconDarkColor = primary.tailIconDarkColor ?: suitParsed.tailIconDarkColor,
            tailIconSelectedColor = primary.tailIconSelectedColor ?: suitParsed.tailIconSelectedColor,
            tailIconSelectedDarkColor = primary.tailIconSelectedDarkColor ?: suitParsed.tailIconSelectedDarkColor,
            sideBackgroundColor = primary.sideBackgroundColor ?: suitParsed.sideBackgroundColor,
            publishPlusColor = primary.publishPlusColor ?: suitParsed.publishPlusColor,
            publishShadeTopColor = primary.publishShadeTopColor ?: suitParsed.publishShadeTopColor,
            publishShadeBottomColor = primary.publishShadeBottomColor ?: suitParsed.publishShadeBottomColor,
            colorMode = primary.colorMode ?: suitParsed.colorMode,
        )
    }

    private fun parseJsonObjectOrFirstElement(bytes: ByteArray): JsonObject? {
        val element = runCatching { json.parseToJsonElement(bytes.decodeBilibiliSkinJson()) }.getOrNull()
            ?: return null
        return when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull() as? JsonObject
            else -> null
        }
    }

    private fun parseThemeJson(bytes: ByteArray): BilibiliSkinTheme {
        val root = parseJsonObjectOrFirstElement(bytes) ?: JsonObject(emptyMap())
        val dataObject = root.objectOrNull("data")
        val suitItems = dataObject?.objectOrNull("suit_items") ?: root.objectOrNull("suit_items")
        val suitProps = dataObject?.objectOrNull("properties") ?: root.objectOrNull("properties")
        val themeObject = root.resolveThemeObject()
        val properties = themeObject?.objectOrNull("properties")
            ?: themeObject?.objectOrNull("data")
            ?: dataObject?.objectOrNull("properties")?.takeIf { it.looksLikeThemeProperties() }
            ?: root.objectOrNull("properties")?.takeIf { it.looksLikeThemeProperties() }
            ?: dataObject?.takeIf { it.looksLikeThemeProperties() }
            ?: themeObject?.takeIf { it.looksLikeThemeProperties() }
            ?: dataObject
            ?: JsonObject(emptyMap())
        val id = themeObject?.stringOrNull("item_id")
            ?: themeObject?.stringOrNull("id")
            ?: root.stringOrNull("item_id")
            ?: root.stringOrNull("id")
            ?: dataObject?.stringOrNull("item_id")
            ?: dataObject?.stringOrNull("id")
            ?: properties.stringOrNull("item_id")
            ?: properties.stringOrNull("id")
        val name = themeObject?.stringOrNull("name")
            ?: root.stringOrNull("name")
            ?: dataObject?.stringOrNull("name")
            ?: "Bilibili Skin"
        val version = themeObject?.stringOrNull("ver")
            ?: root.stringOrNull("ver")
            ?: properties.stringOrNull("ver")
            ?: dataObject?.stringOrNull("ver")
            ?: "1.0.0"
        val cardFanColor = suitItems?.get("card")?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull()?.let { it as? JsonObject }
            ?.objectOrNull("properties")?.stringOrNull("fan_no_color")
        val cardBgFanColor = suitItems?.get("card_bg")?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull()?.let { it as? JsonObject }
            ?.objectOrNull("properties")?.stringOrNull("fan_no_color")

        return BilibiliSkinTheme(
            id = id,
            name = name,
            version = version,
            imageCoverColor = suitProps?.stringOrNull("image_cover_color")
                ?.takeIf { it.isNotBlank() },
            fanNoColor = cardFanColor?.takeIf { it.isNotBlank() },
            commentFanNoColor = cardBgFanColor?.takeIf { it.isNotBlank() },
            color = properties.stringOrNull("color")?.takeIf { it.isNotBlank() },
            colorSecondPage = properties.stringOrNull("color_second_page")?.takeIf { it.isNotBlank() },
            tailColor = properties.stringOrNull("tail_color")?.takeIf { it.isNotBlank() },
            tailSelectedColor = properties.stringOrNull("tail_color_selected")?.takeIf { it.isNotBlank() },
            tailIconColor = properties.stringOrNull("tail_icon_color")?.takeIf { it.isNotBlank() },
            tailIconDarkColor = properties.stringOrNull("tail_icon_color_dark")?.takeIf { it.isNotBlank() },
            tailIconSelectedColor = properties.stringOrNull("tail_icon_color_selected")?.takeIf { it.isNotBlank() },
            tailIconSelectedDarkColor = properties.stringOrNull("tail_icon_color_selected_dark")?.takeIf { it.isNotBlank() },
            sideBackgroundColor = properties.stringOrNull("side_bg_color")?.takeIf { it.isNotBlank() },
            publishPlusColor = properties.stringOrNull("pub_btn_plus_color")?.takeIf { it.isNotBlank() },
            publishShadeTopColor = properties.stringOrNull("pub_btn_shade_color_top")?.takeIf { it.isNotBlank() },
            publishShadeBottomColor = properties.stringOrNull("pub_btn_shade_color_bottom")?.takeIf { it.isNotBlank() },
            colorMode = properties.stringOrNull("color_mode")?.takeIf { it.isNotBlank() },
            tailIconAnimated = properties.stringOrNull("tail_icon_ani")
                ?.let { it.isNotBlank() && !it.equals("false", ignoreCase = true) } == true,
            tailIconAnimationMode = properties.stringOrNull("tail_icon_ani_mode")?.takeIf { it.isNotBlank() },
            tailIconMode = properties.stringOrNull("tail_icon_mode")?.takeIf { it.isNotBlank() },
            profileVideoPlayMode = properties.stringOrNull("head_myself_mp4_play")?.takeIf { it.isNotBlank() }
        )
    }

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        val trimmed = bytes.decodeBilibiliSkinJson().trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun ByteArray.packageUrlOrNull(): String? {
        val root = parseJsonObjectOrFirstElement(this) ?: return null
        val dataObject = root.objectOrNull("data")
        val themeObject = root.resolveThemeObject()
        return themeObject?.stringOrNull("package_url")
            ?: themeObject?.stringOrNull("packageUrl")
            ?: themeObject?.objectOrNull("properties")?.stringOrNull("package_url")
            ?: themeObject?.objectOrNull("properties")?.stringOrNull("packageUrl")
            ?: dataObject?.stringOrNull("package_url")
            ?: dataObject?.stringOrNull("packageUrl")
            ?: root.stringOrNull("package_url")
            ?: root.stringOrNull("packageUrl")
            ?: root.findStringDeep("package_url", "packageUrl")
    }

    private fun ByteArray.decodeBilibiliSkinJson(): String {
        val utf8 = decodeToString()
        return if ('\uFFFD' in utf8) {
            String(this, Charset.forName("GB18030"))
        } else {
            utf8
        }
    }

    private fun outerSuitAssets(
        outerEntries: Map<String, ByteArray>,
        packageEntries: Map<String, ByteArray>,
        suitName: String,
    ): Map<String, ByteArray> {
        val supplemental = linkedMapOf<String, ByteArray>()

        // 1. Suit cover / preview image
        outerEntries.entries.firstOrNull { (path, _) ->
            val name = path.substringAfterLast('/')
            name.matches(Regex("image_cover\\.(png|jpg|webp)")) ||
                (path.startsWith("skin/") && name.matches(Regex("image_(cover|preview)[0-9a-f]{40}\\.(png|jpg|webp)"))) ||
                name == "preview.jpg" || name == "preview.png"
        }?.let { (path, bytes) ->
            supplemental["cover_image.${path.substringAfterLast('.')}"] = bytes
        }

        // 2. Fallback hashed skin/* files (e.g. head_myself_squared_bg<sha1>.png, head_bg<sha1>.jpg, tail_icon_*<sha1>.png)
        val knownSkinStems = listOf(
            "head_myself_bg",
            "head_myself_squared_bg",
            "head_bg",
            "head_tab_bg",
            "tail_bg",
            "side_bg",
            "side_bg_bottom",
            "tail_icon_pub_btn_bg",
            "tail_icon_selected_pub_btn_bg",
            "tail_icon_channel",
            "tail_icon_selected_channel",
        ) + iconMapping.keys + selectedIconMapping.keys
        knownSkinStems.forEach { stem ->
            if (packageEntries.keys.any { it.substringAfterLast('/').matches(Regex("$stem\\.(png|jpg)")) }) {
                return@forEach
            }
            val entry = outerEntries.entries.firstOrNull { (path, _) ->
                path.startsWith("skin/") &&
                    path.substringAfterLast('/').matches(Regex("$stem[0-9a-f]{40,64}\\.(png|jpg)"))
            } ?: return@forEach
            val extension = entry.key.substringAfterLast('.')
            supplemental["$stem.$extension"] = entry.value
        }

        val hasBracketedEmojis = outerEntries.keys.any { path ->
            val n = path.substringAfterLast('/')
            path.startsWith("emoji_package/") && n.startsWith("[") && n.contains("]")
        }

        outerEntries.forEach { (path, bytes) ->
            val name = path.substringAfterLast('/')
            when {
                // 3. Space backgrounds (hashed or canonical)
                path.startsWith("space_bg/") &&
                    name.matches(Regex("image[1-9][0-9]*_(portrait|landscape)([a-f0-9]{40,64})?\\.(png|jpg)")) -> {
                    val canonicalStem = name.substringBeforeLast('.').replace(Regex("[a-f0-9]{40,64}$"), "")
                    val canonicalName = "$canonicalStem.${name.substringAfterLast('.')}"
                    if (packageEntries.keys.none { it.substringAfterLast('/') == canonicalName }) {
                        supplemental["space_bg/$canonicalName"] = bytes
                    }
                }
                path.startsWith("space_bg/") && name.endsWith(".mp4", ignoreCase = true) -> {
                    if (name.contains("vertical", ignoreCase = true) || name.contains("portrait", ignoreCase = true)) {
                        supplemental["space_video_vertical.mp4"] = bytes
                    } else {
                        supplemental["space_video_horizontal.mp4"] = bytes
                    }
                }
                // 4. Fan collection cards (card/*.png)
                path.startsWith("card/") && name.matches(Regex(".+\\.(png|jpg|webp)")) -> {
                    val ext = name.substringAfterLast('.')
                    val stem = name.substringBeforeLast('.')
                    if (stem.endsWith("粉丝")) {
                        supplemental.putIfAbsent("fan_share_card.$ext", bytes)
                    } else {
                        supplemental.putIfAbsent("fan_card.$ext", bytes)
                    }
                }
                // 5. Comment sailing banner (card_bg/*.png)
                path.startsWith("card_bg/") && name.matches(Regex(".+\\.(png|jpg|webp)")) -> {
                    val ext = name.substringAfterLast('.')
                    supplemental.putIfAbsent("comment_card_bg.$ext", bytes)
                }
                // 6. Emoji package (strictly prefer [装扮名_表情] items, skipping suit cover banner)
                path.startsWith("emoji_package/") &&
                    !name.endsWith("_512x512.webp") &&
                    name.matches(Regex(".+\\.(png|webp)")) -> {
                    val stem = name.substringBeforeLast('.')
                    val isActualEmoji = if (hasBracketedEmojis) {
                        stem.startsWith("[") && stem.endsWith("]")
                    } else {
                        stem != suitName && stem != "弥咔Mika"
                    }
                    if (isActualEmoji && packageEntries.keys.none { it.substringAfterLast('/') == name }) {
                        supplemental["emoji_package/$name"] = bytes
                    }
                }
            }
        }
        return supplemental
    }

    private fun buildAssetBytes(packageEntries: Map<String, ByteArray>): Map<String, ByteArray> {
        val assetBytes = linkedMapOf<String, ByteArray>()
        firstExisting(packageEntries, "cover_image.png", "cover_image.jpg", "cover_image.webp")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "tail_bg.png", "tail_bg.jpg")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "side_bg_bottom.png", "side_bg_bottom.jpg")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "head_bg.jpg", "head_bg.png")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "head_tab_bg.jpg", "head_tab_bg.png")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "side_bg.jpg", "side_bg.png")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "head_myself_bg.jpg", "head_myself_bg.png")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(
            packageEntries,
            "head_myself_squared_bg.jpg",
            "head_myself_squared_bg.png"
        )?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "fan_card.png", "fan_card.jpg", "fan_card.webp")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "fan_share_card.png", "fan_share_card.jpg", "fan_share_card.webp")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "comment_card_bg.png", "comment_card_bg.jpg", "comment_card_bg.webp")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "space_video_vertical.mp4")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "space_video_horizontal.mp4")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        packageEntries.forEach { (path, bytes) ->
            val name = path.substringAfterLast('/')
            if (
                path.startsWith("space_bg/") &&
                name.matches(Regex("image[1-9][0-9]*_(portrait|landscape)\\.(png|jpg)"))
            ) {
                assetBytes["assets/space_bg/$name"] = bytes
            }
            if (
                path.startsWith("emoji_package/") &&
                !name.endsWith("_512x512.webp") &&
                name.matches(Regex(".+\\.(png|webp)"))
            ) {
                assetBytes["assets/emojis/$name"] = bytes
            }
        }
        firstExisting(packageEntries, "head_myself_mp4_bg.mp4")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        listOf(
            "loading.json", "loading.webp", "loading.png", "loading_frame.png",
            "like_effect.json", "like_effect.webp", "like_effect.png",
            "like_effect_preview.png", "like_effect_preview.jpg",
            "progress_icon.json", "progress_icon.webp", "progress_icon.png",
            "progress_drag_icon.json", "progress_drag_icon.webp", "progress_drag_icon.png",
            "progress_static_icon.png", "progress_static_icon.webp"
        ).forEach { assetName ->
            firstExisting(packageEntries, assetName)?.let { (path, bytes) ->
                assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
            }
        }
        firstExisting(packageEntries, "tail_icon_pub_btn_bg.png", "tail_icon_pub_btn_bg.jpg")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(
            packageEntries,
            "tail_icon_selected_pub_btn_bg.png",
            "tail_icon_selected_pub_btn_bg.jpg"
        )?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(packageEntries, "tail_icon_channel.png", "tail_icon_channel.jpg")?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        firstExisting(
            packageEntries,
            "tail_icon_selected_channel.png",
            "tail_icon_selected_channel.jpg"
        )?.let { (path, bytes) ->
            assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
        }
        iconMapping.forEach { (packageStem, _) ->
            firstExisting(packageEntries, "$packageStem.png", "$packageStem.jpg")?.let { (path, bytes) ->
                assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
            }
        }
        selectedIconMapping.forEach { (packageStem, _) ->
            firstExisting(packageEntries, "$packageStem.png", "$packageStem.jpg")?.let { (path, bytes) ->
                assetBytes["assets/${path.substringAfterLast("/")}"] = bytes
            }
        }
        return assetBytes
    }

    private fun buildManifest(
        theme: BilibiliSkinTheme,
        assetPaths: Set<String>
    ): UiSkinManifest {
        val iconPaths = iconMapping.mapNotNull { (packageStem, hostKey) ->
            val path = assetPaths.firstOrNull {
                it.endsWith("$packageStem.png") || it.endsWith("$packageStem.jpg")
            }
            path?.let { hostKey to it }
        }.toMap() + selectedIconMapping.mapNotNull { (packageStem, hostKey) ->
            val path = assetPaths.firstOrNull {
                it.endsWith("$packageStem.png") || it.endsWith("$packageStem.jpg")
            }
            path?.let { hostKey to it }
        }.toMap()
        val surfaces = buildSet {
            if (
                assetPaths.any { it.contains("tail_bg") } ||
                iconPaths.isNotEmpty() ||
                theme.tailIconMode.equals("color", ignoreCase = true) ||
                theme.tailIconColor != null ||
                theme.tailIconSelectedColor != null ||
                theme.tailColor != null ||
                theme.tailSelectedColor != null
            ) {
                add(UiSkinSurface.HOME_BOTTOM_BAR)
            }
            if (
                assetPaths.any { it.contains("head_bg") || it.contains("head_tab_bg") } ||
                theme.color != null ||
                theme.colorSecondPage != null
            ) {
                add(UiSkinSurface.HOME_TOP_CHROME)
            }
            if (
                assetPaths.any { it.contains("side_bg.") || it.contains("side_bg_bottom") } ||
                theme.sideBackgroundColor != null
            ) {
                add(UiSkinSurface.HOME_DRAWER)
            }
            if (
                assetPaths.any {
                    it.contains("head_myself_") ||
                        it.startsWith("assets/space_bg/") ||
                        it.contains("space_video_")
                }
            ) {
                add(UiSkinSurface.PROFILE)
            }
            if (
                assetPaths.any { it.contains("tail_icon_pub_btn_bg") } ||
                theme.publishPlusColor != null ||
                theme.publishShadeTopColor != null ||
                theme.publishShadeBottomColor != null
            ) {
                add(UiSkinSurface.DYNAMIC_PUBLISH)
            }
            if (
                assetPaths.any {
                    it.contains("comment_card_bg") ||
                        it.contains("fan_card") ||
                        it.contains("fan_share_card")
                }
            ) {
                add(UiSkinSurface.COMMENT_CARD)
            }
            if (
                assetPaths.any {
                    it.endsWith("loading.json") || it.endsWith("loading.webp") || it.endsWith("loading.png")
                }
            ) {
                add(UiSkinSurface.LOADING_INDICATOR)
            }
            if (assetPaths.any { it.substringAfterLast('/').startsWith("like_effect") }) {
                add(UiSkinSurface.LIKE_EFFECT)
            }
            if (assetPaths.any { it.substringAfterLast('/').startsWith("progress_") }) {
                add(UiSkinSurface.PLAYER_PROGRESS)
            }
        }
        return UiSkinManifest(
            formatVersion = 1,
            skinId = "local.bilibili_skin.${theme.safeSkinIdSegment()}",
            displayName = theme.name,
            version = theme.version,
            apiVersion = 1,
            author = "BiliPai local converter",
            surfaces = surfaces,
            assets = UiSkinAssets(
                coverImage = assetPaths.firstOrNull {
                    it.endsWith("cover_image.png") || it.endsWith("cover_image.jpg") || it.endsWith("cover_image.webp")
                },
                bottomBarTrim = assetPaths.firstOrNull {
                    it.endsWith("tail_bg.png") || it.endsWith("tail_bg.jpg")
                },
                drawerBottomTrim = assetPaths.firstOrNull {
                    it.endsWith("side_bg_bottom.png") || it.endsWith("side_bg_bottom.jpg")
                },
                topAtmosphere = assetPaths.firstOrNull {
                    it.endsWith("head_bg.jpg") || it.endsWith("head_bg.png")
                },
                homeTopTabBackground = assetPaths.firstOrNull {
                    it.endsWith("head_tab_bg.jpg") || it.endsWith("head_tab_bg.png")
                },
                homeSideBackground = assetPaths.firstOrNull {
                    it.endsWith("side_bg.jpg") || it.endsWith("side_bg.png")
                },
                homeProfileBackground = assetPaths.firstOrNull {
                    it.endsWith("head_myself_bg.jpg") || it.endsWith("head_myself_bg.png")
                },
                homeProfileSquaredBackground = assetPaths.firstOrNull {
                    it.endsWith("head_myself_squared_bg.jpg") || it.endsWith("head_myself_squared_bg.png")
                },
                homeProfileVideoBackground = assetPaths.firstOrNull {
                    it.endsWith("head_myself_mp4_bg.mp4")
                },
                dynamicPublishIcon = assetPaths.firstOrNull {
                    it.endsWith("tail_icon_pub_btn_bg.png") || it.endsWith("tail_icon_pub_btn_bg.jpg")
                },
                dynamicPublishSelectedIcon = assetPaths.firstOrNull {
                    it.endsWith("tail_icon_selected_pub_btn_bg.png") ||
                        it.endsWith("tail_icon_selected_pub_btn_bg.jpg")
                },
                fanCardImage = assetPaths.firstOrNull {
                    it.endsWith("fan_card.png") || it.endsWith("fan_card.jpg") || it.endsWith("fan_card.webp")
                },
                fanShareCardImage = assetPaths.firstOrNull {
                    it.endsWith("fan_share_card.png") || it.endsWith("fan_share_card.jpg") || it.endsWith("fan_share_card.webp")
                },
                commentCardBgImage = assetPaths.firstOrNull {
                    it.endsWith("comment_card_bg.png") || it.endsWith("comment_card_bg.jpg") || it.endsWith("comment_card_bg.webp")
                },
                spaceVideoVertical = assetPaths.firstOrNull { it.endsWith("space_video_vertical.mp4") },
                spaceVideoHorizontal = assetPaths.firstOrNull { it.endsWith("space_video_horizontal.mp4") },
                loadingAnimation = assetPaths.firstOrNull {
                    it.endsWith("loading.json") || it.endsWith("loading.webp") || it.endsWith("loading.png")
                },
                loadingFrame = assetPaths.firstOrNull { it.endsWith("loading_frame.png") },
                likeEffectAnimation = assetPaths.firstOrNull {
                    it.endsWith("like_effect.json") || it.endsWith("like_effect.webp") ||
                        it.endsWith("like_effect.png")
                },
                likeEffectPreview = assetPaths.firstOrNull {
                    it.endsWith("like_effect_preview.png") || it.endsWith("like_effect_preview.jpg")
                },
                playerProgressIcon = assetPaths.firstOrNull {
                    it.endsWith("progress_icon.json") || it.endsWith("progress_icon.webp") ||
                        it.endsWith("progress_icon.png")
                },
                playerProgressDraggingIcon = assetPaths.firstOrNull {
                    it.endsWith("progress_drag_icon.json") || it.endsWith("progress_drag_icon.webp") ||
                        it.endsWith("progress_drag_icon.png")
                },
                playerProgressStaticIcon = assetPaths.firstOrNull {
                    it.endsWith("progress_static_icon.png") || it.endsWith("progress_static_icon.webp")
                },
                bottomBarIcons = iconPaths,
                spaceBackgrounds = buildSpaceBackgrounds(assetPaths),
                emojiImages = assetPaths.mapNotNull { path ->
                    if (!path.startsWith("assets/emojis/")) return@mapNotNull null
                    path.substringAfterLast('/').substringBeforeLast('.').let { name -> name to path }
                }.toMap(),
            ),
            colors = UiSkinColorTokens(
                coverTint = theme.imageCoverColor.validColorOrNull(),
                // 注意：B 站装扮 JSON 中 tail_color / color 是前景文字色，color_second_page / image_cover_color 才是底板背景色！
                bottomBarTrimTint = (theme.colorSecondPage ?: theme.imageCoverColor).validColorOrNull(),
                bottomBarIconTint = (theme.tailIconColor ?: theme.tailColor).validColorOrNull(),
                bottomBarIconDarkTint = theme.tailIconDarkColor.validColorOrNull(),
                bottomBarSelectedTint = (theme.tailIconSelectedColor ?: theme.tailSelectedColor)
                    .validColorOrNull(),
                bottomBarSelectedDarkTint = theme.tailIconSelectedDarkColor.validColorOrNull(),
                topAtmosphereTint = (theme.colorSecondPage ?: theme.imageCoverColor)
                    .validColorOrNull(),
                topForegroundTint = theme.color.validColorOrNull(),
                searchCapsuleTint = (theme.colorSecondPage ?: theme.imageCoverColor).validColorOrNull(),
                sideBackgroundTint = theme.sideBackgroundColor.validColorOrNull(),
                dynamicPublishIconTint = theme.publishPlusColor.validColorOrNull(),
                dynamicPublishShadeTop = theme.publishShadeTopColor.validColorOrNull(),
                dynamicPublishShadeBottom = theme.publishShadeBottomColor.validColorOrNull(),
                fanNoColor = theme.fanNoColor.validColorOrNull(),
                commentFanNoColor = (theme.commentFanNoColor ?: theme.fanNoColor).validColorOrNull(),
                playerProgressActiveTint = (theme.tailSelectedColor ?: theme.tailIconSelectedColor)
                    .validColorOrNull(),
                liquidGlassSurfaceTint = (theme.colorSecondPage ?: theme.imageCoverColor)
                    .validColorOrNull(),
                liquidGlassTintOpacity = 0.24f,
                colorMode = theme.colorMode ?: inferColorModeFromForegroundHex(theme.color),
            ),
            motion = UiSkinMotionTokens(
                bottomBarIconAnimated = theme.tailIconAnimated,
                bottomBarIconAnimationMode = theme.tailIconAnimationMode,
                bottomBarIconMode = theme.tailIconMode,
                profileVideoPlayMode = theme.profileVideoPlayMode,
            ),
            styleSourceName = BILIBILI_SKIN_SOURCE_NAME,
            styleSourceUrl = BILIBILI_SKIN_SOURCE_URL,
            licenseNote = BILIBILI_SKIN_LICENSE_NOTE,
            communityShareable = false,
            containsOfficialAssets = true
        )
    }

    private fun inferColorModeFromForegroundHex(foregroundHex: String?): String? {
        val hex = foregroundHex?.trim()?.removePrefix("#") ?: return null
        val rgb = when (hex.length) {
            6 -> hex
            8 -> hex.substring(2)
            else -> return null
        }
        val intVal = rgb.toLongOrNull(16) ?: return null
        val r = ((intVal shr 16) and 0xFF) / 255.0
        val g = ((intVal shr 8) and 0xFF) / 255.0
        val b = (intVal and 0xFF) / 255.0
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        // Bilibili skin `color` represents foreground text color on `head_bg`:
        // dark text (< 0.42) means `head_bg` is light; bright text (> 0.72) means `head_bg` is dark.
        return when {
            lum < 0.42 -> "light"
            lum > 0.72 -> "dark"
            else -> null
        }
    }

    private fun buildSpaceBackgrounds(assetPaths: Set<String>): List<UiSkinSpaceBackground> {
        val pattern = Regex("assets/space_bg/image([1-9][0-9]*)_(portrait|landscape)\\.(png|jpg)")
        return assetPaths.mapNotNull { path ->
            pattern.matchEntire(path)?.let { match -> match.groupValues[1] to (match.groupValues[2] to path) }
        }.groupBy({ it.first }, { it.second })
            .toSortedMap(compareBy { it.toInt() })
            .values
            .map { variants ->
                UiSkinSpaceBackground(
                    portrait = variants.firstOrNull { it.first == "portrait" }?.second,
                    landscape = variants.firstOrNull { it.first == "landscape" }?.second,
                )
            }
    }

    private fun buildBpskinPackage(
        manifest: UiSkinManifest,
        assetBytesByPath: Map<String, ByteArray>
    ): ByteArray {
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putStableEntry("skin-manifest.json")
                zip.write(json.encodeToString(UiSkinManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
                assetBytesByPath.forEach { (path, bytes) ->
                    zip.putStableEntry(path)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    private fun stripSingleRootFolderIfPresent(entries: Map<String, ByteArray>): Map<String, ByteArray> {
        if (entries.isEmpty()) return entries
        val knownRootDirs = setOf("assets", "skin", "space_bg", "card", "card_bg", "emoji_package")
        if (entries.keys.any { '/' !in it || it.substringBefore('/') in knownRootDirs }) {
            return entries
        }
        val firstSegments = entries.keys.mapTo(linkedSetOf()) { it.substringBefore('/') }
        if (firstSegments.size != 1) return entries
        val prefix = "${firstSegments.first()}/"
        return entries.mapKeys { (key, _) -> key.removePrefix(prefix) }
    }

    private fun scanZip(
        inputBytes: ByteArray,
        illegalPathMessage: String
    ): Map<String, ByteArray> {
        return try {
            scanZipWithCharset(inputBytes, Charsets.UTF_8, illegalPathMessage)
        } catch (utf8Error: IllegalArgumentException) {
            if (utf8Error.message?.contains("MALFORMED", ignoreCase = true) == true) {
                scanZipWithCharset(inputBytes, Charset.forName("GB18030"), illegalPathMessage)
            } else {
                throw utf8Error
            }
        }
    }

    private fun scanZipWithCharset(
        inputBytes: ByteArray,
        charset: Charset,
        illegalPathMessage: String,
    ): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var entryCount = 0
        var totalBytes = 0
        ZipInputStream(ByteArrayInputStream(inputBytes), charset).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entryCount += 1
                    if (entryCount > MAX_THEME_ENTRY_COUNT) {
                        throw IllegalArgumentException("装扮存档文件数量超过 $MAX_THEME_ENTRY_COUNT")
                    }
                    val normalizedName = normalizeEntryName(entry.name, illegalPathMessage)
                    val bytes = zip.readBytes()
                    totalBytes += bytes.size
                    if (totalBytes > MAX_THEME_TOTAL_BYTES) {
                        throw IllegalArgumentException("装扮存档解压后内容超过 $MAX_THEME_TOTAL_BYTES 字节")
                    }
                    if (entries.put(normalizedName, bytes) != null) {
                        throw IllegalArgumentException("装扮存档包含重复路径: ${entry.name}")
                    }
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun normalizeEntryName(rawName: String, illegalPathMessage: String): String {
        if (rawName.isBlank() || rawName.startsWith("/") || rawName.startsWith("\\")) {
            throw IllegalArgumentException("$illegalPathMessage: $rawName")
        }
        val normalized = rawName
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotEmpty() && it != "." }
            .also { parts ->
                if (parts.any { it == ".." }) {
                    throw IllegalArgumentException("$illegalPathMessage: $rawName")
                }
            }
            .joinToString("/")
        if (normalized.isBlank()) {
            throw IllegalArgumentException("$illegalPathMessage: $rawName")
        }
        return normalized
    }

    private fun firstExisting(
        entries: Map<String, ByteArray>,
        vararg names: String
    ): Pair<String, ByteArray>? {
        names.forEach { name ->
            entries.entries.firstOrNull { it.key.substringAfterLast("/") == name }?.let {
                return it.key to it.value
            }
        }
        return null
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? {
        return get(key)?.runCatching { jsonObject }?.getOrNull()
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val primitive = get(key)?.runCatching { jsonPrimitive }?.getOrNull() ?: return null
        return primitive.contentOrNull ?: primitive.booleanOrNull?.toString()
    }

    private fun JsonObject.resolveThemeObject(): JsonObject? {
        val dataObject = objectOrNull("data")
        val suitItems = dataObject?.objectOrNull("suit_items") ?: objectOrNull("suit_items")
        val suitSkinItem = suitItems?.get("skin")
            ?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull() as? JsonObject
        val skinSuitFirst = (dataObject?.get("skin_suit") ?: get("skin_suit"))
            ?.runCatching { jsonArray }?.getOrNull()
            ?.firstOrNull() as? JsonObject

        val knownCandidates = listOfNotNull(
            dataObject?.objectOrNull("user_equip"),
            objectOrNull("user_equip"),
            suitSkinItem,
            skinSuitFirst,
            dataObject?.objectOrNull("skin_suit")?.objectOrNull("item"),
            objectOrNull("skin_suit")?.objectOrNull("item"),
            dataObject?.objectOrNull("item"),
            objectOrNull("item"),
            dataObject?.objectOrNull("skin_suit"),
            objectOrNull("skin_suit"),
            dataObject?.takeIf { it.looksLikeThemeProperties() || it.objectOrNull("properties")?.looksLikeThemeProperties() == true }
        )
        return knownCandidates.firstOrNull { it.looksLikeThemeObject() }
            ?: findObjectDeep { it.looksLikeThemeObject() && it.objectOrNull("properties")?.looksLikeThemeProperties() == true }
            ?: knownCandidates.firstOrNull()
            ?: findObjectDeep { it.looksLikeThemeObject() }
    }

    private fun JsonObject.looksLikeThemeObject(): Boolean {
        return stringOrNull("item_id") != null ||
            stringOrNull("id") != null ||
            stringOrNull("name") != null ||
            objectOrNull("properties") != null
    }

    private fun JsonObject.looksLikeThemeProperties(): Boolean {
        return stringOrNull("color") != null ||
            stringOrNull("color_second_page") != null ||
            stringOrNull("tail_color") != null ||
            stringOrNull("ver") != null ||
            stringOrNull("package_url") != null ||
            stringOrNull("packageUrl") != null
    }

    private fun JsonObject.findStringDeep(vararg keys: String): String? {
        keys.forEach { key ->
            stringOrNull(key)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        values.forEach { value ->
            when (value) {
                is JsonObject -> value.findStringDeep(*keys)?.let { return it }
                else -> runCatching { value.jsonArray }.getOrNull()?.forEach { item ->
                    (item as? JsonObject)?.findStringDeep(*keys)?.let { return it }
                }
            }
        }
        return null
    }

    private fun JsonObject.findObjectDeep(predicate: (JsonObject) -> Boolean): JsonObject? {
        if (predicate(this)) return this
        values.forEach { value ->
            when (value) {
                is JsonObject -> value.findObjectDeep(predicate)?.let { return it }
                else -> runCatching { value.jsonArray }.getOrNull()?.forEach { item ->
                    (item as? JsonObject)?.findObjectDeep(predicate)?.let { return it }
                }
            }
        }
        return null
    }

    private fun String.parentName(): String {
        return substringBeforeLast("/", "").substringAfterLast("/")
    }

    private fun String?.validColorOrNull(): String? {
        val value = this?.trim()?.removePrefix("#") ?: return null
        return if (Regex("[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?").matches(value)) "#$value" else null
    }

    private fun BilibiliSkinTheme.safeSkinIdSegment(): String {
        val nameSlug = name
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .trim('_')
            .lowercase()
        val idSlug = id
            ?.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            ?.trim('_')
            ?.lowercase()
        return idSlug?.takeIf { it.isNotBlank() }
            ?: nameSlug.takeIf { it.isNotBlank() }
            ?: "theme"
    }
}

internal fun normalizeSkinPackageUrl(packageUrl: String): String {
    val trimmed = packageUrl.trim()
    return when {
        trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("http://") && trimmed.contains("hdslb.com") -> {
            "https://" + trimmed.removePrefix("http://")
        }
        else -> throw IllegalArgumentException("皮肤 package_url 不是安全 HTTPS 链接")
    }
}

private data class BilibiliSkinTheme(
    val id: String?,
    val name: String,
    val version: String,
    val imageCoverColor: String? = null,
    val fanNoColor: String? = null,
    val commentFanNoColor: String? = null,
    val color: String? = null,
    val colorSecondPage: String? = null,
    val tailColor: String? = null,
    val tailSelectedColor: String? = null,
    val tailIconColor: String? = null,
    val tailIconDarkColor: String? = null,
    val tailIconSelectedColor: String? = null,
    val tailIconSelectedDarkColor: String? = null,
    val sideBackgroundColor: String? = null,
    val publishPlusColor: String? = null,
    val publishShadeTopColor: String? = null,
    val publishShadeBottomColor: String? = null,
    val colorMode: String? = null,
    val tailIconAnimated: Boolean = false,
    val tailIconAnimationMode: String? = null,
    val tailIconMode: String? = null,
    val profileVideoPlayMode: String? = null
)

private fun ZipOutputStream.putStableEntry(name: String) {
    putNextEntry(
        ZipEntry(name).apply {
            time = 0L
        }
    )
}
