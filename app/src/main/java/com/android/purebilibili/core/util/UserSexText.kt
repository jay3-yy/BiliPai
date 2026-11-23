package com.android.purebilibili.core.util

/** B 站 sex 字段只认这三种公开值；空串/未知值一律不展示。 */
fun resolveUserSexLabel(sex: String?): String? =
    sex?.trim()?.takeIf { it == "男" || it == "女" || it == "保密" }
