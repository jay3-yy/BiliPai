// 文件路径: feature/video/ui/components/VideoSettingsPanel.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.AppTextButton as TextButton
import com.android.purebilibili.core.ui.components.AppSlider as Slider
import com.android.purebilibili.core.ui.AppAlertDialog as AlertDialog

import com.android.purebilibili.core.store.FullscreenActionCount
import com.android.purebilibili.core.store.FullscreenActionCountSettings
import com.android.purebilibili.core.store.ChapterDisplayMode
import com.android.purebilibili.feature.settings.playbackSettingTitle
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.motion.folmeExpandEnterTransition
import com.android.purebilibili.core.ui.motion.folmeExpandExitTransition
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoCamera
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.rememberAppCodecIcon
import com.android.purebilibili.core.ui.rememberAppDownloadIcon
import com.android.purebilibili.core.ui.rememberAppFlipHorizontalIcon
import com.android.purebilibili.core.ui.rememberAppFlipVerticalIcon
import com.android.purebilibili.core.ui.rememberAppGestureTapIcon
import com.android.purebilibili.core.ui.rememberAppSettingsIcon
import com.android.purebilibili.core.ui.rememberAppMusicIcon
import com.android.purebilibili.core.ui.rememberAppQualityIcon
import com.android.purebilibili.core.ui.rememberAppSpeedIcon
import com.android.purebilibili.core.ui.components.PlaybackSpeedPreferenceControl
import com.android.purebilibili.core.ui.components.LongPressSpeedPreferenceControl
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.formatPlaybackSpeed
import com.android.purebilibili.data.model.response.AiAudioInfo
import com.android.purebilibili.plugin.sdk.CdnLineDiagnostic
import com.android.purebilibili.feature.anime4k.Anime4KBypassReason
import com.android.purebilibili.plugin.sdk.Anime4KPreset
import com.android.purebilibili.plugin.sdk.DEFAULT_FSR_SHARPNESS
import com.android.purebilibili.plugin.sdk.VideoEnhancementAlgorithm
import com.android.purebilibili.feature.video.playback.audio.AudioQualityOption
import com.android.purebilibili.feature.video.viewmodel.normalizeCodecFamilyKey
import com.android.purebilibili.feature.video.ui.overlay.resolvePiliCodecPreferenceOptions
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.feature.settings.SettingsIconRole
import com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon

private fun piliCodecLabel(codec: String?): String = when (normalizeCodecFamilyKey(codec)) {
    "avc1" -> "AVC (H.264)"
    "hev1" -> "HEVC (H.265)"
    "av01" -> "AV1"
    "dvh1", "dvhe" -> "Dolby Vision"
    else -> codec ?: "未知"
}

private enum class VideoSettingsPanelTextRole {
    TITLE,
    BODY,
    OPTION,
    DIAGNOSTIC,
}

@Composable
private fun VideoSettingsPanelText(
    text: String,
    role: VideoSettingsPanelTextRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    legacyFontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val style = when (role) {
        VideoSettingsPanelTextRole.TITLE -> MaterialTheme.typography.titleSmall
        VideoSettingsPanelTextRole.BODY -> MaterialTheme.typography.bodySmall
        VideoSettingsPanelTextRole.OPTION -> MaterialTheme.typography.labelMedium
        VideoSettingsPanelTextRole.DIAGNOSTIC -> MaterialTheme.typography.labelSmall
    }
    AppText(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = legacyFontWeight,
        maxLines = maxLines,
        overflow = overflow,
    )
}

internal data class VideoSettingsPanelVisualSpec(
    val rowHorizontalPadding: androidx.compose.ui.unit.Dp,
    val rowVerticalPadding: androidx.compose.ui.unit.Dp,
    val rowMinHeight: androidx.compose.ui.unit.Dp,
    val iconSize: androidx.compose.ui.unit.Dp,
    val iconGap: androidx.compose.ui.unit.Dp,
    val dividerHorizontalPadding: androidx.compose.ui.unit.Dp,
    val dividerAlpha: Float,
    val chipHeight: androidx.compose.ui.unit.Dp,
    val chipCornerRadius: androidx.compose.ui.unit.Dp,
    val chipHorizontalPadding: androidx.compose.ui.unit.Dp,
    val chipSpacing: androidx.compose.ui.unit.Dp
)

internal fun resolveVideoSettingsPanelVisualSpec(
    usesTonalContainerTreatment: Boolean,
    useMiuixNonGlassPresentation: Boolean,
): VideoSettingsPanelVisualSpec {
    return if (useMiuixNonGlassPresentation) {
        VideoSettingsPanelVisualSpec(
            rowHorizontalPadding = AppSpacingTokens.Large,
            rowVerticalPadding = AppSpacingTokens.Medium,
            rowMinHeight = 56.dp,
            iconSize = 20.dp,
            iconGap = AppSpacingTokens.Medium,
            dividerHorizontalPadding = AppSpacingTokens.Large,
            dividerAlpha = 0.18f,
            chipHeight = 36.dp,
            chipCornerRadius = 10.dp,
            chipHorizontalPadding = AppSpacingTokens.Medium,
            chipSpacing = 9.dp,
        )
    } else if (usesTonalContainerTreatment) {
        VideoSettingsPanelVisualSpec(
            rowHorizontalPadding = 16.dp,
            rowVerticalPadding = 12.dp,
            rowMinHeight = 56.dp,
            iconSize = 20.dp,
            iconGap = 12.dp,
            dividerHorizontalPadding = 16.dp,
            dividerAlpha = 0.18f,
            chipHeight = 34.dp,
            chipCornerRadius = 17.dp,
            chipHorizontalPadding = 13.dp,
            chipSpacing = 7.dp
        )
    } else {
        VideoSettingsPanelVisualSpec(
            rowHorizontalPadding = 16.dp,
            rowVerticalPadding = 14.dp,
            rowMinHeight = 52.dp,
            iconSize = 24.dp,
            iconGap = 16.dp,
            dividerHorizontalPadding = 16.dp,
            dividerAlpha = 0.5f,
            chipHeight = 32.dp,
            chipCornerRadius = 16.dp,
            chipHorizontalPadding = 12.dp,
            chipSpacing = 8.dp
        )
    }
}

