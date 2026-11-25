package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.plugin.ExternalPluginInstallDecision
import com.android.purebilibili.plugin.sdk.PluginCapability
import com.android.purebilibili.core.plugin.kotlinpkg.ExternalKotlinPluginPayloadEntry
import com.android.purebilibili.core.plugin.kotlinpkg.ExternalKotlinPluginPayloadType
import com.android.purebilibili.core.plugin.kotlinpkg.InstalledExternalPluginPackage
import com.android.purebilibili.core.plugin.skin.InstalledUiSkinPackage
import com.android.purebilibili.core.plugin.skin.UiSkinManifest
import com.android.purebilibili.core.plugin.skin.UiSkinPackagePreview
import com.android.purebilibili.core.plugin.skin.UiSkinSurface

data class PluginCapabilityUiModel(
    val capability: PluginCapability,
    val label: String,
    val description: String,
    val requiresExplicitApproval: Boolean
)

data class ExternalPluginInstallPreviewUiModel(
    val title: String,
    val subtitle: String,
    val packageHashText: String,
    val signerText: String,
    val sensitiveCapabilityLabels: List<String>
)

data class ExternalKotlinPluginEntryUiState(
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val enabled: Boolean,
)

fun resolveExternalKotlinPluginEntryUiState(): ExternalKotlinPluginEntryUiState =
    ExternalKotlinPluginEntryUiState(
        title = "Kotlin 插件包（.bpplugin）",
        subtitle = "开发中功能：暂无法修改软件机制或运行插件代码，已暂时锁定",
        badgeText = "开发中",
        enabled = false,
    )

data class InstalledExternalPluginPackageUiModel(
    val pluginId: String,
    val packageSha256: String,
    val title: String,
    val subtitle: String,
    val stateText: String,
    val packageHashText: String,
    val signerText: String,
    val grantedCapabilityLabels: List<String>
)

data class UiSkinPackagePreviewUiModel(
    val title: String,
    val subtitle: String,
    val packageHashText: String,
    val assetSummaryText: String,
    val sourceText: String,
    val licenseText: String,
    val shareText: String,
    val officialAssetText: String
)

data class InstalledUiSkinPreviewUiModel(
    val title: String,
    val subtitle: String,
    val packageHashText: String,
    val assetSummaryText: String,
    val sourceText: String,
    val licenseText: String,
    val shareText: String,
    val officialAssetText: String,
    val canDelete: Boolean
)

data class UiSkinImagePreviewItem(
    val label: String,
    val localPath: String,
    val isVideo: Boolean = false,
)

private val capabilityOrder = listOf(
    PluginCapability.PLAYER_STATE,
    PluginCapability.PLAYER_CONTROL,
    PluginCapability.PLAYBACK_CDN,
    PluginCapability.DANMAKU_STREAM,
    PluginCapability.DANMAKU_MUTATION,
    PluginCapability.RECOMMENDATION_CANDIDATES,
    PluginCapability.LOCAL_HISTORY_READ,
    PluginCapability.LOCAL_FEEDBACK_READ,
    PluginCapability.NETWORK,
    PluginCapability.PLUGIN_STORAGE,
    PluginCapability.EXTERNAL_MEDIA_PLAYBACK
)

private val explicitApprovalCapabilities = setOf(
    PluginCapability.PLAYER_CONTROL,
    PluginCapability.PLAYBACK_CDN,
    PluginCapability.DANMAKU_MUTATION,
    PluginCapability.LOCAL_HISTORY_READ,
    PluginCapability.LOCAL_FEEDBACK_READ,
    PluginCapability.NETWORK,
    PluginCapability.PLUGIN_STORAGE,
    PluginCapability.EXTERNAL_MEDIA_PLAYBACK
)

fun resolvePluginCapabilityUiModels(
    capabilities: Set<PluginCapability>
): List<PluginCapabilityUiModel> {
    return capabilities
        .sortedBy { capabilityOrder.indexOf(it).let { index -> if (index >= 0) index else Int.MAX_VALUE } }
        .map { capability ->
            PluginCapabilityUiModel(
                capability = capability,
                label = capability.label,
                description = capability.description,
                requiresExplicitApproval = capability in explicitApprovalCapabilities
            )
        }
}

