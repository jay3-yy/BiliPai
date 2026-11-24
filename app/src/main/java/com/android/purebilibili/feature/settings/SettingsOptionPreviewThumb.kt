package com.android.purebilibili.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel

internal sealed interface SettingsOptionPreview {
    data class Feed(val columns: Int, val horizontalCards: Boolean = false) : SettingsOptionPreview
    data class Labels(val mode: Int, val atTop: Boolean = false) : SettingsOptionPreview
    data class HeaderOrder(val searchFirst: Boolean) : SettingsOptionPreview
    data class HeaderCollapse(val searchVisible: Boolean, val tabsVisible: Boolean) : SettingsOptionPreview
    data class Typography(val scale: Float = 1f, val weight: FontWeight? = null) : SettingsOptionPreview
    data class Density(val scale: Float) : SettingsOptionPreview
    data class Choice(val anchored: Boolean) : SettingsOptionPreview

    /** 界面预设小样:[miuix] 为真示意 Miuix 彩色容器条目,为假示意 MD3 单色条目。 */
    data class UiPreset(val miuix: Boolean) : SettingsOptionPreview

    /** 界面风格小样:[miuix] 为真示意 Miuix 整段底栏指示器,为假示意 MD3 胶囊指示器。 */
    data class ThemeStyle(val miuix: Boolean) : SettingsOptionPreview

    /** 列表条目样式小样:0 跟随预设(双风格)、1 项目自定义、2 原生组件。 */
    data class ListItem(val mode: Int) : SettingsOptionPreview

    /** 图标风格小样:0 跟随预设、1 主题色容器、2 MD3 单色、3 柔和彩色、4 鲜明彩色。 */
    data class IconChip(val mode: Int) : SettingsOptionPreview
}

