package com.android.purebilibili.feature.video.ui.overlay

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.PlayerSettingsCache
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.MediaContrastPalette
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import kotlinx.coroutines.launch
import java.util.Locale

internal data class LowerQualityTarget(
    val index: Int,
    val qualityId: Int,
    val label: String,
)

internal data class SoftwareDecodeOverloadBannerState(
    val message: String,
    val showEnableHardwareAction: Boolean,
    val lowerQualityTarget: LowerQualityTarget?,
)

internal fun isSoftwareVideoDecodeActive(debugInfo: PlaybackDebugInfo): Boolean {
    if (debugInfo.videoDecoderType == "软件解码") return true
    if (debugInfo.videoDecoderType.isNotBlank()) return false
    val decoder = debugInfo.videoDecoder.lowercase(Locale.ROOT)
    return decoder.contains("software") ||
        decoder.contains("omx.google") ||
        decoder.contains("c2.android")
}

internal fun resolveLowerQualityTarget(
    currentQuality: Int,
    qualityIds: List<Int>,
    qualityLabels: List<String>,
    switchableQualityIds: List<Int> = emptyList(),
): LowerQualityTarget? {
    if (currentQuality <= 0 || qualityIds.isEmpty()) return null
    val allowed = switchableQualityIds.takeIf { it.isNotEmpty() }?.toSet()
    var bestIndex = -1
    var bestQualityId = -1
    for (index in qualityIds.indices) {
        val candidateId = qualityIds[index]
        if (candidateId <= 0 || candidateId >= currentQuality) continue
        if (allowed != null && candidateId !in allowed) continue
        if (candidateId > bestQualityId) {
            bestQualityId = candidateId
            bestIndex = index
        }
    }
    if (bestIndex < 0) return null
    val label = qualityLabels.getOrNull(bestIndex).orEmpty().ifBlank { "${bestQualityId}P" }
    return LowerQualityTarget(index = bestIndex, qualityId = bestQualityId, label = label)
}

internal fun resolveSoftwareDecodeOverloadBannerState(
    debugInfo: PlaybackDebugInfo,
    hwDecodeEnabled: Boolean,
    currentQuality: Int,
    qualityIds: List<Int>,
    qualityLabels: List<String>,
    switchableQualityIds: List<Int>,
    isScreenLocked: Boolean,
    suppressTransientOverlays: Boolean,
    dismissed: Boolean,
): SoftwareDecodeOverloadBannerState? {
    if (dismissed || isScreenLocked || suppressTransientOverlays) return null
    if (debugInfo.videoDecoderDropWarning.isBlank()) return null
    if (!isSoftwareVideoDecodeActive(debugInfo)) return null
    return SoftwareDecodeOverloadBannerState(
        message = debugInfo.videoDecoderDropWarning,
        showEnableHardwareAction = !hwDecodeEnabled,
        lowerQualityTarget = resolveLowerQualityTarget(
            currentQuality = currentQuality,
            qualityIds = qualityIds,
            qualityLabels = qualityLabels,
            switchableQualityIds = switchableQualityIds,
        ),
    )
}

