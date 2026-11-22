package com.android.purebilibili.feature.list

/** 底栏搜索联动开启时，允许将关键词提交给当前底栏列表。 */
internal fun isListScopedSearchActive(
    bottomBarSearchEnabled: Boolean,
    listScopedSearchEnabled: Boolean,
): Boolean = bottomBarSearchEnabled && listScopedSearchEnabled