fun resolveJsonRulePluginCapabilities(type: String): Set<PluginCapability> {
    return when (type) {
        "feed" -> setOf(PluginCapability.RECOMMENDATION_CANDIDATES)
        "danmaku" -> setOf(
            PluginCapability.DANMAKU_STREAM,
            PluginCapability.DANMAKU_MUTATION
        )
        else -> emptySet()
    }
}

fun buildExternalPluginInstallPreview(
    decision: ExternalPluginInstallDecision
): ExternalPluginInstallPreviewUiModel {
    return when (decision) {
        is ExternalPluginInstallDecision.RequiresUserApproval -> ExternalPluginInstallPreviewUiModel(
            title = decision.manifest.displayName,
            subtitle = "${decision.manifest.pluginId} · v${decision.manifest.version}",
            packageHashText = "SHA-256: ${decision.packageSha256}",
            signerText = if (decision.signerTrusted) "签名可信" else "签名未信任",
            sensitiveCapabilityLabels = resolvePluginCapabilityUiModels(decision.sensitiveCapabilities)
                .map { it.label }
        )
        is ExternalPluginInstallDecision.Rejected -> ExternalPluginInstallPreviewUiModel(
            title = "插件不可安装",
            subtitle = decision.reason,
            packageHashText = "",
            signerText = "已拒绝",
            sensitiveCapabilityLabels = emptyList()
        )
    }
}

fun buildExternalPluginPayloadSummary(
    payloadEntries: List<ExternalKotlinPluginPayloadEntry>
): String {
    if (payloadEntries.isEmpty()) return "载荷：无，当前不执行"

    val labels = buildList {
        if (payloadEntries.any { it.type == ExternalKotlinPluginPayloadType.CLASSES_JAR }) {
            add("classes.jar")
        }
        if (payloadEntries.any { it.type == ExternalKotlinPluginPayloadType.CLASSES_DEX }) {
            add("classes.dex")
        }
        val otherCount = payloadEntries.count { it.type == ExternalKotlinPluginPayloadType.OTHER }
        if (otherCount > 0) {
            add("其他文件 $otherCount 个")
        }
    }
    return "载荷：${labels.joinToString("、")}，当前不执行"
}

fun buildInstalledExternalPluginUiModels(
    installedPackages: List<InstalledExternalPluginPackage>
): List<InstalledExternalPluginPackageUiModel> {
    return installedPackages
        .sortedBy { it.manifest.displayName }
        .map { installed ->
            InstalledExternalPluginPackageUiModel(
                pluginId = installed.manifest.pluginId,
                packageSha256 = installed.packageSha256,
                title = installed.manifest.displayName,
                subtitle = "${installed.manifest.pluginId} · v${installed.manifest.version}",
                stateText = if (installed.enabled) "已启用" else "已保存，暂不运行",
                packageHashText = "SHA-256: ${installed.packageSha256}",
                signerText = if (installed.signerSha256.isNullOrBlank()) "签名未信任" else "签名可信",
                grantedCapabilityLabels = resolvePluginCapabilityUiModels(installed.grantedCapabilities)
                    .map { it.label }
            )
        }
}

fun buildUiSkinPackagePreview(
    preview: UiSkinPackagePreview
): UiSkinPackagePreviewUiModel {
    val manifest = preview.manifest
    val sourceName = manifest.styleSourceName?.takeIf { it.isNotBlank() } ?: "未声明"
    val licenseNote = manifest.licenseNote?.takeIf { it.isNotBlank() } ?: "未声明分享许可"
    return UiSkinPackagePreviewUiModel(
        title = manifest.displayName,
        subtitle = "${manifest.skinId} · v${manifest.version}",
        packageHashText = "SHA-256: ${preview.packageSha256}",
        assetSummaryText = "资源：${preview.assetEntries.size} 个，当前只作为装饰输入",
        sourceText = "来源：$sourceName",
        licenseText = "授权：$licenseNote",
        shareText = if (manifest.communityShareable) "社区分享：允许" else "社区分享：未声明允许",
        officialAssetText = if (manifest.containsOfficialAssets) {
            "官方素材：声明包含，请勿作为社区包分发"
        } else {
            "官方素材：未声明包含"
        }
    )
}

