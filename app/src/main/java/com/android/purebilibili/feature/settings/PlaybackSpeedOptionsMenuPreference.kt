package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.PlaybackSpeedOptionsPreferenceControl

/** All materials use a compact entry; only the editor expands the full option list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSpeedOptionsMenuPreference(
    options: List<Float>,
    defaultSpeed: Float,
    onAddSpeed: (Float) -> Unit,
    onRemoveSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        AppPreference(
            icon = com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon(com.android.purebilibili.feature.settings.SettingsIconRole.PLAYBACK_SPEED),
            title = "播放器倍速列表",
            subtitle = "管理播放器菜单、双指调速和默认速度的可选倍速",
            value = "${options.size} 项",
            onClick = { editorVisible = true },
        )
    }
    if (editorVisible) {
        val maxEditorHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp
        AppModalBottomSheet(onDismissRequest = { editorVisible = false }) {
            PlaybackSpeedOptionsPreferenceControl(
                options = options,
                defaultSpeed = defaultSpeed,
                onAddSpeed = onAddSpeed,
                onRemoveSpeed = onRemoveSpeed,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxEditorHeight)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }
}