@Composable
private fun rememberVideoSettingsPanelVisualSpec(): VideoSettingsPanelVisualSpec {
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
    val usesTonalContainerTreatment = rememberAppPlayerChromeProfile()
        .effects
        .usesTonalContainerTreatment
    return remember(usesTonalContainerTreatment, useMiuixNonGlassPresentation) {
        resolveVideoSettingsPanelVisualSpec(
            usesTonalContainerTreatment = usesTonalContainerTreatment,
            useMiuixNonGlassPresentation = useMiuixNonGlassPresentation,
        )
    }
}

@Composable
private fun videoSettingsChipContainerColor(isSelected: Boolean): Color {
    if (isMiuixNonGlassEnabled()) return playerOptionContainerColor(isSelected, AppSurfaceTokens.surfaceContainerHigh())
    val usesTonalContainerTreatment = rememberAppPlayerChromeProfile()
        .effects
        .usesTonalContainerTreatment
    return if (usesTonalContainerTreatment) {
        if (isSelected) AppSurfaceTokens.secondaryContainer() else AppSurfaceTokens.surfaceContainerHigh()
    } else {
        if (isSelected) AppContentColors.accent() else MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun videoSettingsChipContentColor(isSelected: Boolean): Color {
    if (isMiuixNonGlassEnabled()) return playerOptionContentColor(isSelected, AppContentColors.secondaryText())
    val usesTonalContainerTreatment = rememberAppPlayerChromeProfile()
        .effects
        .usesTonalContainerTreatment
    return if (usesTonalContainerTreatment) {
        if (isSelected) AppSurfaceTokens.onSecondaryContainer() else AppSurfaceTokens.onSurfaceVariantSummary()
    } else {
        if (isSelected) AppContentColors.onAccent() else AppContentColors.secondaryText()
    }
}

/**
 *  视频设置面板 - 竖屏模式下的高级设置底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsPanel(
    // 定时关闭
    sleepTimerMinutes: Int?,
    onSleepTimerChange: (Int?) -> Unit,
    isFullscreen: Boolean = false,
    playerBounds: androidx.compose.ui.unit.IntRect? = null,

    // 视频控制
    onReload: () -> Unit,
    onDanmakuSettingsClick: () -> Unit = {},
    onShowDanmakuPool: (() -> Unit)? = null,

    // 画质 - 内联选择
    currentQualityLabel: String,
    qualityLabels: List<String> = emptyList(),
    qualityIds: List<Int> = emptyList(),
    switchableQualityIds: List<Int> = emptyList(),
    isLoggedIn: Boolean = false,
    isVip: Boolean = false,
    onQualitySelected: (Int) -> Unit = {},

    // 倍速
    currentSpeed: Float = 1.0f,
    onSpeedChange: (Float) -> Unit = {},

    // 镜像翻转
    isFlippedHorizontal: Boolean = false,
    isFlippedVertical: Boolean = false,
    onFlipHorizontal: () -> Unit = {},
    onFlipVertical: () -> Unit = {},

    // 音频模式
    isAudioOnly: Boolean = false,
    onAudioOnlyToggle: () -> Unit = {},

    //  CDN 线路切换
    currentCdnIndex: Int = 0,
    cdnCount: Int = 1,
    cdnLineDiagnostics: List<CdnLineDiagnostic> = emptyList(),
    isCdnProbing: Boolean = false,
    onSwitchCdn: () -> Unit = {},
    onSwitchCdnTo: (Int) -> Unit = {},
    onProbeCdnCandidates: () -> Unit = {},

    // [New] Codec & Audio Quality
    // Passed from VideoPlaybackViewModel/SettingsManager
    currentCodec: String = "hev1",
    playingVideoCodec: String? = null,
    onCodecChange: (String) -> Unit = {},
    currentSecondCodec: String = "avc1",
    onSecondCodecChange: (String) -> Unit = {},
    currentAudioQuality: Int = -1,
    selectedAudioQuality: Int = -1,
    availableAudioQualities: List<AudioQualityOption> = emptyList(),
    onAudioQualityChange: (Int) -> Unit = {},
    anime4kEnabled: Boolean = false,
    anime4kAvailable: Boolean = false,
    anime4kBypassReason: Anime4KBypassReason = Anime4KBypassReason.DISABLED,
    videoEnhancementAlgorithm: VideoEnhancementAlgorithm = VideoEnhancementAlgorithm.ANIME4K,
    anime4kPreset: Anime4KPreset = Anime4KPreset.FAST,
    fsrSharpness: Float = DEFAULT_FSR_SHARPNESS,
    onAnime4kToggle: (Boolean) -> Unit = {},
    onVideoEnhancementAlgorithmChange: (VideoEnhancementAlgorithm) -> Unit = {},
    onAnime4kPresetChange: (Anime4KPreset) -> Unit = {},
    onFsrSharpnessChange: (Float) -> Unit = {},
    // [New] 音频语言 (AI Translation)
    aiAudioInfo: AiAudioInfo? = null,
    currentAudioLang: String? = null,
    onAudioLangChange: (String) -> Unit = {},

    // 资源下载
    onSaveCover: () -> Unit = {},
    onCaptureScreenshot: () -> Unit = {},
    onDownloadAudio: () -> Unit = {},

    onWatchLater: (() -> Unit)? = null,
    onShowNotes: (() -> Unit)? = null,
    onDownloadVideo: (() -> Unit)? = null,
    onEditPlayUrl: (() -> Unit)? = null,
    onSubtitleSettings: (() -> Unit)? = null,
    onLoadSubtitle: (() -> Unit)? = null,
    onSaveSubtitle: (() -> Unit)? = null,
    onShowPlaybackInfo: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    playbackOrderLabel: String = "顺序播放",
    playbackOrderOptions: List<String> = emptyList(),
    onPlaybackOrderChange: ((Int) -> Unit)? = null,
    playerVolume: Float = 1f,
    onPlayerVolumeChange: ((Float) -> Unit)? = null,
    playerVolumeUnavailableReason: String? = null,
    isBackgroundPlaybackEnabled: Boolean = false,
    onBackgroundPlaybackChange: ((Boolean) -> Unit)? = null,
    availableCodecIds: List<String> = emptyList(),
    sleepWaitForVideoEnd: Boolean = false,
    onSleepWaitForVideoEndChange: ((Boolean) -> Unit)? = null,
    sleepExitApp: Boolean = false,
    onSleepExitAppChange: ((Boolean) -> Unit)? = null,
    cdnServiceLabels: List<String> = emptyList(),
    currentCdnServiceIndex: Int = 0,
    onCdnServiceSelected: ((Int) -> Unit)? = null,
    chapterDisplayMode: ChapterDisplayMode = ChapterDisplayMode.WITH_CONTROLS,
    onChapterDisplayModeChange: ((ChapterDisplayMode) -> Unit)? = null,
    // 两种方向与材质共用已有的诊断显示设置；未接入的调用方不显示此入口。
    playerDiagnosticsEnabled: Boolean = false,
    onPlayerDiagnosticsChange: ((Boolean) -> Unit)? = null,
    // 关闭面板
    onDismiss: () -> Unit
) {
    PlayerNativeMenuTheme {
    var page by remember { mutableStateOf("main") }
    var showPlayerVolumeDialog by remember { mutableStateOf(false) }
    val controlCenterMotion: VideoControlCenterMotion? = null
    val useMiuixNonGlassPresentation = isMiuixNonGlassEnabled()
    val panelSpec = rememberVideoSettingsPanelVisualSpec()
    val customSectionHorizontalPadding = if (useMiuixNonGlassPresentation) {
        panelSpec.rowHorizontalPadding
    } else {
        16.dp
    }
    val customSectionVerticalPadding = if (useMiuixNonGlassPresentation) {
        panelSpec.rowVerticalPadding
    } else {
        12.dp
    }
    val customSectionIconSize = if (useMiuixNonGlassPresentation) panelSpec.iconSize else 24.dp
    val customSectionIconGap = if (useMiuixNonGlassPresentation) panelSpec.iconGap else 16.dp
    val customInlineTextGap = if (useMiuixNonGlassPresentation) AppSpacingTokens.Small else 8.dp
    val customTitleToOptionsGap = if (useMiuixNonGlassPresentation) AppSpacingTokens.Medium else 12.dp
    val nearbyHandoff = com.android.purebilibili.feature.nearby.LocalNearbyHandoff.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val codecChoices = remember(availableCodecIds) {
        resolvePiliCodecPreferenceOptions(availableCodecIds)
    }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val actionPolicy = remember(configuration.screenWidthDp) {
        resolveVideoSettingsPanelActionPolicy(widthDp = configuration.screenWidthDp)
    }
    val scope = rememberCoroutineScope()
    val visibleActionCounts by remember(context) { FullscreenActionCountSettings.getVisibleCounts(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    val seekForwardSeconds by com.android.purebilibili.core.store.SettingsManager
        .getSeekForwardSeconds(context)
        .collectAsStateWithLifecycle(initialValue = 10
        )
    val seekBackwardSeconds by com.android.purebilibili.core.store.SettingsManager
        .getSeekBackwardSeconds(context)
        .collectAsStateWithLifecycle(initialValue = 10
        )
    val longPressSpeed by com.android.purebilibili.core.store.SettingsManager
        .getLongPressSpeed(context)
        .collectAsStateWithLifecycle(initialValue = 2.0f
        )
    val playbackSpeedOptions by com.android.purebilibili.core.store.SettingsManager
        .getPlaybackSpeedOptions(context)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val longPressSpeedLockEnabled by com.android.purebilibili.core.store.SettingsManager
        .getLongPressSpeedLockEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false
        )
    val twoFingerVerticalSpeedEnabled by com.android.purebilibili.core.store.SettingsManager
        .getTwoFingerVerticalSpeedEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false
        )
    val twoFingerHorizontalSpeedEnabled by com.android.purebilibili.core.store.SettingsManager
        .getTwoFingerHorizontalSpeedEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false
        )
    val defaultPlaybackSpeed by com.android.purebilibili.core.store.SettingsManager
        .getDefaultPlaybackSpeed(context)
        .collectAsStateWithLifecycle(initialValue = 1.0f
        )
    val rememberLastPlaybackSpeed by com.android.purebilibili.core.store.SettingsManager
        .getRememberLastPlaybackSpeed(context)
        .collectAsStateWithLifecycle(initialValue = false
        )
    val progressPeakDanmakuEnabled by com.android.purebilibili.core.store.SettingsManager
        .getProgressPeakDanmakuEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false
        )
    val danmakuHotBarEnabled by com.android.purebilibili.core.store.SettingsManager
        .getDanmakuHotBarEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val downloadIcon = rememberAppDownloadIcon()
    val musicIcon = rememberAppMusicIcon()
    val flipHorizontalIcon = rememberAppFlipHorizontalIcon()
    val flipVerticalIcon = rememberAppFlipVerticalIcon()
    val qualityIcon = rememberAppQualityIcon()
    val codecIcon = rememberAppCodecIcon()
    val speedIcon = rememberAppSpeedIcon()
    val gestureTapIcon = rememberAppGestureTapIcon()
    val settingsIcon = rememberAppSettingsIcon()

    if (showPlayerVolumeDialog && onPlayerVolumeChange != null) {
        var draftVolume by remember { mutableFloatStateOf(playerVolume.coerceIn(1f, 3f)) }
        AlertDialog(
            onDismissRequest = { showPlayerVolumeDialog = false },
            title = { Text("播放器音量") },
            text = {
                Column {
                    Text("${(draftVolume * 100).toInt()}%")
                    Slider(draftVolume, { draftVolume = it }, valueRange = 1f..3f, steps = 39)
                }
            },
            dismissButton = { TextButton({ showPlayerVolumeDialog = false }) { Text("取消") } },
            confirmButton = { TextButton({ onPlayerVolumeChange(draftVolume); showPlayerVolumeDialog = false }) { Text("确认") } },
        )
    }
    PlayerNativeSettingsHost(
        onDismiss = onDismiss,
        isFullscreen = isFullscreen,
        playerBounds = playerBounds,
    ) {
        val dispatchAfterDismiss = com.android.purebilibili.core.ui.components.LocalAppGlassPanelDismissAction.current
        fun dismissWithAction(action: () -> Unit) {
            val finish = { onDismiss(); action() }
            // 下一层弹窗必须等共用玻璃宿主释放；原生面板保留原来的执行顺序。
            if (dispatchAfterDismiss != null) dispatchAfterDismiss(finish) else finish()
        }
        LazyColumn(
            // 同窗玻璃菜单由覆盖层避让系统栏，列表只保留紧凑留白；原生面板沿用原参数。
            modifier = Modifier.fillMaxWidth().then(
                if (LocalPlayerGlassSettingsSurface.current) Modifier else Modifier.navigationBarsPadding()
            ),
            contentPadding = PaddingValues(vertical = if (LocalPlayerGlassSettingsSurface.current) 6.dp else 14.dp),
        ) {
            if (page != "advanced") {
                item {
                    if (page != "main") PlayerSettingsRow("返回", onClick = { page = if (page == "cdnAdvanced") "cdn" else "main" })
                    val submenuTitle = when (page) { "quality" -> "选择画质"; "audio" -> "选择音质"; "codec" -> "首选编码格式"; "cdn", "cdnAdvanced" -> "CDN 设置"; "transform" -> "画面变换"; "enhancement" -> "画质增强"; "actionCounts" -> FullscreenActionCountSettings.TITLE; else -> null }
                    submenuTitle?.let { title ->
                        Box(Modifier.fillMaxWidth().height(45.dp), contentAlignment = Alignment.Center) { Text(title, fontSize = 14.sp) }
                    }
                    when (page) {
                        "main" -> {
                            nearbyHandoff?.takeIf { com.android.purebilibili.feature.nearby.NEARBY_HANDOFF_ENABLED }?.let { controller ->
                                PlayerSettingsRow(com.android.purebilibili.feature.nearby.NEARBY_TITLE) {
                                    dismissWithAction { controller.openDevices() }
                                }
                            }
                            onWatchLater?.let { action -> PlayerSettingsRow("稍后再看（添加 / 移出）") { dismissWithAction(action) } }
                            onShowNotes?.let { action -> PlayerSettingsRow("查看笔记") { dismissWithAction(action) } }
                            onDownloadVideo?.let { action -> PlayerSettingsRow("离线缓存") { dismissWithAction(action) } }
                            PlayerSettingsRow("保存封面") { dismissWithAction(onSaveCover) }
                            PlayerSettingsRow("定时关闭", sleepTimerMinutes?.let { "剩余 $it 分钟" }) { page = "sleep" }
                            onEditPlayUrl?.let { action -> PlayerSettingsRow("播放地址") { dismissWithAction(action) } }
                            onChapterDisplayModeChange?.let { onModeChange ->
                                PlayerSettingsPopupRow(
                                    title = playbackSettingTitle("playback.chapter_display_mode"),
                                    current = chapterDisplayMode.label,
                                    options = ChapterDisplayMode.entries.map { it.label },
                                ) { index -> onModeChange(ChapterDisplayMode.entries[index]) }
                            }
                            PlayerSettingsRow("重载视频") { dismissWithAction(onReload) }
                            PlayerSettingsRow(
                                "画质增强",
                                resolveAnime4KSettingsSubtitle(anime4kEnabled, anime4kAvailable, anime4kBypassReason),
                            ) { page = "enhancement" }
                            if (onPlayerVolumeChange != null) {
                                PlayerSettingsRow("播放器音量", "当前: ${(playerVolume * 100).toInt()}%") { showPlayerVolumeDialog = true }
                            } else if (playerVolumeUnavailableReason != null) {
                                PlayerSettingsRow("播放器音量", playerVolumeUnavailableReason, onClick = null)
                            }
                            if (cdnCount > 0 || cdnServiceLabels.isNotEmpty()) PlayerSettingsRow("CDN 设置", "当前：${cdnServiceLabels.getOrNull(currentCdnServiceIndex) ?: "线路 ${currentCdnIndex + 1}"}，无法播放请切换") { page = "cdn" }
                            PlayerSettingsRow(
                                "画面变换",
                                buildList {
                                    if (isFlippedHorizontal) add("左右翻转")
                                    if (isFlippedVertical) add("上下翻转")
                                }.joinToString("、").ifEmpty { "默认" },
                            ) { page = "transform" }
                            if (qualityLabels.isNotEmpty()) PlayerSettingsRow("选择画质", "当前画质 $currentQualityLabel") { page = "quality" }
                            if (availableAudioQualities.isNotEmpty()) PlayerSettingsRow("选择音质", availableAudioQualities.firstOrNull { it.preferenceId == selectedAudioQuality }?.label?.let { "当前音质 $it" } ?: "当前音质未知") { page = "audio" }
                            PlayerSettingsRow("首选编码格式", "首选 ${piliCodecLabel(currentCodec)}，当前播放 ${piliCodecLabel(playingVideoCodec)}") { page = "codec" }
                            onPlaybackOrderChange?.let { change -> PlayerSettingsPopupRow("播完后行为", playbackOrderLabel, playbackOrderOptions, onSelected = change) }
                            onShowDanmakuPool?.let { action -> PlayerSettingsRow("弹幕列表") { dismissWithAction(action) } }
                            PlayerSettingsRow("弹幕设置") { dismissWithAction(onDanmakuSettingsClick) }
                            onSubtitleSettings?.let { action -> PlayerSettingsRow("字幕设置") { dismissWithAction(action) } }
                            onLoadSubtitle?.let { action -> PlayerSettingsRow("加载字幕") { dismissWithAction(action) } }
                            onSaveSubtitle?.let { action -> PlayerSettingsRow("保存字幕") { dismissWithAction(action) } }
                            onShowPlaybackInfo?.let { action -> PlayerSettingsRow("播放信息") { dismissWithAction(action) } }
                            onPlayerDiagnosticsChange?.let { change ->
                                PlayerSettingsToggleRow(
                                    title = "播放器诊断",
                                    checked = playerDiagnosticsEnabled,
                                    onCheckedChange = change,
                                )
                            }
                            onReport?.let { action -> PlayerSettingsRow("举报") { dismissWithAction(action) } }
                            if (isFullscreen) {
                                PlayerSettingsRow(FullscreenActionCountSettings.TITLE) { page = "actionCounts" }
                            }
                            PlayerSettingsRow("高级设置") { page = "advanced" }
                        }
                        "actionCounts" -> {
                            FullscreenActionCount.entries.forEach { count ->
                                PlayerSettingsToggleRow(
                                    title = count.title,
                                    modifier = Modifier.fillMaxWidth(),
                                    checked = visibleActionCounts?.contains(count) == true,
                                    enabled = visibleActionCounts != null,
                                    onCheckedChange = { visible ->
                                        scope.launch { FullscreenActionCountSettings.setVisible(context, count, visible) }
                                    },
                                )
                            }
                        }
                        "transform" -> {
                            VideoSettingsSwitchRow(flipHorizontalIcon, "左右翻转", null, isFlippedHorizontal) { onFlipHorizontal() }
                            VideoSettingsSwitchRow(flipVerticalIcon, "上下翻转", null, isFlippedVertical) { onFlipVertical() }
                            if (isFlippedHorizontal || isFlippedVertical) {
                                PlayerSettingsRow("恢复默认") {
                                    if (isFlippedHorizontal) onFlipHorizontal()
                                    if (isFlippedVertical) onFlipVertical()
                                }
                            }
                        }
                        "enhancement" -> {
                            VideoSettingsSwitchRow(
                                icon = qualityIcon,
                                title = "画质增强",
                                subtitle = resolveAnime4KSettingsSubtitle(anime4kEnabled, anime4kAvailable, anime4kBypassReason),
                                checked = anime4kEnabled && anime4kAvailable,
                                enabled = anime4kAvailable,
                                onCheckedChange = { enabled ->
                                    if (anime4kAvailable) onAnime4kToggle(enabled)
                                },
                            )
                            if (anime4kEnabled && anime4kAvailable) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = AppSpacingTokens.Large,
                                        vertical = AppSpacingTokens.Small,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                                ) {
                                    VideoEnhancementAlgorithmOptions(videoEnhancementAlgorithm, onVideoEnhancementAlgorithmChange)
                                    if (videoEnhancementAlgorithm == VideoEnhancementAlgorithm.ANIME4K) {
                                        Anime4KPresetOptions(anime4kPreset, onAnime4kPresetChange)
                                    } else {
                                        FsrSharpnessOptions(fsrSharpness, onFsrSharpnessChange)
                                    }
                                }
                            }
                        }
                        "sleep" -> PlayerSleepTimerSettings(
                            sleepTimerMinutes, onSleepTimerChange,
                            sleepWaitForVideoEnd, onSleepWaitForVideoEndChange,
                            sleepExitApp, onSleepExitAppChange, onDismiss,
                        )
                        "quality" -> qualityLabels.forEachIndexed { index, label ->
                            val id = qualityIds.getOrNull(index)
                            PlayerSettingsSelectionRow(
                                title = label,
                                selected = label == currentQualityLabel,
                                enabled = id != null && id in switchableQualityIds,
                            ) { if (label != currentQualityLabel) { onQualitySelected(index); onDismiss() } }
                        }
                        "audio" -> availableAudioQualities.forEach { option ->
                            PlayerSettingsSelectionRow(
                                option.label, option.preferenceId == currentAudioQuality,
                                subtitle = option.codecDescription.takeIf { it.isNotBlank() },
                            ) {
                                onAudioQualityChange(option.preferenceId)
                                onDismiss()
                            }
                        }
                        "codec" -> codecChoices.forEach { family ->
                            val selected = family == normalizeCodecFamilyKey(currentCodec)
                            PlayerSettingsSelectionRow(piliCodecLabel(family), selected) {
                                onCodecChange(family)
                                onDismiss()
                            }
                        }
                        "cdn", "cdnAdvanced" -> {
                            if (page == "cdn") onCdnServiceSelected?.let { select ->
                                cdnServiceLabels.forEachIndexed { index, label ->
                                    PlayerSettingsSelectionRow(label, index == currentCdnServiceIndex) { select(index); onDismiss() }
                                }
                            }
                            if (cdnServiceLabels.isNotEmpty() && page == "cdn") PlayerSettingsRow("线路诊断") { page = "cdnAdvanced" }
                            if (cdnServiceLabels.isEmpty() || page == "cdnAdvanced") {
                            repeat(cdnCount) { index ->
                                val diagnostic = cdnLineDiagnostics.firstOrNull { it.index == index }
                                PlayerSettingsRow(diagnostic?.displayName ?: "线路 ${index + 1}", if (index == currentCdnIndex) "当前" else null) {
                                    com.android.purebilibili.feature.plugin.CdnTransferRuntime.preferHost(diagnostic?.host)
                                    onSwitchCdnTo(index)
                                }
                            }
                            PlaybackCdnDiagnostics(cdnLineDiagnostics, isCdnProbing, cdnCount > 0, onProbeCdnCandidates, onSwitchCdnTo, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            } else {
                item { PlayerSettingsRow("返回") { page = "main" } }
                item {
                    onCast?.let { action -> PlayerSettingsRow("投屏") { dismissWithAction(action) } }
                    onEnterPip?.let { action -> PlayerSettingsRow("画中画") { dismissWithAction(action) } }
                    onShare?.let { action -> PlayerSettingsRow("分享") { dismissWithAction(action) } }
                }
            val groups = VideoControlCenterGroups(this, controlCenterMotion)


            // [New] 资源下载
            groups.item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = customSectionHorizontalPadding,
                            vertical = customSectionVerticalPadding,
                        )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            imageVector = downloadIcon,
                            contentDescription = null,
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(customSectionIconSize)
                        )
                        Spacer(modifier = Modifier.width(customSectionIconGap))
                        VideoSettingsPanelText(
                            text = "资源下载",
                            role = VideoSettingsPanelTextRole.TITLE,

                            legacyFontWeight = FontWeight.Medium,
                            color = AppContentColors.primaryText()
                        )
                    }
                    Spacer(modifier = Modifier.height(customTitleToOptionsGap))

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(actionPolicy.rowItemSpacingDp.dp)
                    ) {
                        SettingsActionPill(
                            icon = Icons.Filled.PhotoCamera,
                            label = "视频截图",
                            onClick = { dismissWithAction(onCaptureScreenshot) },
                            policy = actionPolicy
                        )
                        SettingsActionPill(
                            icon = musicIcon,
                            label = "下载音频",
                            onClick = { dismissWithAction(onDownloadAudio) },
                            policy = actionPolicy
                        )
                    }
                }
                SettingsDivider()
            }

            // [New] 次选编码格式选择
            groups.item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = customSectionHorizontalPadding,
                            vertical = customSectionVerticalPadding,
                        )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            imageVector = codecIcon,
                            contentDescription = null,
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(customSectionIconSize)
                        )
                        Spacer(modifier = Modifier.width(customSectionIconGap))
                        VideoSettingsPanelText(
                            text = "次选编码",
                            role = VideoSettingsPanelTextRole.TITLE,

                            legacyFontWeight = FontWeight.Medium,
                            color = AppContentColors.primaryText()
                        )
                        Spacer(modifier = Modifier.width(customInlineTextGap))

                        val secondCodecLabel = when(currentSecondCodec) {
                            "avc1" -> "AVC (兼容)"
                            "hev1" -> "HEVC (高效)"
                            "av01" -> "AV1 (高压缩)"
                            else -> "未知"
                        }
                        VideoSettingsPanelText(
                            text = secondCodecLabel,
                            role = VideoSettingsPanelTextRole.BODY,

                            color = AppContentColors.secondaryText()
                        )
                    }
                    Spacer(modifier = Modifier.height(customTitleToOptionsGap))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (useMiuixNonGlassPresentation) panelSpec.chipSpacing else 8.dp
                        )
                    ) {
                        val codecs = listOf("avc1" to "AVC (H.264)", "hev1" to "HEVC (H.265)", "av01" to "AV1")
                        codecs.forEach { (codec, label) ->
                            val isSelected = currentSecondCodec == codec
                            AppSurface(
                                onClick = { onSecondCodecChange(codec) },
                                shape = playerOptionShape(AppShapes.container(ContainerLevel.Card)),
                                color = playerOptionContainerColor(isSelected,
                                    if (isSelected) AppContentColors.accent() else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.height(
                                    if (useMiuixNonGlassPresentation) panelSpec.chipHeight else 32.dp
                                )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(
                                        horizontal = if (useMiuixNonGlassPresentation) {
                                            panelSpec.chipHorizontalPadding
                                        } else {
                                            12.dp
                                        },
                                    ),
                                ) {
                                    VideoSettingsPanelText(
                                        text = label,
                                        role = VideoSettingsPanelTextRole.OPTION,

                                        color = playerOptionContentColor(isSelected,
                                            if (isSelected) AppContentColors.onAccent() else AppContentColors.secondaryText())
                                    )
                                }
                            }
                        }
                    }
                }
                SettingsDivider()
            }

             // [New] 音频语言选择 (AI Translation)
            if (aiAudioInfo?.items?.isNotEmpty() == true) {
                groups.item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = customSectionHorizontalPadding,
                                vertical = customSectionVerticalPadding,
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VideoSettingsPanelText(
                                text = "AI原生翻译",
                                role = VideoSettingsPanelTextRole.TITLE,

                                legacyFontWeight = FontWeight.Medium,
                                color = AppContentColors.primaryText()
                            )
                            Spacer(modifier = Modifier.width(customInlineTextGap))

                            val currentLangItem = aiAudioInfo.items.find { it.langCode == currentAudioLang }
                            val langLabel = currentLangItem?.langDoc ?: "原声"

                            VideoSettingsPanelText(
                                text = langLabel,
                                role = VideoSettingsPanelTextRole.BODY,

                                color = AppContentColors.secondaryText()
                            )
                        }
                        Spacer(modifier = Modifier.height(customTitleToOptionsGap))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(
                                if (useMiuixNonGlassPresentation) panelSpec.chipSpacing else 8.dp
                            )
                        ) {
                            aiAudioInfo.items.forEach { item ->
                                val isSelected = currentAudioLang == item.langCode
                                AppSurface(
                                    onClick = { onAudioLangChange(item.langCode) },
                                    shape = playerOptionShape(AppShapes.container(ContainerLevel.Card)),
                                    color = playerOptionContainerColor(isSelected,
                                    if (isSelected) AppContentColors.accent() else MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.height(
                                        if (useMiuixNonGlassPresentation) panelSpec.chipHeight else 32.dp
                                    )
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(
                                            horizontal = if (useMiuixNonGlassPresentation) {
                                                panelSpec.chipHorizontalPadding
                                            } else {
                                                12.dp
                                            },
                                        ),
                                    ) {
                                        VideoSettingsPanelText(
                                            text = item.langDoc,
                                            role = VideoSettingsPanelTextRole.OPTION,

                                            color = playerOptionContentColor(isSelected,
                                            if (isSelected) AppContentColors.onAccent() else AppContentColors.secondaryText())
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SettingsDivider()
                }
            }

            //  播放倍速 - 内联选择
            groups.item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = customSectionHorizontalPadding,
                            vertical = customSectionVerticalPadding,
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            imageVector = speedIcon,
                            contentDescription = null,
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(customSectionIconSize)
                        )
                        Spacer(modifier = Modifier.width(customSectionIconGap))
                        VideoSettingsPanelText(
                            text = "播放倍速",
                            role = VideoSettingsPanelTextRole.TITLE,

                            legacyFontWeight = FontWeight.Medium,
                            color = AppContentColors.primaryText()
                        )
                        Spacer(modifier = Modifier.width(customInlineTextGap))
                        VideoSettingsPanelText(
                            text = PlaybackSpeed.formatSpeedFull(currentSpeed),
                            role = VideoSettingsPanelTextRole.BODY,

                            color = AppContentColors.secondaryText()
                        )
                    }
                    Spacer(modifier = Modifier.height(customTitleToOptionsGap))
                    SpeedOptions(
                        currentSpeed = currentSpeed,
                        options = playbackSpeedOptions,
                        onSelect = onSpeedChange
                    )
                    Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                    VideoSettingsPanelText(
                        text = "可在播放设置中调整倍速列表",
                        role = VideoSettingsPanelTextRole.BODY,

                        color = AppContentColors.secondaryText()
                    )
                }
                SettingsDivider()
            }

            groups.item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    VideoSettingsSwitchRow(
                        icon = settingsIcon,
                        title = "记忆上次播放速度",
                        subtitle = if (rememberLastPlaybackSpeed) {
                            "已开启记忆上次速度（当前优先）"
                        } else {
                            "当前默认 ${formatPlaybackSpeed(defaultPlaybackSpeed)}"
                        },
                        checked = rememberLastPlaybackSpeed,
                        onCheckedChange = { checked ->
                            scope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setRememberLastPlaybackSpeed(context, checked)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))

                    PlaybackSpeedPreferenceControl(
                        currentSpeed = defaultPlaybackSpeed,
                        options = remember(playbackSpeedOptions, defaultPlaybackSpeed) {
                            if (defaultPlaybackSpeed in playbackSpeedOptions) playbackSpeedOptions
                            else (playbackSpeedOptions + defaultPlaybackSpeed).sorted()
                        },
                        onSpeedChange = { speed ->
                            scope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setDefaultPlaybackSpeed(context, speed)
                            }
                        },
                        title = "默认播放速度",
                        subtitle = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
                SettingsDivider()
            }

            //  [新增] 双击跳转秒数设置 (带开关)
            groups.item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val doubleTapSeekEnabled by com.android.purebilibili.core.store.SettingsManager
                        .getDoubleTapSeekEnabled(context)
                        .collectAsStateWithLifecycle(initialValue = false)

                    VideoSettingsSwitchRow(
                        icon = speedIcon,
                        title = "双击跳转",
                        subtitle = if (doubleTapSeekEnabled) {
                            "快进 ${seekForwardSeconds}s / 后退 ${seekBackwardSeconds}s"
                        } else {
                            "已关闭"
                        },
                        checked = doubleTapSeekEnabled,
                        onCheckedChange = { checked ->
                            scope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setDoubleTapSeekEnabled(context, checked)
                            }
                        }
                    )

                    // 仅当开启时显示秒数选项
                    AnimatedVisibility(
                        visible = doubleTapSeekEnabled,
                        enter = folmeExpandEnterTransition(useMiuixNonGlassPresentation),
                        exit = folmeExpandExitTransition(useMiuixNonGlassPresentation)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = customSectionHorizontalPadding)) {
                            Spacer(modifier = Modifier.height(customTitleToOptionsGap))

                            // 快进秒数选择
                            VideoSettingsPanelText(
                                text = "快进秒数（双击右侧）",
                                role = VideoSettingsPanelTextRole.BODY,

                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(bottom = AppSpacingTokens.Small)
                            )
                            SeekSecondsOptions(
                                currentSeconds = seekForwardSeconds,
                                onSelect = { seconds ->
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager.setSeekForwardSeconds(context, seconds)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(customTitleToOptionsGap))

                            // 后退秒数选择
                            VideoSettingsPanelText(
                                text = "后退秒数（双击左侧）",
                                role = VideoSettingsPanelTextRole.BODY,

                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(bottom = AppSpacingTokens.Small)
                            )
                            SeekSecondsOptions(
                                currentSeconds = seekBackwardSeconds,
                                onSelect = { seconds ->
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager.setSeekBackwardSeconds(context, seconds)
                                    }
                                }
                            )
                        }
                    }
                }
                SettingsDivider()
            }

            //  [新增] 长按倍速设置
            groups.item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = customSectionHorizontalPadding,
                            vertical = if (useMiuixNonGlassPresentation) {
                                AppSpacingTokens.Medium
                            } else {
                                8.dp
                            },
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            imageVector = gestureTapIcon,
                            contentDescription = null,
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(customSectionIconSize)
                        )
                        Spacer(modifier = Modifier.width(customSectionIconGap))
                        Column(modifier = Modifier.weight(1f)) {
                            VideoSettingsPanelText(
                                text = "长按倍速",
                                role = VideoSettingsPanelTextRole.TITLE,

                                legacyFontWeight = FontWeight.Medium,
                                color = AppContentColors.primaryText()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(customTitleToOptionsGap))
                    LongPressSpeedPreferenceControl(
                        currentSpeed = longPressSpeed,
                        onSpeedChange = { speed ->
                            scope.launch {
                                com.android.purebilibili.core.store.SettingsManager.setLongPressSpeed(context, speed)
                            }
                        },
                        title = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SettingsDivider()
            }

            groups.item {
                VideoSettingsSwitchRow(
                    icon = gestureTapIcon,
                    title = "长按倍速锁定",
                    subtitle = "长按后拖至上下区域保持倍速",
                    checked = longPressSpeedLockEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setLongPressSpeedLockEnabled(context, checked)
                            if (checked) {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setLongPressSpeedLockHintShown(context, true)
                            }
                        }
                    }
                )
                SettingsDivider()
            }

            groups.item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    VideoSettingsSwitchRow(
                        icon = flipVerticalIcon,
                        title = "双指上下滑动调倍速",
                        subtitle = "仅全屏生效，开启一项时会关闭另一项",
                        checked = twoFingerVerticalSpeedEnabled,
                        onCheckedChange = { checked ->
                            scope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setTwoFingerVerticalSpeedEnabled(context, checked)
                            }
                        }
                    )

                    VideoSettingsSwitchRow(
                        icon = flipHorizontalIcon,
                        title = "双指左右滑动调倍速",
                        subtitle = "仅全屏生效，开启一项时会关闭另一项",
                        checked = twoFingerHorizontalSpeedEnabled,
                        onCheckedChange = { checked ->
                            scope.launch {
                                com.android.purebilibili.core.store.SettingsManager
                                    .setTwoFingerHorizontalSpeedEnabled(context, checked)
                            }
                        }
                    )
                }
                SettingsDivider()
            }

            groups.item {
                VideoSettingsSwitchRow(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PROGRESS_PEAK_DANMAKU),
                    title = "高能进度条趋势",
                    subtitle = if (progressPeakDanmakuEnabled) "在进度条上展示弹幕高能热度曲线" else "关闭高能弹幕热度曲线",
                    checked = progressPeakDanmakuEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setProgressPeakDanmakuEnabled(context, checked)
                        }
                    }
                )
                SettingsDivider()
            }

            groups.item {
                VideoSettingsSwitchRow(
                    icon = rememberSettingsSemanticIcon(SettingsIconRole.PROGRESS_PEAK_DANMAKU),
                    title = "显示计数弹幕",
                    subtitle = if (danmakuHotBarEnabled) "按内容长度自动显示 1～3 条完整横幅，均匀排布" else "已关闭，普通弹幕照常显示",
                    checked = danmakuHotBarEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            com.android.purebilibili.core.store.SettingsManager
                                .setDanmakuHotBarEnabled(context, checked)
                        }
                    }
                )
                SettingsDivider()
            }
            }
        }
    }
    }
}

/**
 * 设置项组件
 */
@Composable
private fun VideoSettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    AppSwitchPreference(
        icon = icon,
        title = title,
        subtitle = subtitle,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        iconTint = AppSurfaceTokens.onSurfaceVariantActions(),
    )
}


@Composable
private fun SettingsDivider() {
    val spec = rememberVideoSettingsPanelVisualSpec()
    AppHorizontalDivider(
        modifier = Modifier.padding(horizontal = spec.dividerHorizontalPadding),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.dividerAlpha)
    )
}