fun buildInstalledUiSkinSubtitle(manifest: UiSkinManifest): String {
    val surfaceText = manifest.surfaces
        .sortedBy { it.ordinal }
        .joinToString("、") { it.displayLabel }
    return "${manifest.version} · $surfaceText · 资源装饰"
}

fun buildInstalledUiSkinPreview(
    installed: InstalledUiSkinPackage,
    isActive: Boolean
): InstalledUiSkinPreviewUiModel {
    val manifest = installed.manifest
    val surfaceText = manifest.surfaces
        .sortedBy { it.ordinal }
        .joinToString("、") { it.displayLabel }
    val sourceName = manifest.styleSourceName?.takeIf { it.isNotBlank() } ?: "未声明"
    val licenseNote = manifest.licenseNote?.takeIf { it.isNotBlank() } ?: "未声明分享许可"
    return InstalledUiSkinPreviewUiModel(
        title = installed.displayName,
        subtitle = "${manifest.version} · $surfaceText · ${if (isActive) "当前启用" else "未启用"}",
        packageHashText = "SHA-256: ${installed.packageSha256}",
        assetSummaryText = "资源：${installed.assetFiles.size} 个，已保存到本地",
        sourceText = "来源：$sourceName",
        licenseText = "授权：$licenseNote",
        shareText = if (manifest.communityShareable) "社区分享：允许" else "社区分享：未声明允许",
        officialAssetText = if (manifest.containsOfficialAssets) {
            "官方素材：声明包含，请勿作为社区包分发"
        } else {
            "官方素材：未声明包含"
        },
        canDelete = true
    )
}

fun buildUiSkinImagePreviewItems(
    assetFiles: Map<String, String>
): List<UiSkinImagePreviewItem> {
    return assetFiles
        .toList()
        .sortedWith(compareBy({ (assetPath, _) -> assetPath.previewOrder }, { (assetPath, _) -> assetPath }))
        .map { (assetPath, localPath) ->
            UiSkinImagePreviewItem(
                label = assetPath.previewLabel,
                localPath = localPath,
                isVideo = assetPath.endsWith(".mp4", ignoreCase = true),
            )
        }
}

internal val UiSkinSurface.displayLabel: String
    get() = when (this) {
        UiSkinSurface.HOME_BOTTOM_BAR -> "首页底栏"
        UiSkinSurface.HOME_TOP_CHROME -> "首页顶部"
        UiSkinSurface.HOME_DRAWER -> "首页侧栏"
        UiSkinSurface.PROFILE -> "个人页与空间"
        UiSkinSurface.DYNAMIC_PUBLISH -> "动态发布"
        UiSkinSurface.COMMENT_CARD -> "评论装扮卡"
        UiSkinSurface.LOADING_INDICATOR -> "加载动画"
        UiSkinSurface.LIKE_EFFECT -> "点赞特效"
        UiSkinSurface.PLAYER_PROGRESS -> "播放器进度条"
    }

private val String.previewOrder: Int
    get() = when {
        substringAfterLast("/").startsWith("cover_image.") -> 0
        substringAfterLast("/") in setOf("tail_bg.png", "tail_bg.jpg") -> 1
        substringAfterLast("/") in setOf("head_bg.jpg", "head_bg.png", "head_tab_bg.jpg", "head_tab_bg.png") -> 2
        substringAfterLast("/") in setOf("side_bg.jpg", "side_bg.png", "side_bg_bottom.png", "side_bg_bottom.jpg") -> 3
        substringAfterLast("/").startsWith("head_myself_") || startsWith("assets/space_bg/") -> 4
        substringAfterLast("/").startsWith("comment_card_bg.") || substringAfterLast("/").startsWith("fan_") -> 5
        substringAfterLast("/").startsWith("tail_icon_") -> 6
        startsWith("assets/emojis/") -> 8
        else -> 7
    }