/** 固定占位的装饰小样只显示选项差异，读屏和点按统一由选项行承接。 */
@Composable
internal fun SettingsOptionPreviewThumb(
    preview: SettingsOptionPreview,
    modifier: Modifier = Modifier,
) {
    val background = AppSurfaceTokens.background()
    val surface = AppSurfaceTokens.cardContainer()
    val accent = AppContentColors.accent()
    val muted = AppContentColors.secondaryText().copy(alpha = 0.42f)
    val foreground = AppContentColors.primaryText()
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiary
    val shape = AppShapes.container(ContainerLevel.Chip)
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 30.dp)
            .clip(shape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val unit = size.width / 44f
            fun tile(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 1f) {
                drawRoundRect(color, Offset(x * unit, y * unit), Size(w * unit, h * unit),
                    CornerRadius(radius * unit))
            }
            fun header(searchFirst: Boolean, showSearch: Boolean = true, showTabs: Boolean = true) {
                val searchY = if (!showTabs || searchFirst) 2f else 8f
                val tabsY = if (!showSearch || !searchFirst) 2f else 8f
                if (showSearch) tile(3f, searchY, 38f, 4f, surface, 2f)
                if (showTabs) repeat(3) { tile(4f + it * 13f, tabsY, 10f, 2f, if (it == 0) accent else muted) }
            }
            drawRect(background)
            when (preview) {
                is SettingsOptionPreview.Feed -> {
                    repeat(2) { row ->
                        val columns = if (preview.columns == 0) 2 + row else preview.columns.coerceIn(1, 6)
                        val width = (38f - (columns - 1) * 2f) / columns
                        repeat(columns) { column ->
                        val x = 3f + column * (width + 2f)
                        val y = 3f + row * 13f
                        if (preview.horizontalCards) {
                            tile(x, y, 15f, 10f, surface)
                            tile(x + 17f, y + 2f, 18f, 2f, accent)
                            tile(x + 17f, y + 6f, 12f, 1f, muted)
                        } else {
                            tile(x, y, width, 8f, surface)
                            tile(x, y + 9f, width * 0.8f, 1.5f, accent)
                            tile(x, y + 11f, width * 0.6f, 1f, muted)
                        }
                    } }
                }
                is SettingsOptionPreview.Labels -> {
                    val railY = if (preview.atTop) 2f else 18f
                    tile(3f, if (preview.atTop) 16f else 2f, 38f, 12f, surface)
                    tile(3f, railY, 38f, 10f, surface, 4f)
                    repeat(3) { index ->
                        val x = 9f + index * 13f
                        val color = if (index == 0) accent else muted
                        if (preview.mode != 2) drawCircle(color, 1.5f * unit, Offset(x * unit, (railY + 4f) * unit))
                        if (preview.mode != 1) tile(x - 3f, railY + (if (preview.mode == 2) 5f else 7f), 6f, 1f, color)
                    }
                }
                is SettingsOptionPreview.HeaderOrder -> {
                    header(preview.searchFirst)
                    repeat(2) { tile(3f, 14f + it * 8f, 38f, 6f, surface) }
                }
                is SettingsOptionPreview.HeaderCollapse -> {
                    header(true, preview.searchVisible, preview.tabsVisible)
                    val start = when {
                        preview.searchVisible && preview.tabsVisible -> 13f
                        preview.searchVisible || preview.tabsVisible -> 8f
                        else -> 3f
                    }
                    repeat(2) { tile(3f, start + it * 8f, 38f, 6f, surface) }
                    // 右侧短箭头表示向下浏览后的位置，不额外播放循环动画。
                    drawLine(accent, Offset(40f * unit, 14f * unit), Offset(40f * unit, 23f * unit), unit)
                    drawLine(accent, Offset(38f * unit, 21f * unit), Offset(40f * unit, 23f * unit), unit)
                }
                is SettingsOptionPreview.Density -> {
                    val rowHeight = 6f * preview.scale
                    repeat(3) { index ->
                        val y = 3f + index * (rowHeight + 2f)
                        tile(3f, y, 38f, rowHeight, surface)
                        tile(5f, y + 1f, rowHeight - 2f, rowHeight - 2f, accent)
                        tile(7f + rowHeight, y + 2f, 20f, 1.2f, muted)
                    }
                }
                is SettingsOptionPreview.Choice -> {
                    tile(3f, 3f, 38f, 4f, surface)
                    tile(30f, 4f, 8f, 2f, accent)
                    tile(if (preview.anchored) 19f else 9f, if (preview.anchored) 8f else 11f,
                        22f, 16f, surface, 2f)
                    repeat(3) { index -> tile(if (preview.anchored) 22f else 12f,
                        (if (preview.anchored) 11f else 14f) + index * 4f, 16f, 1.4f,
                        if (index == 0) accent else muted) }
                }
                is SettingsOptionPreview.UiPreset -> {
                    // 两行条目:MD3 预设为无容器单色图标;Miuix 预设为彩色圆角容器图标。
                    val chipColor = if (preview.miuix) accent.copy(alpha = 0.22f) else muted.copy(alpha = 0.5f)
                    val glyphColor = if (preview.miuix) accent else muted
                    repeat(2) { row ->
                        val y = 4f + row * 13f
                        if (preview.miuix) {
                            tile(3f, y, 9f, 9f, chipColor, 2.4f)
                            tile(5.5f, y + 2.5f, 4f, 4f, glyphColor, 1f)
                        } else {
                            tile(5.5f, y + 2f, 4.5f, 4.5f, glyphColor, 1f)
                        }
                        tile(15f, y + 1f, 22f, 2.2f, if (row == 0) foreground.copy(alpha = 0.6f) else muted)
                        tile(15f, y + 5f, 15f, 1.6f, muted)
                    }
                }
                is SettingsOptionPreview.ThemeStyle -> {
                    // 顶部双列内容卡 + 底栏选中指示,区分 MD3 胶囊与 Miuix 分段底栏。
                    val cardRadius = if (preview.miuix) 2.4f else 1.6f
                    tile(3f, 3f, 18f, 10f, surface, cardRadius)
                    tile(23f, 3f, 18f, 10f, surface, cardRadius)
                    if (preview.miuix) {
                        tile(3f, 16f, 38f, 11f, surface, 5.5f)
                        tile(4.5f, 17.5f, 14f, 8f, accent, 4f)
                        repeat(2) { index ->
                            drawCircle(muted, 1.4f * unit, Offset((27f + index * 6f) * unit, 21.5f * unit))
                        }
                    } else {
                        tile(3f, 17f, 38f, 9f, surface, 4.5f)
                        tile(5f, 18.5f, 12f, 6f, accent.copy(alpha = 0.32f), 3f)
                        drawCircle(accent, 1.5f * unit, Offset(11f * unit, 21.5f * unit))
                        repeat(2) { index ->
                            drawCircle(muted, 1.5f * unit, Offset((24f + index * 7f) * unit, 21.5f * unit))
                        }
                    }
                }
                is SettingsOptionPreview.ListItem -> {
                    // 行小样:0 跟随预设(上行彩色容器 / 下行单色)、1 项目自定义(行容器背景)、2 原生组件。
                    val customRow = preview.mode == 1
                    repeat(2) { row ->
                        val y = 3f + row * 13f
                        if (customRow) tile(3f, y - 1f, 38f, 11f, surface, 2.6f)
                        val colorful = preview.mode == 0 && row == 0 || customRow
                        val chipColor = when {
                            colorful && row == 0 -> accent.copy(alpha = 0.24f)
                            colorful -> tertiary.copy(alpha = 0.26f)
                            else -> muted.copy(alpha = 0.5f)
                        }
                        val glyphColor = if (colorful) (if (row == 0) accent else tertiary) else muted
                        tile(4.5f, y + 0.5f, 8f, 8f, chipColor, 2.2f)
                        tile(6.5f, y + 2.5f, 4f, 4f, glyphColor, 1f)
                        tile(15f, y + 1f, if (row == 0) 22f else 17f, 2.2f, foreground.copy(alpha = if (colorful) 0.62f else 0.45f))
                        tile(15f, y + 5f, 12f, 1.6f, muted)
                    }
                }
                is SettingsOptionPreview.IconChip -> {
                    // 单枚图标芯片的呈现差异:容器有无、颜色饱和度与形状。
                    when (preview.mode) {
                        0 -> {
                            // 跟随预设:左 Miuix 彩色容器、右 MD3 单色,两枚并列。
                            tile(5f, 7f, 14f, 14f, accent.copy(alpha = 0.24f), 3.4f)
                            tile(9.5f, 11.5f, 5f, 5f, accent, 1.2f)
                            tile(25f, 11f, 6f, 6f, muted, 1.4f)
                        }
                        1 -> {
                            tile(13f, 5f, 18f, 18f, primaryContainer, 4f)
                            tile(19.5f, 11.5f, 5f, 5f, onPrimaryContainer, 1.2f)
                        }
                        2 -> {
                            tile(19f, 11.5f, 6f, 6f, muted, 1.4f)
                        }
                        3 -> {
                            tile(13f, 5f, 18f, 18f, tertiary.copy(alpha = 0.3f), 4f)
                            tile(19.5f, 11.5f, 5f, 5f, tertiary, 1.2f)
                        }
                        else -> {
                            tile(13f, 5f, 18f, 18f, accent, 4f)
                            tile(19.5f, 11.5f, 5f, 5f, background, 1.2f)
                        }
                    }
                    tile(8f, 25f, 28f, 1.8f, muted)
                }
                is SettingsOptionPreview.Typography -> tile(3f, 2f, 38f, 26f, surface, 3f)
            }
        }
        if (preview is SettingsOptionPreview.Typography) {
            Text("Aa", color = foreground, fontSize = (12f * preview.scale).sp,
                // 「跟随默认」固定展示基线字重;不能取当前 typography——全局字重覆盖后
                // 会与对应档位渲染成同一粗细,样例失去区分。
                fontWeight = preview.weight ?: FontWeight.Normal,
                maxLines = 1)
        }
    }
}
