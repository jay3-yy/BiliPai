package com.android.purebilibili.core.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeCoverReturnPrefetchStructureTest {

    @Test
    fun appNavigationTriggersPrefetchOnGestureAndCommit() {
        val source = appNavigationSource()

        assertTrue(source.contains("onNativeVideoBackProgress = { _, _, progress ->"))
        assertTrue(source.contains("if (progress > 0f) {"))
        assertTrue(source.contains("maybePrefetchHomeCoversForVideoReturn()"))
        // 返回提交兜底：普通返回(顶部按钮)也预热
        assertTrue(source.contains("onPrepareVideoCardSharedReturn = {"))
        assertFalse(source.contains("shouldUseClassicBackForVideoSharedElementReturn("))
    }

    private fun appNavigationSource(): String = listOf(
        File("app/src/main/java/com/android/purebilibili/navigation/AppNavigation.kt"),
        File("src/main/java/com/android/purebilibili/navigation/AppNavigation.kt")
    ).first { it.exists() }.readText()
}
