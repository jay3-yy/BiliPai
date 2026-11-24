package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText

@Composable
fun QualitySelectionMenu(
    qualities: List<String>, qualityIds: List<Int> = emptyList(),
    switchableQualityIds: List<Int> = emptyList(), currentQuality: String,
    isLoggedIn: Boolean = false, isVip: Boolean = false,
    onQualitySelected: (Int) -> Unit, onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") useDialog: Boolean = false,
    placement: PlayerListPopupPlacement = PlayerListPopupPlacement.CENTER,
    anchorBounds: IntRect? = null,
) {
    PlayerSelectionPopup(
        title = "画质选择", showTitle = false,
        onDismissRequest = onDismiss, placement = placement,
        anchorBounds = anchorBounds,
    ) {
        val unlockGuidance = remember(qualityIds, switchableQualityIds, isLoggedIn, isVip) {
            when {
                !isLoggedIn && qualityIds.any { it >= 80 && it !in switchableQualityIds } ->
                    "登录账号即可解锁 1080P 及以上高清画质"
                isLoggedIn && isVip && qualityIds.any { (it == 100 || it >= 112) && it !in switchableQualityIds } ->
                    "部分高帧率 / 4K / HDR 需在登录页通过扫码授权高画质"
                else -> null
            }
        }
        qualities.forEachIndexed { index, quality ->
            val qualityId = qualityIds.getOrNull(index) ?: 0
            val selected = quality == currentQuality
            val hint = qualityUnavailableReason(
                qualityId = qualityId,
                playableQualityIds = switchableQualityIds,
                isLoggedIn = isLoggedIn,
                isVip = isVip
            )
            PlayerSelectionItem(
                modifier = Modifier.playerPopupSelectedOption(selected),
                text = quality, summary = hint, selected = selected,
                index = index, optionCount = qualities.size,
                enabled = hint == null && !selected,
                onClick = { onQualitySelected(index) },
            )
        }
        if (unlockGuidance != null) {
            AppText(
                text = unlockGuidance,
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

enum class SpeedSelectionMenuPlacement { CENTER, RIGHT_SIDE, ANCHOR_END }

/** 未加载的配置不能被当前速度补成单项菜单；已加载的自定义列表保持原样。 */
internal fun resolveSpeedSelectionMenuOptions(
    speedOptions: List<Float>?,
    currentSpeed: Float,
): List<Float>? {
    val loaded = speedOptions ?: return null
    return if (currentSpeed in loaded) loaded else (loaded + currentSpeed).sorted()
}

@Composable
fun SpeedSelectionMenu(
    currentSpeed: Float, onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit, placement: SpeedSelectionMenuPlacement,
    anchorBounds: IntRect? = null,
) {
    val context = LocalContext.current
    val speedOptionsFlow = remember(context) { SettingsManager.getPlaybackSpeedOptions(context) }
    val speedOptions by speedOptionsFlow
        .collectAsStateWithLifecycle<List<Float>?>(initialValue = null)
    val options = remember(speedOptions, currentSpeed) {
        resolveSpeedSelectionMenuOptions(speedOptions, currentSpeed)
    } ?: return
    PlayerSelectionPopup(
        title = "播放速度", showTitle = false, onDismissRequest = onDismiss,
        placement = when (placement) {
            SpeedSelectionMenuPlacement.RIGHT_SIDE -> PlayerListPopupPlacement.END
            SpeedSelectionMenuPlacement.CENTER -> PlayerListPopupPlacement.CENTER
            SpeedSelectionMenuPlacement.ANCHOR_END -> PlayerListPopupPlacement.ANCHOR_END
        },
        anchorBounds = anchorBounds,
    ) {
        options.forEachIndexed { index, speed ->
            val selected = speed == currentSpeed
            PlayerSelectionItem(
                modifier = Modifier.playerPopupSelectedOption(selected),
                text = "${speed}X", selected = selected,
                index = index, optionCount = options.size,
                enabled = !selected,
                onClick = { onSpeedSelected(speed) },
            )
        }
    }
}

@Composable
fun SpeedSelectionMenuDialog(
    currentSpeed: Float, onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
    placement: SpeedSelectionMenuPlacement = SpeedSelectionMenuPlacement.CENTER,
    anchorBounds: IntRect? = null,
) = SpeedSelectionMenu(currentSpeed, onSpeedSelected, onDismiss, placement, anchorBounds)
