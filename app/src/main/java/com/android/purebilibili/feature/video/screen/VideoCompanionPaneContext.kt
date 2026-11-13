package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.compositionLocalOf

/** 外层已提供列表/详情分栏时，详情内部使用纵向简介与相关推荐。 */
internal val LocalVideoCompanionPane = compositionLocalOf { false }
