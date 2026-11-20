package com.android.purebilibili.feature.list

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListScopedSearchPolicyTest {

    @Test
    fun listScopedSearch_requiresBottomBarSearchEnabled() {
        assertTrue(
            isListScopedSearchActive(
                bottomBarSearchEnabled = true,
                listScopedSearchEnabled = true,
            )
        )
        assertFalse(
            isListScopedSearchActive(
                bottomBarSearchEnabled = false,
                listScopedSearchEnabled = true,
            )
        )
        assertFalse(
            isListScopedSearchActive(
                bottomBarSearchEnabled = true,
                listScopedSearchEnabled = false,
            )
        )
        assertFalse(
            isListScopedSearchActive(
                bottomBarSearchEnabled = false,
                listScopedSearchEnabled = false,
            )
        )
    }
}
