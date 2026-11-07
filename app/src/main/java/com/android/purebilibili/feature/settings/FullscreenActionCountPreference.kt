package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.FullscreenActionCount
import com.android.purebilibili.core.store.FullscreenActionCountSettings
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.AppText
import kotlinx.coroutines.launch

@Composable
fun FullscreenActionCountPreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visible by remember(context) { FullscreenActionCountSettings.getVisibleCounts(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    var editing by remember { mutableStateOf(false) }
    Box(modifier) {
        AppPreference(
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_ACTION_COUNTS),
            title = FullscreenActionCountSettings.TITLE,
            subtitle = visible?.let { counts ->
                counts.joinToString("、") { it.title }.ifEmpty { "全部关闭" }
            } ?: "分别设置点赞数、评论数和收藏数",
            onClick = { editing = true },
        )
    }
    if (editing) {
        AppAlertDialog(
            onDismissRequest = { editing = false },
            title = { AppText(FullscreenActionCountSettings.TITLE) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FullscreenActionCount.entries.forEach { count ->
                        AppSwitchPreference(
                            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_ACTION_COUNTS),
                            title = count.title,
                            checked = visible?.contains(count) == true,
                            enabled = visible != null,
                            onCheckedChange = { checked ->
                                scope.launch { FullscreenActionCountSettings.setVisible(context, count, checked) }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                AppDialogAction(onClick = { editing = false }) { AppText("完成") }
            },
        )
    }
}
