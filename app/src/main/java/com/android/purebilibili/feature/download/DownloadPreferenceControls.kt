package com.android.purebilibili.feature.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.store.DownloadPreferences
import com.android.purebilibili.core.store.DownloadPreferencesStore
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppText

@Stable
internal class DownloadPreferenceState(preferences: DownloadPreferences, initialQuality: Int) {
    var selectedQuality by mutableIntStateOf(initialQuality)
    var includeDanmaku by mutableStateOf(!preferences.rememberSelection || preferences.includeDanmaku)
    var rememberSelection by mutableStateOf(preferences.rememberSelection)
    var saving by mutableStateOf(false)
        private set

    suspend fun confirm(context: android.content.Context, onConfirm: (Int, DownloadOptions) -> Unit) {
        if (saving) return
        val quality = selectedQuality
        val options = DownloadOptions(includeDanmaku = includeDanmaku)
        val preferences = DownloadPreferences(rememberSelection, quality, includeDanmaku)
        saving = true
        try {
            DownloadPreferencesStore.save(context, preferences)
            onConfirm(quality, options)
        } finally {
            saving = false
        }
    }
}

// 等偏好读取后再创建编辑状态，避免默认值覆盖已记住的选择或用户输入。
@Composable
internal fun rememberDownloadPreferenceState(
    qualityOptions: List<Pair<Int, String>>,
    currentQuality: Int,
): DownloadPreferenceState? {
    val context = LocalContext.current.applicationContext
    val state by produceState<DownloadPreferenceState?>(null, context) {
        val preferences = DownloadPreferencesStore.read(context)
        value = DownloadPreferenceState(
            preferences,
            resolvePreferredDownloadQuality(
                qualityOptions.map { it.first },
                preferences.quality.takeIf { preferences.rememberSelection },
                currentQuality,
            ),
        )
    }
    return state
}

@Composable
internal fun DownloadPreferenceControls(state: DownloadPreferenceState, modifier: Modifier = Modifier) {
    Column(modifier) {
        DownloadCheckboxRow(
            checked = state.includeDanmaku,
            onCheckedChange = { state.includeDanmaku = it },
            title = "同时缓存弹幕",
        )
        DownloadCheckboxRow(
            checked = state.rememberSelection,
            onCheckedChange = { state.rememberSelection = it },
            title = "记住画质和弹幕选项",
        )
        AppText(
            text = if (state.rememberSelection) {
                "确认下载时保存，下次自动选中，可随时修改"
            } else {
                "开启后，下次下载会自动选中本次画质和弹幕选项"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DownloadCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppCheckbox(checked = checked, onCheckedChange = null)
        AppText(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