@Composable
internal fun SoftwareDecodeOverloadOverlayHost(
    bvid: String,
    cid: Long,
    debugInfo: PlaybackDebugInfo,
    currentQuality: Int,
    qualityIds: List<Int>,
    qualityLabels: List<String>,
    switchableQualityIds: List<Int>,
    isScreenLocked: Boolean,
    suppressTransientOverlays: Boolean,
    onQualitySelected: (Int) -> Unit,
    onReloadVideo: () -> Unit,
    modifier: Modifier = Modifier,
    useGlass: Boolean = LocalPlayerControlGlassEnabled.current,
    onVisibilityChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaKey = "$bvid:$cid"
    var dismissed by remember(mediaKey) { mutableStateOf(false) }
    var hwDecodeEnabled by remember(mediaKey, debugInfo.videoDecoderDropWarning) {
        mutableStateOf(PlayerSettingsCache.isHwDecodeEnabled(context))
    }

    val bannerState = resolveSoftwareDecodeOverloadBannerState(
        debugInfo = debugInfo,
        hwDecodeEnabled = hwDecodeEnabled,
        currentQuality = currentQuality,
        qualityIds = qualityIds,
        qualityLabels = qualityLabels,
        switchableQualityIds = switchableQualityIds,
        isScreenLocked = isScreenLocked,
        suppressTransientOverlays = suppressTransientOverlays,
        dismissed = dismissed,
    )
    androidx.compose.runtime.SideEffect {
        onVisibilityChanged(bannerState != null)
    }

    AnimatedVisibility(
        visible = bannerState != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val activeState = bannerState ?: return@AnimatedVisibility
        PlayerDegradePromptBanner(
            message = activeState.message,
            dismissContentDescription = "关闭软解掉帧提示",
            primaryActionLabel = if (activeState.showEnableHardwareAction) {
                "开启硬件解码"
            } else {
                activeState.lowerQualityTarget?.let { "降至 ${it.label}" }
            },
            onPrimaryAction = if (activeState.showEnableHardwareAction) {
                {
                    dismissed = true
                    hwDecodeEnabled = true
                    val appContext = context.applicationContext
                    PlayerSettingsCache.setHwDecodeEnabled(appContext, true)
                    scope.launch {
                        SettingsManager.setHwDecode(appContext, true)
                    }
                    Toast.makeText(context, "已开启硬件解码，正在重新选流", Toast.LENGTH_SHORT).show()
                    onReloadVideo()
                }
            } else {
                activeState.lowerQualityTarget?.let { target ->
                    {
                        dismissed = true
                        Toast.makeText(context, "已切换至 ${target.label}", Toast.LENGTH_SHORT).show()
                        onQualitySelected(target.index)
                    }
                }
            },
            secondaryActionLabel = if (activeState.showEnableHardwareAction) {
                activeState.lowerQualityTarget?.let { "降至 ${it.label}" }
            } else null,
            onSecondaryAction = if (activeState.showEnableHardwareAction) {
                activeState.lowerQualityTarget?.let { target ->
                    {
                        dismissed = true
                        Toast.makeText(context, "已切换至 ${target.label}", Toast.LENGTH_SHORT).show()
                        onQualitySelected(target.index)
                    }
                }
            } else null,
            onDismiss = { dismissed = true },
            useGlass = useGlass,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerDegradePromptBanner(
    message: String,
    dismissContentDescription: String,
    primaryActionLabel: String?,
    onPrimaryAction: (() -> Unit)?,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    useGlass: Boolean = LocalPlayerControlGlassEnabled.current,
) {
    val shape = AppShapes.container(ContainerLevel.Card)
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MediaContrastPalette.Warning, CircleShape)
                )
                AppText(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = dismissContentDescription,
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .padding(2.dp),
                )
            }
            if ((primaryActionLabel != null && onPrimaryAction != null) ||
                (secondaryActionLabel != null && onSecondaryAction != null)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (primaryActionLabel != null && onPrimaryAction != null) {
                        OverloadQuickActionChip(
                            label = primaryActionLabel,
                            highlighted = true,
                            onClick = onPrimaryAction,
                        )
                    }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        OverloadQuickActionChip(
                            label = secondaryActionLabel,
                            highlighted = false,
                            onClick = onSecondaryAction,
                        )
                    }
                }
            }
        }
    }

    val controlBackdrop = currentPlayerControlBackdrop()
    val resolvedBackdrop = controlBackdrop ?: LocalPlayerPageBackdrop.current
    if (useGlass && resolvedBackdrop != null) {
        if (controlBackdrop != null) {
            PlayerControlGlassDock(
                enabled = true,
                shape = shape,
                minimumReadabilityScrimAlpha = 0.45f,
            ) {
                content()
            }
        } else {
            PlayerControlsBackdropHost(enabled = true) {
                PlayerControlGlassDock(
                    enabled = true,
                    shape = shape,
                    minimumReadabilityScrimAlpha = 0.45f,
                ) {
                    content()
                }
            }
        }
    } else {
        AppSurface(
            color = Color.Black.copy(alpha = 0.78f),
            contentColor = Color.White,
            shape = shape,
        ) {
            content()
        }
    }
}

@Composable
private fun OverloadQuickActionChip(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (highlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    } else {
        Color.White.copy(alpha = 0.16f)
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color.White
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
