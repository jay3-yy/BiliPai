package com.android.purebilibili.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.ContentDisplayPreferences
import com.android.purebilibili.core.store.ScrollFeedbackPreset
import com.android.purebilibili.core.ui.components.AppPreferenceDivider
import com.android.purebilibili.core.ui.components.AppPreferenceGroup
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppSwitchPreference
import com.android.purebilibili.core.ui.components.LocalContentImageSettings
import com.android.purebilibili.core.ui.components.LocalScrollFeedbackPreset
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import android.content.Context
import android.widget.Toast

internal object ContentDisplaySetting {
    const val LIST_QUALITY = "appearance.content_list_image_quality"
    const val PREVIEW_QUALITY = "appearance.content_preview_image_quality"
    const val DARK_DIM = "appearance.content_dark_image_dim"
    const val SCROLL_FEEDBACK = "animation.scroll_feedback_preset"
    const val REMEMBER_PLAYER_ADJUSTMENTS = "playback.remember_player_adjustments"
    const val ROTATION_SENSITIVITY = "playback.rotation_sensitivity"
    const val PLAYER_CONTROLS_TIMEOUT = "playback.controls_timeout"
    const val DYNAMIC_VISIBILITY_CHECK = "home.dynamic_visibility_check"
    const val KEEP_RECOMMEND = "home.recommend_keep_old_content"

    fun title(id: String): String = when (id) {
        LIST_QUALITY -> "列表图片质量"
        PREVIEW_QUALITY -> "大图预览质量"
        DARK_DIM -> "深色模式图片减亮"
        SCROLL_FEEDBACK -> "滑动回弹手感"
        KEEP_RECOMMEND -> "刷新保留旧推荐"
        REMEMBER_PLAYER_ADJUSTMENTS -> "播放器内调整保存方式"
        ROTATION_SENSITIVITY -> "旋转灵敏度"
        PLAYER_CONTROLS_TIMEOUT -> "播放控件显示时长"
        DYNAMIC_VISIBILITY_CHECK -> "发布后检查动态可见性"
        else -> error("未知显示设置：$id")
    }

    fun qualityLabel(value: Int, preview: Boolean): String = when (value) {
        0 -> if (preview) "原图" else "跟随省流量模式"
        40 -> "省流 · 40%"
        70 -> "均衡 · 70%"
        90 -> "清晰 · 90%"
        else -> "最高 · 100%"
    }
    fun dimLabel(value: Int) = if (value == 0) "关闭" else "减亮 $value%"
}

@Composable
internal fun ContentImagePreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalContentImageSettings.current
    AppPreferenceGroup(modifier = modifier) {
        SettingsItemAnchor(ContentDisplaySetting.LIST_QUALITY) {
            SettingsSingleChoicePreference(
                title = ContentDisplaySetting.title(ContentDisplaySetting.LIST_QUALITY),
                icon = rememberSettingsSemanticIcon(SettingsIconRole.LIST_IMAGE_QUALITY),
                subtitle = "封面与内容缩略图；省流量模式开启时优先降低图片质量",
                selectedValue = settings.listQuality,
                options = listOf(0, 40, 70, 90, 100).map { AppSegmentOption(it, ContentDisplaySetting.qualityLabel(it, false)) },
                onSelectionChange = { scope.savePreference(context) { ContentDisplayPreferences.setImage(context, ContentDisplayPreferences.listQualityKey, it) } },
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor(ContentDisplaySetting.PREVIEW_QUALITY) {
            SettingsSingleChoicePreference(
                title = ContentDisplaySetting.title(ContentDisplaySetting.PREVIEW_QUALITY),
                icon = rememberSettingsSemanticIcon(SettingsIconRole.PREVIEW_IMAGE_QUALITY),
                subtitle = "仅调整打开大图时的质量；查看原图和保存图片不受影响",
                selectedValue = settings.previewQuality,
                options = listOf(0, 40, 70, 90, 100).map { AppSegmentOption(it, ContentDisplaySetting.qualityLabel(it, true)) },
                onSelectionChange = { scope.savePreference(context) { ContentDisplayPreferences.setImage(context, ContentDisplayPreferences.previewQualityKey, it) } },
            )
        }
        AppPreferenceDivider()
        SettingsItemAnchor(ContentDisplaySetting.DARK_DIM) {
            SettingsSingleChoicePreference(
                title = ContentDisplaySetting.title(ContentDisplaySetting.DARK_DIM),
                icon = rememberSettingsSemanticIcon(SettingsIconRole.DARK_IMAGE_DIM),
                subtitle = "深色主题下减暗封面与内容缩略图，不改变视频、原图和保存文件",
                selectedValue = settings.darkDimPercent,
                options = listOf(0, 10, 20, 30).map { AppSegmentOption(it, ContentDisplaySetting.dimLabel(it)) },
                onSelectionChange = { scope.savePreference(context) { ContentDisplayPreferences.setImage(context, ContentDisplayPreferences.darkDimKey, it) } },
            )
        }
    }
}

@Composable
internal fun RecommendRetentionPreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keep by remember(context) { ContentDisplayPreferences.keepRecommend(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    SettingsItemAnchor(ContentDisplaySetting.KEEP_RECOMMEND, modifier) {
        AppSwitchPreference(
            title = ContentDisplaySetting.title(ContentDisplaySetting.KEEP_RECOMMEND),
            subtitle = "刷新推荐后把新内容放在前面，保留之前的内容；与位置提示分别控制",
            icon = rememberSettingsSemanticIcon(SettingsIconRole.RECOMMEND_RETENTION),
            checked = keep,
            onCheckedChange = { scope.savePreference(context) { ContentDisplayPreferences.setKeepRecommend(context, it) } },
        )
    }
}

@Composable
internal fun ScrollFeedbackPreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preset = LocalScrollFeedbackPreset.current
    SettingsItemAnchor(ContentDisplaySetting.SCROLL_FEEDBACK, modifier) {
        SettingsSingleChoicePreference(
            title = ContentDisplaySetting.title(ContentDisplaySetting.SCROLL_FEEDBACK),
            icon = rememberSettingsSemanticIcon(SettingsIconRole.SCROLL_FEEDBACK),
            subtitle = "调整首页下拉释放与底栏收合的回弹；标准保留原有手感，减少动态效果时优先停用",
            selectedValue = preset,
            options = ScrollFeedbackPreset.entries.map { AppSegmentOption(it, it.label) },
            onSelectionChange = { scope.savePreference(context) { ContentDisplayPreferences.setSpring(context, it) } },
        )
    }
}

@Composable
internal fun PlayerControlsTimeoutPreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = com.android.purebilibili.feature.video.ui.overlay.LocalPlayerControlsTimeoutSeconds.current
    SettingsItemAnchor(ContentDisplaySetting.PLAYER_CONTROLS_TIMEOUT, modifier) {
        SettingsSingleChoicePreference(
            title = ContentDisplaySetting.title(ContentDisplaySetting.PLAYER_CONTROLS_TIMEOUT),
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_CONTROLS_TIMEOUT),
            subtitle = "适用于视频、番剧、直播和小窗；读屏或键盘焦点停留时保持可见",
            selectedValue = selected,
            options = listOf(0 to "跟随默认", 8 to "8 秒", 15 to "15 秒", 30 to "30 秒", -1 to "不自动隐藏").map { AppSegmentOption(it.first, it.second) },
            onSelectionChange = { scope.savePreference(context) { ContentDisplayPreferences.setPlayerControlsTimeout(context, it) } },
        )
    }
}

