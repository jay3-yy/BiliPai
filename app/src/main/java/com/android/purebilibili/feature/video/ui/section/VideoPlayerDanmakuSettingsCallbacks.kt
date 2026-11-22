package com.android.purebilibili.feature.video.ui.section

import android.content.Context
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlayActions

// 只组装弹幕设置事件，保留其他控制回调。保存工作由播放器宿主的 UI 作用域启动，
// 即时渲染更新与持久化请求沿用原有顺序；当前显示范围由宿主传入。
internal fun VideoPlayerOverlayActions.withPlayerDanmakuSettingsCallbacks(
    context: Context,
    danmakuManager: DanmakuManager,
    danmakuEnabled: Boolean,
    activeDanmakuScope: DanmakuSettingsScope,
    launchSettingsWrite: (suspend () -> Unit) -> Unit,
): VideoPlayerOverlayActions = copy(
    onDanmakuToggle = {
        val newState = !danmakuEnabled
        danmakuManager.isEnabled = newState
        if (!newState) {
            danmakuManager.clear()
        }
        launchSettingsWrite {
            SettingsManager.setDanmakuEnabled(
                context,
                newState,
                activeDanmakuScope
            )
        }
        //  记录弹幕开关事件
        com.android.purebilibili.core.util.AnalyticsHelper.logDanmakuToggle(newState)
    },
    onDanmakuSliderPreview = { parameter, value ->
        com.android.purebilibili.feature.video.ui.components.applyDanmakuSliderPreview(danmakuManager, parameter, value)
    },
    onDanmakuOpacityChange = { value ->
        danmakuManager.opacity = value
        launchSettingsWrite {
            SettingsManager.setDanmakuOpacity(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuFontScaleChange = { value ->
        danmakuManager.fontScale = value
        launchSettingsWrite {
            SettingsManager.setDanmakuFontScale(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuFontWeightChange = { value ->
        danmakuManager.fontWeight = value
        launchSettingsWrite {
            SettingsManager.setDanmakuFontWeight(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuSpeedChange = { value ->
        danmakuManager.speedFactor = value
        launchSettingsWrite {
            SettingsManager.setDanmakuSpeed(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuDisplayAreaChange = { value ->
        danmakuManager.displayArea = value
        launchSettingsWrite {
            SettingsManager.setDanmakuArea(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuStrokeWidthChange = { value ->
        danmakuManager.strokeWidth = value
        launchSettingsWrite {
            SettingsManager.setDanmakuStrokeWidth(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuLineHeightChange = { value ->
        danmakuManager.lineHeight = value
        launchSettingsWrite {
            SettingsManager.setDanmakuLineHeight(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuScrollDurationSecondsChange = { value ->
        danmakuManager.scrollDurationSeconds = value
        launchSettingsWrite {
            SettingsManager.setDanmakuScrollDurationSeconds(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuStaticDurationSecondsChange = { value ->
        danmakuManager.staticDurationSeconds = value
        launchSettingsWrite {
            SettingsManager.setDanmakuStaticDurationSeconds(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuScrollFixedVelocityChange = { value ->
        danmakuManager.scrollFixedVelocity = value
        launchSettingsWrite {
            SettingsManager.setDanmakuScrollFixedVelocity(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuStaticToScrollChange = { value ->
        danmakuManager.staticDanmakuToScroll = value
        launchSettingsWrite {
            SettingsManager.setDanmakuStaticToScroll(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuMassiveModeChange = { value ->
        danmakuManager.massiveMode = value
        launchSettingsWrite {
            SettingsManager.setDanmakuMassiveMode(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuMergeDuplicatesChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuMergeDuplicates(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuDuplicateMergeWindowMsChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuDuplicateMergeWindowMs(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuDuplicateMergeCountThresholdChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuDuplicateMergeCountThreshold(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuWeightFilterLevelChange = { value ->
        launchSettingsWrite {
            SettingsManager
                .setDanmakuWeightFilterLevel(context, value)
        }
    },
    onDanmakuAllowScrollChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuAllowScroll(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuAllowTopChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuAllowTop(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuAllowBottomChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuAllowBottom(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuAllowColorfulChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuAllowColorful(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuAllowSpecialChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuAllowSpecial(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuHideInteractiveCommandsChange = { value ->
        launchSettingsWrite {
            SettingsManager
                .setDanmakuHideInteractiveCommands(context, value)
        }
    },
    onDanmakuSmartOcclusionChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuSmartOcclusion(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
    onDanmakuFullscreenPanelWidthModeChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuFullscreenPanelWidthMode(context, value)
        }
    },
    onPortraitDanmakuDisplayAreaModeChange = { value ->
        launchSettingsWrite {
            SettingsManager
                .setPortraitDanmakuDisplayAreaMode(context, value)
        }
    },
    onDanmakuBlockRulesRawChange = { value ->
        launchSettingsWrite {
            SettingsManager.setDanmakuBlockRulesRaw(
                context,
                value,
                activeDanmakuScope
            )
        }
    },
)
