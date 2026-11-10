package com.Android.purebilibili.feature.video

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.plugin.skin.InstalledUiSkinPackage
import com.android.purebilibili.core.plugin.skin.LocalUiSkinState
import com.android.purebilibili.core.plugin.skin.UiSkinAssets
import com.android.purebilibili.core.plugin.skin.UiSkinManifest
import com.android.purebilibili.core.plugin.skin.UiSkinState
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import com.android.purebilibili.core.ui.LocalMaidLottieAnimationEnabled
import com.android.purebilibili.feature.video.ui.components.LikeBurstAnimation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LikeFeedbackSettingsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun disabledLikeFeedbackSkipsSkinAndBuiltInArtAndConsumesEachEvent() {
        val enabled = mutableStateOf(false)
        val visible = mutableStateOf(true)
        val skinEnabled = mutableStateOf(false)
        var completed = 0
        val skin = InstalledUiSkinPackage(
            manifest = UiSkinManifest(
                formatVersion = 1, skinId = "feedback-test", displayName = "Feedback", version = "1",
                apiVersion = 1, surfaces = setOf(UiSkinSurface.LIKE_EFFECT),
                assets = UiSkinAssets(likeEffectPreview = "like.png"),
            ),
            packageSha256 = "feedback-test", packagePath = "", installedAtMillis = 0L,
            assetFiles = mapOf("like.png" to "/unused/like.png"),
        )
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(
                LocalMaidLottieAnimationEnabled provides enabled.value,
                LocalUiSkinState provides UiSkinState(skinEnabled.value, skin),
            ) {
                LikeBurstAnimation(
                    visible = visible.value,
                    modifier = Modifier.testTag("like_feedback_art"),
                    onAnimationEnd = { completed++; visible.value = false },
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("like_feedback_art").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(1, completed)
            skinEnabled.value = true
            visible.value = true
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("like_feedback_art").assertDoesNotExist()
        rule.runOnIdle { assertEquals(2, completed); enabled.value = true }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("like_feedback_art").assertDoesNotExist()
        rule.runOnIdle { assertEquals(2, completed) }
    }
}