private val String.previewLabel: String
    get() = when {
        substringAfterLast("/").startsWith("cover_image.") -> "套装封面"
        substringAfterLast("/") in setOf("tail_bg.png", "tail_bg.jpg") -> "底栏饰面"
        substringAfterLast("/") in setOf("side_bg_bottom.png", "side_bg_bottom.jpg") -> "侧栏底饰"
        substringAfterLast("/") in setOf("head_bg.jpg", "head_bg.png", "head_tab_bg.jpg", "head_tab_bg.png") -> "顶部氛围"
        substringAfterLast("/") in setOf("side_bg.jpg", "side_bg.png") -> "侧栏背景"
        substringAfterLast("/") in setOf("head_myself_bg.jpg", "head_myself_bg.png") -> "个人页背景"
        substringAfterLast("/") in setOf("head_myself_squared_bg.jpg", "head_myself_squared_bg.png") -> "个人页方图"
        substringAfterLast("/") == "head_myself_mp4_bg.mp4" -> "个人页动态背景"
        startsWith("assets/space_bg/") -> "空间背景"
        substringAfterLast("/").startsWith("comment_card_bg.") -> "评论装扮横幅"
        substringAfterLast("/").startsWith("fan_share_card.") -> "粉丝专属卡"
        substringAfterLast("/").startsWith("fan_card.") -> "收藏集装扮卡"
        substringAfterLast("/") in setOf("tail_icon_pub_btn_bg.png", "tail_icon_pub_btn_bg.jpg") -> "发布图标"
        substringAfterLast("/") in setOf("tail_icon_selected_pub_btn_bg.png", "tail_icon_selected_pub_btn_bg.jpg") -> "按下发布图标"
        substringAfterLast("/") in setOf("tail_icon_channel.png", "tail_icon_channel.jpg") -> "频道图标"
        substringAfterLast("/") in setOf("tail_icon_selected_channel.png", "tail_icon_selected_channel.jpg") -> "选中频道图标"
        startsWith("assets/emojis/") -> substringAfterLast("/").substringBeforeLast(".")
        substringAfterLast("/").startsWith("tail_icon_") -> "底栏图标"
        else -> "装扮资源"
    }

private val PluginCapability.label: String
    get() = when (this) {
        PluginCapability.PLAYER_STATE -> "播放器状态"
        PluginCapability.PLAYER_CONTROL -> "播放器控制"
        PluginCapability.PLAYBACK_CDN -> "播放 CDN"
        PluginCapability.DANMAKU_STREAM -> "弹幕流"
        PluginCapability.DANMAKU_MUTATION -> "弹幕改写"
        PluginCapability.RECOMMENDATION_CANDIDATES -> "推荐候选"
        PluginCapability.LOCAL_HISTORY_READ -> "观看历史"
        PluginCapability.LOCAL_FEEDBACK_READ -> "本地反馈"
        PluginCapability.NETWORK -> "网络访问"
        PluginCapability.PLUGIN_STORAGE -> "插件存储"
        PluginCapability.EXTERNAL_MEDIA_PLAYBACK -> "外部媒体播放"
        PluginCapability.FEED_SOURCE -> "订阅源"
    }

private val PluginCapability.description: String
    get() = when (this) {
        PluginCapability.PLAYER_STATE -> "读取当前播放位置、视频标识与播放状态"
        PluginCapability.PLAYER_CONTROL -> "控制跳转、跳过片段或调整播放行为"
        PluginCapability.PLAYBACK_CDN -> "读取和调整播放地址候选线路"
        PluginCapability.DANMAKU_STREAM -> "读取当前视频弹幕内容"
        PluginCapability.DANMAKU_MUTATION -> "过滤、高亮或改写弹幕显示"
        PluginCapability.RECOMMENDATION_CANDIDATES -> "读取首页候选内容用于排序或筛选"
        PluginCapability.LOCAL_HISTORY_READ -> "读取本地历史摘要和偏好画像"
        PluginCapability.LOCAL_FEEDBACK_READ -> "读取不感兴趣等本地反馈信号"
        PluginCapability.NETWORK -> "访问网络获取远程数据或服务"
        PluginCapability.PLUGIN_STORAGE -> "读写插件自己的本地配置或缓存"
        PluginCapability.EXTERNAL_MEDIA_PLAYBACK -> "播放插件返回的外部媒体链接"
        PluginCapability.FEED_SOURCE -> "声明 RSS 或 Atom 订阅地址，由应用解析并在首页展示"
    }
