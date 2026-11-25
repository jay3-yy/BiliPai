package com.android.purebilibili.core.plugin.bpx

import com.android.purebilibili.core.plugin.json.Rule
import kotlinx.serialization.Serializable

@Serializable
data class BpxAuthor(
    val name: String,
    val biliMid: Long? = null,
    val github: String? = null,
    val bio: String? = null,
    val avatar: String? = null,
)

@Serializable
data class BpxMount(
    val slot: String,
    val id: String = "main",
    val title: String = "",
    val entry: String? = null,
    val mode: String? = null,
)

@Serializable
data class BpxPermissions(
    val videoContext: List<String> = emptyList(),
    val networkDomains: List<String> = emptyList(),
    val storage: Boolean = false,
    val externalPlayback: Boolean = false,
)

@Serializable
data class BpxSettingOption(
    val label: String,
    val value: String,
)

@Serializable
data class BpxSettingField(
    val key: String,
    val type: String,
    val title: String,
    val description: String? = null,
    val required: Boolean = false,
    val placeholder: String? = null,
    val options: List<BpxSettingOption> = emptyList(),
)

@Serializable
data class BpxSkinModePalette(
    val topAtmosphereTint: String? = null,
    val topForegroundTint: String? = null,
    val bottomBarTrimTint: String? = null,
    val bottomBarIconTint: String? = null,
    val bottomBarSelectedTint: String? = null,
    val seekbarAccent: String? = null,
)

@Serializable
data class BpxLiquidGlassPalette(
    val surfaceTint: String? = null,
    val refractionAccent: String? = null,
    val tintOpacity: Float? = null,
)

@Serializable
data class BpxSkinTokens(
    val light: BpxSkinModePalette? = null,
    val dark: BpxSkinModePalette? = null,
    val liquidGlass: BpxLiquidGlassPalette? = null,
)

@Serializable
data class BpxSkinSpec(
    val surfaces: List<String> = listOf("HOME_TOP_CHROME", "HOME_BOTTOM_BAR"),
    val tokens: BpxSkinTokens = BpxSkinTokens(),
)

@Serializable
data class BpxManifest(
    val bpxVersion: Int = 1,
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val license: String? = null,
    val licenseNote: String? = null,
    val author: BpxAuthor,
    val mounts: List<BpxMount> = emptyList(),
    val permissions: BpxPermissions = BpxPermissions(),
    val settings: List<BpxSettingField> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val skin: BpxSkinSpec? = null,
)

@Serializable
data class InstalledBpxRecord(
    val manifest: BpxManifest,
    val format: String,
    val sha256: String,
    val sourceFileName: String,
    val installedAtMs: Long,
    val enabled: Boolean = true,
    val trippedCount: Int = 0,
    val settingValues: Map<String, String> = emptyMap(),
)

data class BpxPreviewPackage(
    val manifest: BpxManifest,
    val format: String,
    val rawSource: String,
    val sha256: String,
) {
    fun slotDisplayLabels(): List<String> = manifest.mounts.map { mount ->
        val slotLabel = when (mount.slot) {
            "feed.pipe" -> "首页推荐/搜索卡片管线"
            "detail.companion" -> "视频详情页伴侣胶囊与半屏面板"
            "player.track" -> "播放器进度条区间与外挂弹幕/字幕轨"
            "home.lens" -> "首页顶栏算法透镜/频道 Tab"
            "community.craft" -> "视频评论区置顶提炼与净化"
            "theme.skin" -> "三材质（MD3 / Miuix / 液态玻璃）光学主题"
            else -> mount.slot
        }
        if (mount.title.isNotBlank()) "$slotLabel（${mount.title}）" else slotLabel
    }
}

// ============ Runtime Slot Output Contracts ============

@Serializable
data class BpxTimestampAnchor(
    val timeSec: Double = 0.0,
    val label: String = "",
    val summary: String? = null,
)

@Serializable
data class BpxKvRow(
    val label: String = "",
    val value: String = "",
)

@Serializable
data class BpxActionButton(
    val id: String = "",
    val label: String = "",
    val copyText: String? = null,
    val openUri: String? = null,
)

@Serializable
data class BpxSheetBlock(
    val type: String,
    val content: String? = null,
    val items: List<BpxTimestampAnchor> = emptyList(),
    val rows: List<BpxKvRow> = emptyList(),
    val buttons: List<BpxActionButton> = emptyList(),
)

@Serializable
data class BpxSheetDocument(
    val blocks: List<BpxSheetBlock> = emptyList(),
)

@Serializable
data class BpxPinnedCommentCard(
    val title: String = "评论提炼速览",
    val summary: String? = null,
    val timestamps: List<BpxTimestampAnchor> = emptyList(),
)

@Serializable
data class BpxCommentCraftResult(
    val pinnedCard: BpxPinnedCommentCard? = null,
    val foldRpids: List<Long> = emptyList(),
)

@Serializable
data class BpxLensScoreItem(
    val bvid: String = "",
    val score: Double = 0.0,
    val reasonBadge: String? = null,
)

@Serializable
data class BpxExternalDanmakuItem(
    val timeSec: Double = 0.0,
    val text: String = "",
    val color: String? = null,
    val mode: String = "scroll",
)

@Serializable
data class BpxPlayerTrackResult(
    val danmaku: List<BpxExternalDanmakuItem> = emptyList(),
)

// ============ Decentralized Static Registry (registry.json) ============

@Serializable
data class BpxRegistryMaintainer(
    val name: String = "",
    val biliMid: Long? = null,
    val url: String? = null,
)

@Serializable
data class BpxRegistryExtensionEntry(
    val id: String,
    val name: String,
    val version: String,
    val minAppVersionCode: Int = 1,
    val slots: List<String> = emptyList(),
    val description: String = "",
    val downloadUrl: String,
    val sha256: String = "",
    val authorName: String = "",
    val authorBiliMid: Long? = null,
    val changelog: String = "",
)

@Serializable
data class BpxRegistryManifest(
    val registryVersion: Int = 1,
    val name: String,
    val maintainer: BpxRegistryMaintainer = BpxRegistryMaintainer(),
    val updatedAt: String = "",
    val extensions: List<BpxRegistryExtensionEntry> = emptyList(),
)
