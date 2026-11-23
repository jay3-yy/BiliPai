// 文件路径: feature/video/ui/components/VideoSettingsPanel.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.components.AppTextButton as TextButton
import com.android.purebilibili.core.ui.components.AppSlider as Slider
import com.android.purebilibili.core.ui.AppAlertDialog as AlertDialog

import com.android.purebilibili.core.store.FullscreenActionCount
import com.android.purebilibili.core.store.FullscreenActionCountSettings
import com.android.purebilibili.core.store.ChapterDisplayMode
import com.android.purebilibili.core.store.player.PlayerMenuConfiguration
import com.android.purebilibili.core.store.player.PlayerMenuStore
import com.android.purebilibili.feature.video.ui.components.FullscreenMoreMenuEntry.*
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
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
import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
internal fun VideoSettingsPanel(
    // 定时关闭
    sleepTimerMinutes: Int?,
    onSleepTimerChange: (Int?) -> Unit,
    abLoopState: com.android.purebilibili.feature.video.playback.policy.VideoAbLoopState =
        com.android.purebilibili.feature.video.playback.policy.VideoAbLoopState(),
    onSetAbLoopPointA: () -> Unit = {},
    onSetAbLoopPointB: () -> Unit = {},
    onClearAbLoop: () -> Unit = {},
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

    // 镜像翻转与缩放
    isFlippedHorizontal: Boolean = false,
    isFlippedVertical: Boolean = false,
    videoScale: Float = 1f,
    onFlipHorizontal: () -> Unit = {},
    onFlipVertical: () -> Unit = {},
    onVideoScaleChange: (Float) -> Unit = {},

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
    audioQualityValue: String? = null,
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
    subtitleValue: String? = null,
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
    sleepWaitForCurrentVideoEnd: Boolean = false,
    onSleepWaitForCurrentVideoEndChange: ((Boolean) -> Unit)? = null,
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
    uiStyle: AppUiStyle = LocalAppUiStyle.current,
    customizeFullscreenMoreMenu: Boolean = false,
    menuConfiguration: PlayerMenuConfiguration? = null,
    topShortcutOnBar: FullscreenMoreMenuEntry? = null,
    onOpenMovedQualityMenu: (() -> Unit)? = null,
    onOpenMovedSpeedMenu: (() -> Unit)? = null,
    // 关闭面板
    onDismiss: () -> Unit
) {
    PlayerNativeMenuTheme(uiStyle) {
    var page by remember(customizeFullscreenMoreMenu) { mutableStateOf("main") }
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
    val hideCenterPlayback = com.android.purebilibili.feature.video.ui.overlay.rememberHideCenterPlayback()
    val visibleActionCounts by remember(context) { FullscreenActionCountSettings.getVisibleCounts(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    val visibleTopBarItems by remember(context) {
        com.android.purebilibili.core.store.FullscreenTopBarSettings.getVisibleItems(context)
    }.collectAsStateWithLifecycle(initialValue = null)
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
        val mainRows = if (page == "main" || page == "editMenu" || page == "playbackSettings") {
            buildMap<FullscreenMoreMenuEntry, @Composable () -> Unit> {
                nearbyHandoff?.takeIf { com.android.purebilibili.feature.nearby.NEARBY_HANDOFF_ENABLED }?.let { controller ->
                    put(NEARBY_HANDOFF) { PlayerSettingsRow(NEARBY_HANDOFF.title) { dismissWithAction(controller.openDevices) } }
                }
                onWatchLater?.let { action ->
                    put(WATCH_LATER) { PlayerSettingsRow(WATCH_LATER.title) { dismissWithAction(action) } }
                }
                onShowNotes?.let { action ->
                    put(NOTES) { PlayerSettingsRow(NOTES.title) { dismissWithAction(action) } }
                }
                onDownloadVideo?.let { action ->
                    put(DOWNLOAD) { PlayerSettingsRow(DOWNLOAD.title) { dismissWithAction(action) } }
                }
                put(SAVE_COVER) { PlayerSettingsRow(SAVE_COVER.title) { dismissWithAction(onSaveCover) } }
                put(SLEEP_TIMER) {
                    PlayerSettingsRow(
                        SLEEP_TIMER.title,
                        if (sleepWaitForCurrentVideoEnd) "播完当前视频"
                        else formatPlayerMenuTimerValue(sleepTimerMinutes),
                    ) { page = "sleep" }
                }
                put(AB_LOOP) {
                    PlayerSettingsRow(
                        AB_LOOP.title,
                        com.android.purebilibili.feature.video.playback.policy.formatAbLoopSummary(abLoopState),
                    ) { page = "abLoop" }
                }
                onEditPlayUrl?.let { action ->
                    put(PLAY_URL) { PlayerSettingsRow(PLAY_URL.title) { dismissWithAction(action) } }
                }
                onChapterDisplayModeChange?.let { onModeChange ->
                    put(CHAPTERS) {
                        PlayerSettingsPopupRow(
                            title = CHAPTERS.title,
                            current = chapterDisplayMode.label,
                            options = ChapterDisplayMode.entries.map { it.label },
                        ) { index -> onModeChange(ChapterDisplayMode.entries[index]) }
                    }
                }
                put(CENTER_PLAYBACK) {
                    PlayerSettingsToggleRow(
                        title = CENTER_PLAYBACK.title,
                        checked = !hideCenterPlayback,
                        onCheckedChange = { visible ->
                            scope.launch {
                                com.android.purebilibili.core.store.player.PlayerSettingsStore
                                    .setHideCenterPlayback(context, !visible)
                            }
                        },
                    )
                }
                put(RELOAD) { PlayerSettingsRow(RELOAD.title) { dismissWithAction(onReload) } }
                put(ENHANCEMENT) {
                    PlayerSettingsRow(
                        ENHANCEMENT.title,
                        resolveAnime4KSettingsSubtitle(anime4kEnabled, anime4kAvailable, anime4kBypassReason),
                    ) { page = "enhancement" }
                }
                if (onPlayerVolumeChange != null) {
                    put(VOLUME) {
                        PlayerSettingsRow(VOLUME.title, "${(playerVolume * 100).toInt()}%") { showPlayerVolumeDialog = true }
                    }
                } else if (playerVolumeUnavailableReason != null) {
                    put(VOLUME) { PlayerSettingsRow(VOLUME.title, playerVolumeUnavailableReason, onClick = null) }
                }
                if (cdnCount > 0 || cdnServiceLabels.isNotEmpty()) {
                    put(CDN) {
                        val service = cdnServiceLabels.getOrNull(currentCdnServiceIndex) ?: "线路 ${currentCdnIndex + 1}"
                        PlayerSettingsRow(CDN.title, service) { page = "cdn" }
                    }
                }
                put(TRANSFORM) {
                    val transform = buildList {
                        if (isFlippedHorizontal) add("左右翻转")
                        if (isFlippedVertical) add("上下翻转")
                        if (kotlin.math.abs(videoScale - 1f) >= 0.01f) add("${(videoScale * 100).toInt()}%")
                    }.joinToString("、").ifEmpty { "默认" }
                    PlayerSettingsRow(TRANSFORM.title, transform) { page = "transform" }
                }
                if (qualityLabels.isNotEmpty()) {
                    put(QUALITY) {
                        PlayerSettingsRow(QUALITY.title, currentQualityLabel.ifBlank { "未知" }) {
                            if (onOpenMovedQualityMenu != null) {
                                dismissWithAction(onOpenMovedQualityMenu)
                            } else {
                                page = "quality"
                            }
                        }
                    }
                }
                if (availableAudioQualities.isNotEmpty()) {
                    put(AUDIO_QUALITY) {
                        PlayerSettingsRow(AUDIO_QUALITY.title, audioQualityValue ?: "未知") { page = "audio" }
                    }
                }
                put(CODEC) {
                    PlayerSettingsRow(CODEC.title, "首选 ${piliCodecLabel(currentCodec)}，当前播放 ${piliCodecLabel(playingVideoCodec)}") { page = "codec" }
                }
                onPlaybackOrderChange?.let { change ->
                    put(PLAYBACK_ORDER) {
                        PlayerSettingsPopupRow(PLAYBACK_ORDER.title, playbackOrderLabel, playbackOrderOptions, onSelected = change)
                    }
                }
                onShowDanmakuPool?.let { action ->
                    put(DANMAKU_LIST) { PlayerSettingsRow(DANMAKU_LIST.title) { dismissWithAction(action) } }
                }
                put(DANMAKU_SETTINGS) { PlayerSettingsRow(DANMAKU_SETTINGS.title) { dismissWithAction(onDanmakuSettingsClick) } }
                onSubtitleSettings?.let { action ->
                    put(SUBTITLE_SETTINGS) { PlayerSettingsRow(SUBTITLE_SETTINGS.title, subtitleValue) { dismissWithAction(action) } }
                }
                onLoadSubtitle?.let { action ->
                    put(LOAD_SUBTITLES) { PlayerSettingsRow(LOAD_SUBTITLES.title) { dismissWithAction(action) } }
                }
                onSaveSubtitle?.let { action ->
                    put(SAVE_SUBTITLES) { PlayerSettingsRow(SAVE_SUBTITLES.title) { dismissWithAction(action) } }
                }
                onShowPlaybackInfo?.let { action ->
                    put(PLAYBACK_INFO) { PlayerSettingsRow(PLAYBACK_INFO.title) { dismissWithAction(action) } }
                }
                onPlayerDiagnosticsChange?.let { change ->
                    put(DIAGNOSTICS) {
                        PlayerSettingsToggleRow(DIAGNOSTICS.title, playerDiagnosticsEnabled, change)
                    }
                }
                onReport?.let { action ->
                    put(REPORT) { PlayerSettingsRow(REPORT.title) { dismissWithAction(action) } }
                }
                if (isFullscreen) {
                    put(TOP_BAR_ITEMS) {
                        val summary = visibleTopBarItems?.let {
                            "${it.size}/${com.android.purebilibili.core.store.FullscreenTopBarItem.entries.size}"
                        }
                        PlayerSettingsRow(TOP_BAR_ITEMS.title, summary) { page = "topBarItems" }
                    }
                    put(ACTION_COUNTS) { PlayerSettingsRow(ACTION_COUNTS.title) { page = "actionCounts" } }
                }
                put(ADVANCED) { PlayerSettingsRow(ADVANCED.title) { page = "advanced" } }
                if (onOpenMovedSpeedMenu != null) {
                    put(SPEED) {
                        PlayerSettingsRow(SPEED.title, PlaybackSpeed.formatSpeedFull(currentSpeed)) {
                            dismissWithAction(onOpenMovedSpeedMenu)
                        }
                    }
                }
            }
        } else emptyMap()
        // 同窗玻璃菜单自行避让系统栏，原生面板仍保留导航栏内边距。
        val menuModifier = Modifier.fillMaxWidth().then(
            if (LocalPlayerGlassSettingsSurface.current) Modifier else Modifier.navigationBarsPadding()
        )
        val fullscreenMainListState = rememberLazyListState()
        val playbackSettingsListState = rememberLazyListState()
        val settingsListState = rememberLazyListState()
        if (page == "editMenu" && menuConfiguration != null) {
            PlayerMenuEditor(
                configuration = menuConfiguration,
                defaultEntries = fullscreenMoreMenuEntries,
                availableEntries = mainRows.keys + SPEED,
                onBack = { page = "main" },
                onSave = { PlayerMenuStore.FULLSCREEN.save(context, it) },
                onSaved = { page = "main" },
                modifier = menuModifier,
                topShortcutEntries = fullscreenTopShortcutEntries,
                bottomControlOptions = fullscreenBottomControlOptions,
                playbackSettingsEntries = fullscreenPlaybackSettingsEntries,
            )
            return@PlayerNativeSettingsHost
        }
        val visibleEntries = if (customizeFullscreenMoreMenu) {
            menuConfiguration.visibleEntries(fullscreenMoreMenuEntries, mainRows.keys)
                .filterNot { it == topShortcutOnBar }
        } else emptyList()
        val playbackSettingsEntries = visibleEntries.filter { it in fullscreenPlaybackSettingsEntries }
        LazyColumn(
            modifier = menuModifier,
            state = when {
                page == "main" && customizeFullscreenMoreMenu -> fullscreenMainListState
                page == "playbackSettings" -> playbackSettingsListState
                else -> settingsListState
            },
            contentPadding = PaddingValues(vertical = if (LocalPlayerGlassSettingsSurface.current) 6.dp else 14.dp),
        ) {
            if (page == "main") {
                if (customizeFullscreenMoreMenu) {
                    item(key = "edit_menu") {
                        PlayerSettingsRow(
                            PLAYER_MENU_EDITOR_TITLE,
                            onClick = if (menuConfiguration != null) ({ page = "editMenu" }) else null,
                        )
                    }
                    val firstPlaybackSetting = playbackSettingsEntries.firstOrNull()
                    visibleEntries.forEach { entry ->
                        when {
                            entry == firstPlaybackSetting -> item(key = "playback_settings") {
                                PlayerSettingsRow(PLAYER_PLAYBACK_SETTINGS_TITLE) { page = "playbackSettings" }
                            }
                            entry !in fullscreenPlaybackSettingsEntries -> item(key = entry.id) {
                                mainRows.getValue(entry)()
                            }
                        }
                    }
                } else {
                    item { mainRows.values.forEach { row -> row() } }
                }
            } else if (page == "playbackSettings") {
                item {
                    PlayerSettingsRow("返回") { page = "main" }
                    AppText(
                        PLAYER_PLAYBACK_SETTINGS_TITLE,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(playbackSettingsEntries, key = { it.id }) { entry -> mainRows.getValue(entry)() }
            } else if (page != "advanced") {
                item {
                    PlayerSettingsRow("返回") {
                        page = when (page) {
                            "cdnAdvanced" -> "cdn"
                            "abLoop", "enhancement", "cdn", "transform", "audio", "codec" ->
                                if (customizeFullscreenMoreMenu) "playbackSettings" else "main"
                            else -> "main"
                        }
                    }
                    val submenuTitle = when (page) { "quality" -> "选择画质"; "audio" -> "选择音质"; "codec" -> "首选编码格式"; "cdn", "cdnAdvanced" -> "CDN 设置"; "transform" -> "画面变换"; "abLoop" -> "A-B 区间循环"; "enhancement" -> "画质增强"; "actionCounts" -> FullscreenActionCountSettings.TITLE; "topBarItems" -> com.android.purebilibili.core.store.FullscreenTopBarSettings.TITLE; else -> null }
                    submenuTitle?.let { title ->
                        Box(Modifier.fillMaxWidth().height(45.dp), contentAlignment = Alignment.Center) { Text(title, fontSize = 14.sp) }
                    }
                    when (page) {
                        "topBarItems" -> {
                            com.android.purebilibili.core.store.FullscreenTopBarItemGroup.entries.forEach { group ->
                                AppText(
                                    text = group.title,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppContentColors.secondaryText(),
                                )
                                com.android.purebilibili.core.store.FullscreenTopBarItem.entries
                                    .filter { it.group == group }
                                    .forEach { item ->
                                        PlayerSettingsToggleRow(
                                            title = item.title,
                                            modifier = Modifier.fillMaxWidth(),
                                            checked = visibleTopBarItems?.contains(item) == true,
                                            enabled = visibleTopBarItems != null,
                                            onCheckedChange = { visible ->
                                                scope.launch {
                                                    com.android.purebilibili.core.store.FullscreenTopBarSettings.setVisible(context, item, visible)
                                                }
                                            },
                                        )
                                    }
                            }
                            if (visibleTopBarItems != null &&
                                visibleTopBarItems != com.android.purebilibili.core.store.FullscreenTopBarSettings.defaultVisibleItems
                            ) {
                                PlayerSettingsRow("恢复默认") {
                                    scope.launch {
                                        com.android.purebilibili.core.store.FullscreenTopBarSettings.resetDefaults(context)
                                    }
                                }
                            }
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
                        "abLoop" -> {
                            PlayerSettingsRow(
                                "当前区间",
                                com.android.purebilibili.feature.video.playback.policy.formatAbLoopSummary(abLoopState),
                                onClick = null,
                            )
                            PlayerSettingsRow("设当前时间为 A 点") { onSetAbLoopPointA() }
                            PlayerSettingsRow("设当前时间为 B 点") { onSetAbLoopPointB() }
                            if (abLoopState.hasAnyPoint) {
                                PlayerSettingsRow("清除 A-B 区间") { onClearAbLoop() }
                            }
                        }
                        "transform" -> {
                            VideoSettingsSwitchRow(flipHorizontalIcon, "左右翻转", null, isFlippedHorizontal) { onFlipHorizontal() }
                            VideoSettingsSwitchRow(flipVerticalIcon, "上下翻转", null, isFlippedVertical) { onFlipVertical() }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Small),
                                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    VideoSettingsPanelText("画面缩放", VideoSettingsPanelTextRole.TITLE)
                                    VideoSettingsPanelText(
                                        "${(videoScale * 100).toInt()}%",
                                        VideoSettingsPanelTextRole.BODY,
                                        color = if (kotlin.math.abs(videoScale - 1f) >= 0.01f) AppContentColors.accent() else AppContentColors.secondaryText(),
                                    )
                                }
                                Slider(
                                    value = videoScale.coerceIn(0.5f, 2.0f),
                                    onValueChange = { onVideoScaleChange((it * 20f).toInt() / 20f) },
                                    valueRange = 0.5f..2.0f,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(0.75f to "75%", 1.0f to "100%", 1.15f to "115%", 1.33f to "21:9", 1.5f to "150%").forEach { (preset, label) ->
                                        val selected = kotlin.math.abs(videoScale - preset) < 0.02f
                                        AppSurface(
                                            onClick = { onVideoScaleChange(preset) },
                                            shape = AppShapes.container(ContainerLevel.Chip),
                                            color = if (selected) AppContentColors.accent().copy(alpha = 0.18f)
                                                else AppSurfaceTokens.surfaceContainerHigh().copy(alpha = 0.65f),
                                        ) {
                                            VideoSettingsPanelText(
                                                text = label,
                                                role = VideoSettingsPanelTextRole.OPTION,
                                                color = if (selected) AppContentColors.accent() else AppContentColors.primaryText(),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            if (isFlippedHorizontal || isFlippedVertical || kotlin.math.abs(videoScale - 1f) >= 0.01f) {
                                PlayerSettingsRow("恢复默认") {
                                    if (isFlippedHorizontal) onFlipHorizontal()
                                    if (isFlippedVertical) onFlipVertical()
                                    if (kotlin.math.abs(videoScale - 1f) >= 0.01f) onVideoScaleChange(1f)
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
                            sleepWaitForCurrentVideoEnd, onSleepWaitForCurrentVideoEndChange,
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
                    subtitle = if (danmakuHotBarEnabled) "固定预留顶部一行，按内容长度自动显示 1～3 条完整横幅" else "已关闭，普通弹幕照常显示",
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
