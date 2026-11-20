package com.android.purebilibili.feature.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.components.AppPreference
import com.android.purebilibili.feature.settings.SettingsIconRole
import com.android.purebilibili.feature.settings.SettingsNavigationContainerOrigin
import com.android.purebilibili.feature.settings.rememberSettingsSemanticIcon
import com.android.purebilibili.navigation3.BiliPaiNavKey

@Composable
internal fun ContentFilterHistoryPreference(onClick: () -> Unit) {
    SettingsNavigationContainerOrigin(
        target = BiliPaiNavKey.ContentFilterHistory,
        onClick = onClick,
    ) { contentModifier, open ->
        AppPreference(
            title = stringResource(R.string.filtered_content_history_title),
            subtitle = stringResource(R.string.filtered_content_history_entry),
            icon = rememberSettingsSemanticIcon(SettingsIconRole.CONTENT_FILTER_HISTORY),
            onClick = open,
            contentModifier = contentModifier,
        )
    }
}