/**
 * 播放倍速选项
 */
@Composable
private fun SpeedOptions(
    currentSpeed: Float,
    options: List<Float>,
    onSelect: (Float) -> Unit
) {
    val spec = rememberVideoSettingsPanelVisualSpec()
    val displayOptions = remember(options, currentSpeed) {
        if (currentSpeed in options) options else (options + currentSpeed).sorted()
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spec.chipSpacing)
    ) {
        displayOptions.forEach { speed ->
            val isSelected = currentSpeed == speed
            AppSurface(
                onClick = { onSelect(speed) },
                shape = playerOptionShape(RoundedCornerShape(spec.chipCornerRadius)),
                color = videoSettingsChipContainerColor(isSelected),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = spec.chipHorizontalPadding)
                ) {
                    VideoSettingsPanelText(
                        text = PlaybackSpeed.formatSpeedFull(speed),
                        role = VideoSettingsPanelTextRole.OPTION,

                        color = videoSettingsChipContentColor(isSelected),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    policy: VideoSettingsPanelActionPolicy
) {
    val usesTonalContainerTreatment = rememberAppPlayerChromeProfile()
        .effects
        .usesTonalContainerTreatment
    AppSurface(
        onClick = onClick,
        shape = RoundedCornerShape(policy.pillHeightDp.dp),
        color = if (usesTonalContainerTreatment) {
            AppSurfaceTokens.secondaryContainer()
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        modifier = Modifier
            .height(policy.pillHeightDp.dp)
            .defaultMinSize(minWidth = policy.pillMinWidthDp.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = policy.pillHorizontalPaddingDp.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(policy.pillIconSizeDp.dp)
            )
            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
            VideoSettingsPanelText(
                text = label,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                role = VideoSettingsPanelTextRole.OPTION,

                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 双击跳转秒数选项
 */
@Composable
private fun SeekSecondsOptions(
    currentSeconds: Int,
    onSelect: (Int) -> Unit
) {
    val spec = rememberVideoSettingsPanelVisualSpec()
    val options = listOf(5, 10, 15, 20, 30)

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spec.chipSpacing)
    ) {
        options.forEach { seconds ->
            val isSelected = currentSeconds == seconds
            AppSurface(
                onClick = { onSelect(seconds) },
                shape = playerOptionShape(RoundedCornerShape(spec.chipCornerRadius)),
                color = videoSettingsChipContainerColor(isSelected),
                modifier = Modifier.height(spec.chipHeight)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = spec.chipHorizontalPadding)
                ) {
                    VideoSettingsPanelText(
                        text = "${seconds}s",
                        role = VideoSettingsPanelTextRole.OPTION,

                        color = videoSettingsChipContentColor(isSelected)
                    )
                }
            }
        }
    }
}
