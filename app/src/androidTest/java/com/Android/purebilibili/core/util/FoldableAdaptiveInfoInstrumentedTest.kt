package com.android.purebilibili.core.util

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.layout.FoldingFeature.Orientation.Companion.HORIZONTAL
import androidx.window.layout.FoldingFeature.Orientation.Companion.VERTICAL
import androidx.window.layout.FoldingFeature.State.Companion.FLAT
import androidx.window.layout.FoldingFeature.State.Companion.HALF_OPENED
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoldableAdaptiveInfoInstrumentedTest {
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule(order = 2)
    val windowLayoutInfoPublisherRule = WindowLayoutInfoPublisherRule()

    @Test
    fun publishedVerticalHinge_isExposedAsInnerBookDisplay() {
        composeRule.setContent {
            val materialInfo =
                androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2()
            val appInfo = rememberAppWindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass(
                    widthSizeClass = WindowWidthSizeClass.Expanded,
                    heightSizeClass = WindowHeightSizeClass.Medium,
                    widthDp = 900.dp,
                    heightDp = 700.dp,
                    deviceWidthSizeClass = WindowWidthSizeClass.Expanded,
                ),
                windowPosture = materialInfo.windowPosture,
            )
            Text(
                text = "${appInfo.displayContext.foldableDisplayRole}:" +
                    "${appInfo.posture}:${appInfo.shouldAvoidHinge}",
                modifier = Modifier.testTag(ADAPTIVE_INFO_TAG),
            )
        }

        val hinge = FoldingFeature(
            activity = composeRule.activity,
            state = HALF_OPENED,
            orientation = VERTICAL,
            size = 2,
        )
        windowLayoutInfoPublisherRule.overrideWindowLayoutInfo(
            TestWindowLayoutInfo(listOf(hinge))
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ADAPTIVE_INFO_TAG)
            .assertTextEquals("Inner:Book:true")
    }

    @Test
    fun publishedHorizontalHinge_isExposedAsInnerTabletopDisplay() {
        setAdaptiveInfoContent()
        windowLayoutInfoPublisherRule.overrideWindowLayoutInfo(
            TestWindowLayoutInfo(
                listOf(
                    FoldingFeature(
                        activity = composeRule.activity,
                        state = HALF_OPENED,
                        orientation = HORIZONTAL,
                        size = 2,
                    )
                )
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ADAPTIVE_INFO_TAG)
            .assertTextEquals("Inner:Tabletop:true")
    }

    @Test
    fun publishedFlatHinge_isExposedAsInnerFlatDisplay() {
        setAdaptiveInfoContent()
        windowLayoutInfoPublisherRule.overrideWindowLayoutInfo(
            TestWindowLayoutInfo(
                listOf(
                    FoldingFeature(
                        activity = composeRule.activity,
                        state = FLAT,
                        orientation = VERTICAL,
                        size = 0,
                    )
                )
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ADAPTIVE_INFO_TAG)
            .assertTextEquals("Inner:Flat:false")
    }

    @Test
    fun publishedOccludingAndMultipleHinges_exposeInnerPostureWithoutCrossingTheHinge() {
        setAdaptiveInfoContent()
        windowLayoutInfoPublisherRule.overrideWindowLayoutInfo(
            TestWindowLayoutInfo(
                listOf(
                    FoldingFeature(
                        activity = composeRule.activity,
                        state = FLAT,
                        orientation = VERTICAL,
                        size = 24,
                    ),
                    FoldingFeature(
                        activity = composeRule.activity,
                        state = FLAT,
                        orientation = VERTICAL,
                        size = 24,
                    ),
                )
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ADAPTIVE_INFO_TAG)
            .assertTextEquals("Inner:Flat:true")
    }

    private fun setAdaptiveInfoContent() {
        composeRule.setContent {
            val materialInfo =
                androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2()
            val appInfo = rememberAppWindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass(
                    widthSizeClass = WindowWidthSizeClass.Expanded,
                    heightSizeClass = WindowHeightSizeClass.Medium,
                    widthDp = 900.dp,
                    heightDp = 700.dp,
                    deviceWidthSizeClass = WindowWidthSizeClass.Expanded,
                ),
                windowPosture = materialInfo.windowPosture,
            )
            Text(
                text = "${appInfo.displayContext.foldableDisplayRole}:" +
                    "${appInfo.posture}:${appInfo.shouldAvoidHinge}",
                modifier = Modifier.testTag(ADAPTIVE_INFO_TAG),
            )
        }
    }

    private companion object {
        const val ADAPTIVE_INFO_TAG = "adaptive-info"
    }
}
