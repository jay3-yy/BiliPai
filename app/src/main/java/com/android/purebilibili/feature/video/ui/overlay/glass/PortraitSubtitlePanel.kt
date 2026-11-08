package com.android.purebilibili.feature.video.ui.overlay.glass

import com.android.purebilibili.feature.video.ui.overlay.*

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.feature.video.ui.components.playerOptionContainerColor
import com.android.purebilibili.feature.video.ui.components.playerOptionContentColor
import com.android.purebilibili.feature.video.ui.components.playerOptionShape
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.SubtitleTrackOption
import com.android.purebilibili.feature.video.subtitle.buildSubtitleTrackOptions
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import com.android.purebilibili.feature.video.subtitle.normalizeSubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.normalizeSubtitleVerticalOffsetFraction
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleControlAvailability
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayModeByAutoPreference
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayOptions
import com.android.purebilibili.feature.video.subtitle.resolveSubtitlePositionPollingIdentity
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleTextAt
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleTextSizeSpec
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleTrackDisplayLabel
import com.android.purebilibili.feature.video.subtitle.shouldKeepSubtitleOverlayMounted
import com.android.purebilibili.feature.video.subtitle.shouldRenderPrimarySubtitle
import com.android.purebilibili.feature.video.subtitle.shouldRenderSecondarySubtitle
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.ui.components.PlayerSelectionItem
import com.android.purebilibili.feature.video.ui.components.PlayerSelectionPopup
import com.android.purebilibili.feature.video.ui.section.resolveSubtitleLanguageLabel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun PortraitSubtitlePanel(
    displayMode: SubtitleDisplayMode,
    primaryLabel: String,
    secondaryLabel: String,
    primaryAvailable: Boolean,
    secondaryAvailable: Boolean,
    trackOptions: List<SubtitleTrackOption>,
    largeTextEnabled: Boolean,
    positionLocked: Boolean,
    canResetPosition: Boolean = false,
    onDismiss: () -> Unit,
    onDisplayModeChange: (SubtitleDisplayMode) -> Unit,
    onTrackSelected: (String) -> Unit,
    onLargeTextChange: (Boolean) -> Unit,
    onPositionLockedChange: (Boolean) -> Unit,
    onResetPosition: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss)
    ) {
        AppSurface(
            modifier = modifier
                .widthIn(min = 148.dp, max = 228.dp)
                .clickable(enabled = false) {},
            shape = AppShapes.container(ContainerLevel.Dialog),
            color = Color.Black.copy(alpha = 0.82f)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppText(
                    text = "字幕显示",
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                resolveSubtitleDisplayOptions(
                    primaryLabel = primaryLabel,
                    secondaryLabel = secondaryLabel,
                    hasPrimaryTrack = primaryAvailable,
                    hasSecondaryTrack = secondaryAvailable
                ).forEach { option ->
                    val selected = displayMode == option.mode
                    AppSurface(
                        onClick = {
                            if (option.enabled) onDisplayModeChange(option.mode)
                        },
                        enabled = option.enabled,
                        shape = playerOptionShape(AppShapes.container(ContainerLevel.Field)),
                        color = playerOptionContainerColor(selected,
                                if (selected) AppContentColors.accent().copy(alpha = 0.22f) else Color.Transparent,
                                surface = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppText(
                            text = option.label,
                            color = playerOptionContentColor(selected,
                                if (selected) AppContentColors.accent() else Color.White.copy(alpha = if (option.enabled) 1f else 0.4f),
                                surface = Color.Black),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                if (trackOptions.isNotEmpty()) {
                    AppHorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                    AppText(
                        text = "字幕轨道",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                    )
                    trackOptions.forEach { option ->
                        AppSurface(
                            onClick = { onTrackSelected(option.trackKey) },
                            shape = playerOptionShape(AppShapes.container(ContainerLevel.Field)),
                            color = playerOptionContainerColor(option.selected,
                                if (option.selected) AppContentColors.accent().copy(alpha = 0.22f) else Color.Transparent,
                                surface = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppText(
                                text = option.label,
                                color = playerOptionContentColor(option.selected,
                                    if (option.selected) AppContentColors.accent() else Color.White,
                                    surface = Color.Black),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
                AppHorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    AppText(
                        text = "大字号",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    AppSwitch(
                        checked = largeTextEnabled,
                        onCheckedChange = onLargeTextChange
                    )
                }
                AppText(
                    text = if (positionLocked) "字幕位置已固定" else "上下拖动字幕可调整位置",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    AppText(
                        text = "锁定位置",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    AppSwitch(
                        checked = positionLocked,
                        onCheckedChange = onPositionLockedChange
                    )
                }
                if (canResetPosition) {
                    AppSurface(
                        onClick = onResetPosition,
                        shape = playerOptionShape(AppShapes.container(ContainerLevel.Field)),
                        color = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppText(
                            text = "重置位置",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 玻璃宿主可用时的字幕菜单：与同排倍速、画质、音质菜单共用统一玻璃弹窗。
 * 玻璃关闭或所在窗口没有同窗宿主时，调用方继续使用 [PortraitSubtitlePanel] 原生深色面板。
 */
@Composable
internal fun PortraitSubtitleGlassMenu(
    displayMode: SubtitleDisplayMode,
    primaryLabel: String,
    secondaryLabel: String,
    primaryAvailable: Boolean,
    secondaryAvailable: Boolean,
    trackOptions: List<SubtitleTrackOption>,
    largeTextEnabled: Boolean,
    positionLocked: Boolean,
    onDismiss: () -> Unit,
    onDisplayModeChange: (SubtitleDisplayMode) -> Unit,
    onTrackSelected: (String) -> Unit,
    onLargeTextChange: (Boolean) -> Unit,
    onPositionLockedChange: (Boolean) -> Unit,
) {
    PlayerSelectionPopup(
        title = "字幕",
        onDismissRequest = onDismiss,
        minWidth = 148.dp,
    ) {
        resolveSubtitleDisplayOptions(
            primaryLabel = primaryLabel,
            secondaryLabel = secondaryLabel,
            hasPrimaryTrack = primaryAvailable,
            hasSecondaryTrack = secondaryAvailable
        ).forEach { option ->
            PlayerSelectionItem(
                text = option.label,
                selected = displayMode == option.mode,
                enabled = option.enabled,
                onClick = { onDisplayModeChange(option.mode) },
            )
        }
        if (trackOptions.isNotEmpty()) {
            trackOptions.forEach { option ->
                PlayerSelectionItem(
                    text = option.label,
                    selected = option.selected,
                    onClick = {
                        onTrackSelected(option.trackKey)
                        onDisplayModeChange(SubtitleDisplayMode.PRIMARY_ONLY)
                    },
                )
            }
        }
        // 开关行点按整行切换且不关闭菜单，可在菜单内连续调整后点空白退出。
        AppDropdownMenuItem(
            text = { AppText("大字号", color = AppContentColors.primaryText()) },
            trailingIcon = { AppSwitch(checked = largeTextEnabled, onCheckedChange = null) },
            onClick = { onLargeTextChange(!largeTextEnabled) },
            dismissOnClick = false,
        )
        AppDropdownMenuItem(
            text = { AppText("锁定位置", color = AppContentColors.primaryText()) },
            trailingIcon = { AppSwitch(checked = positionLocked, onCheckedChange = null) },
            onClick = { onPositionLockedChange(!positionLocked) },
            dismissOnClick = false,
        )
    }
}
