package com.android.purebilibili.feature.video.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager

@Composable
fun QualitySelectionMenu(
    qualities: List<String>, qualityIds: List<Int> = emptyList(),
    switchableQualityIds: List<Int> = emptyList(), currentQuality: String,
    isLoggedIn: Boolean = false, isVip: Boolean = false,
    onQualitySelected: (Int) -> Unit, onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") useDialog: Boolean = false,
    placement: PlayerListPopupPlacement = PlayerListPopupPlacement.CENTER,
) {
    PlayerMiuixListPopup(title = "画质选择", onDismissRequest = onDismiss, placement = placement) {
        qualities.forEachIndexed { index, quality ->
            val qualityId = qualityIds.getOrNull(index) ?: 0
            val selected = quality == currentQuality
            val hint = when {
                (qualityId == 100 || qualityId >= 112) && !isVip -> "需要大会员"
                qualityId >= 80 && !isLoggedIn -> "登录后可用"
                // Some portrait playback paths do not provide the optional
                // switchable list even though the API returned valid tracks.
                // An empty list means "unknown", not "nothing is selectable".
                switchableQualityIds.isNotEmpty() && qualityId !in switchableQualityIds -> "当前视频不可切换"
                else -> null
            }
            DropdownImpl(
                item = DropdownItem(text = quality, summary = hint),
                optionSize = qualities.size, isSelected = selected, index = index,
                enabled = hint == null && !selected,
                onSelectedIndexChange = onQualitySelected,
            )
        }
    }
}

enum class SpeedSelectionMenuPlacement { CENTER, RIGHT_SIDE }

@Composable
fun SpeedSelectionMenu(
    currentSpeed: Float, onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit, placement: SpeedSelectionMenuPlacement,
) {
    val context = LocalContext.current
    val speedOptions by SettingsManager.getPlaybackSpeedOptions(context)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val options = remember(speedOptions, currentSpeed) {
        if (currentSpeed in speedOptions) speedOptions.asReversed()
        else (speedOptions + currentSpeed).sortedDescending()
    }
    PlayerMiuixListPopup(
        title = "播放速度", onDismissRequest = onDismiss,
        placement = if (placement == SpeedSelectionMenuPlacement.RIGHT_SIDE) {
            PlayerListPopupPlacement.END
        } else PlayerListPopupPlacement.CENTER,
    ) {
        options.forEachIndexed { index, speed ->
            val selected = speed == currentSpeed
            DropdownImpl(
                item = DropdownItem(text = PlaybackSpeed.formatSpeedFull(speed)),
                optionSize = options.size, isSelected = selected, index = index,
                enabled = !selected,
                onSelectedIndexChange = { onSpeedSelected(speed) },
            )
        }
    }
}

@Composable
fun SpeedSelectionMenuDialog(
    currentSpeed: Float, onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
    placement: SpeedSelectionMenuPlacement = SpeedSelectionMenuPlacement.CENTER,
) = SpeedSelectionMenu(currentSpeed, onSpeedSelected, onDismiss, placement)
