package com.android.purebilibili.feature.profile

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfilePageLoadEffectUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun remountAndRestoreDoNotForceReloadUntilANewActiveAccountRefresh() {
        val show = mutableStateOf(true)
        val active = mutableStateOf(true)
        val generation = mutableIntStateOf(1)
        val callbackRevision = mutableIntStateOf(1)
        val loads = mutableListOf<Pair<Int, Boolean>>()
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val holder = rememberSaveableStateHolder()
            if (show.value) {
                holder.SaveableStateProvider("profile") {
                    val revision = callbackRevision.intValue
                    ProfilePageLoadEffect(active.value, generation.intValue) { force ->
                        loads += revision to force
                    }
                }
            }
        }
        rule.runOnIdle { assertEquals(listOf(1 to true), loads) }
        rule.runOnIdle { show.value = false }
        rule.runOnIdle { show.value = true }
        rule.runOnIdle { assertEquals(listOf(1 to true, 1 to false), loads) }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(listOf(1 to true, 1 to false, 1 to false), loads) }
        rule.runOnIdle { callbackRevision.intValue = 2 }
        rule.runOnIdle { assertEquals(3, loads.size) }
        rule.runOnIdle { active.value = false }
        rule.runOnIdle { generation.intValue = 2 }
        rule.runOnIdle { assertEquals(3, loads.size) }
        rule.runOnIdle { active.value = true }
        rule.runOnIdle {
            assertEquals(2 to true, loads.last())
            assertEquals(2, loads.count { it.second })
        }
    }
}