@Composable
internal fun DynamicVisibilityCheckPreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by remember(context) { ContentDisplayPreferences.dynamicVisibilityCheck(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    SettingsItemAnchor(ContentDisplaySetting.DYNAMIC_VISIBILITY_CHECK, modifier) {
        AppSwitchPreference(
            title = ContentDisplaySetting.title(ContentDisplaySetting.DYNAMIC_VISIBILITY_CHECK),
            icon = rememberSettingsSemanticIcon(SettingsIconRole.DYNAMIC_VISIBILITY_CHECK),
            subtitle = "发布或转发后，检查未登录时能否看到；私密和定时发布不立即检查",
            checked = enabled,
            onCheckedChange = { scope.savePreference(context) { ContentDisplayPreferences.setDynamicVisibilityCheck(context, it) } },
        )
    }
}

@Composable
internal fun PlayerAdjustmentPersistencePreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rememberAdjustments by remember(context) { ContentDisplayPreferences.rememberPlayerAdjustments(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    SettingsItemAnchor(ContentDisplaySetting.REMEMBER_PLAYER_ADJUSTMENTS, modifier) {
        SettingsSingleChoicePreference(
            title = ContentDisplaySetting.title(ContentDisplaySetting.REMEMBER_PLAYER_ADJUSTMENTS),
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_ADJUSTMENT_PERSISTENCE),
            subtitle = "倍速、比例、后台播放和循环/随机顺序可临时调整；默认值、弹幕与字幕独立保存",
            selectedValue = rememberAdjustments,
            options = listOf(AppSegmentOption(true, "按原设置保存"), AppSegmentOption(false, "仅本次播放")),
            onSelectionChange = { scope.savePreference(context) { ContentDisplayPreferences.setRememberPlayerAdjustments(context, it) } },
        )
    }
}

@Composable
internal fun PlayerRotationSensitivityPreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sensitivity by remember(context) { ContentDisplayPreferences.rotationSensitivity(context) }
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.util.PlayerRotationSensitivity.STANDARD)
    SettingsItemAnchor(ContentDisplaySetting.ROTATION_SENSITIVITY, modifier) {
        SettingsSingleChoicePreference(
            title = ContentDisplaySetting.title(ContentDisplaySetting.ROTATION_SENSITIVITY),
            icon = rememberSettingsSemanticIcon(SettingsIconRole.PLAYER_ROTATION_SENSITIVITY),
            subtitle = "自动横竖屏开启时生效；稳重需转得更正，灵敏更早响应，标准沿用原有判断",
            selectedValue = sensitivity,
            options = com.android.purebilibili.core.util.PlayerRotationSensitivity.entries.map { AppSegmentOption(it, it.label) },
            onSelectionChange = { scope.savePreference(context) { ContentDisplayPreferences.setRotationSensitivity(context, it) } },
        )
    }
}

private fun CoroutineScope.savePreference(context: Context, save: suspend () -> Unit) = launch {
    try {
        save()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
    }
}
