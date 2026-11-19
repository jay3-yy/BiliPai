package com.android.purebilibili.feature.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.feature.settings.SettingsIconRole
import com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon

@Composable
internal fun ContentFilterHistoryPreference(onClick: () -> Unit) {
    AppPreference(
        title = stringResource(R.string.filtered_content_history_title),
        subtitle = stringResource(R.string.filtered_content_history_entry),
        icon = rememberSettingsSemanticIcon(SettingsIconRole.CONTENT_FILTER_HISTORY),
        onClick = onClick,
    )
}
