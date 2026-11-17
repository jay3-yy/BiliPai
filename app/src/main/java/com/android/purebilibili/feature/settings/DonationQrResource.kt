package com.android.purebilibili.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.isAppInDarkTheme

/** 展示、展开过渡与保存共用应用当前主题对应的赞赏码原图。 */
@Composable
@DrawableRes
internal fun donationQrResource(): Int =
    if (isAppInDarkTheme()) R.drawable.author_qr_dark else R.drawable.author_qr
