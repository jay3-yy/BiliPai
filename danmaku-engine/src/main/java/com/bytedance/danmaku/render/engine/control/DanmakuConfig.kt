/*
 * Copyright (C) 2022 ByteDance Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bytedance.danmaku.render.engine.control

import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.utils.Logger

/**
 * Created by dss886 on 2018/11/6.
 * Common configuration of danmakus.
 */
class DanmakuConfig : AbsConfig() {

    companion object {
        const val TYPE_DEBUG_SHOW_LAYOUT_BOUNDS = 1000
        const val TYPE_DEBUG_LOGGER_LEVEL = 1001
        const val TYPE_DEBUG_SHOW_DRAW_TIME_COST = 1001

        const val TYPE_COMMON_ALPHA = 1100
        const val TYPE_COMMON_PLAY_SPEED = 1101
        const val TYPE_COMMON_BUFFER_DISCARD_RULE = 1102
        const val TYPE_COMMON_TOP_CENTER_VISIBLE_CHANGE = 1103
        const val TYPE_COMMON_BOTTOM_CENTER_VISIBLE_CHANGE = 1104
        const val TYPE_COMMON_BUFFER_EXPIRE_CHECK = 1105
        const val TYPE_COMMON_PAUSE_INVALIDATE_WHEN_BLANK = 1106
        const val TYPE_COMMON_BRIGHTNESS = 1107

        const val TYPE_TEXT_SIZE = 1200
        const val TYPE_TEXT_COLOR = 1201
        const val TYPE_TEXT_TYPEFACE = 1202
        const val TYPE_TEXT_STROKE_WIDTH = 1203
        const val TYPE_TEXT_STROKE_COLOR = 1204
        const val TYPE_TEXT_INCLUDE_FONT_PADDING = 1205

        const val TYPE_UNDERLINE_WIDTH = 1300
        const val TYPE_UNDERLINE_COLOR = 1301
        const val TYPE_UNDERLINE_STROKE_WIDTH = 1302
        const val TYPE_UNDERLINE_STROKE_COLOR = 1303
        const val TYPE_UNDERLINE_MARGIN_TOP = 1304

        const val TYPE_SCROLL_MOVE_TIME = 1400
        const val TYPE_SCROLL_LINE_HEIGHT = 1401
        const val TYPE_SCROLL_LINE_COUNT = 1402
        const val TYPE_SCROLL_LINE_MARGIN = 1403
        const val TYPE_SCROLL_MARGIN_TOP = 1404
        const val TYPE_SCROLL_ITEM_MARGIN = 1405
        const val TYPE_SCROLL_BUFFER_SIZE = 1406
        const val TYPE_SCROLL_BUFFER_MAX_TIME = 1407

        const val TYPE_TOP_CENTER_SHOW_TIME_MAX = 1500
        const val TYPE_TOP_CENTER_SHOW_TIME_MIN = 1501
        const val TYPE_TOP_CENTER_LINE_HEIGHT = 1502
        const val TYPE_TOP_CENTER_LINE_COUNT = 1503
        const val TYPE_TOP_CENTER_LINE_MARGIN = 1504
        const val TYPE_TOP_CENTER_MARGIN_TOP = 1505
        const val TYPE_TOP_CENTER_BUFFER_SIZE = 1506
        const val TYPE_TOP_CENTER_BUFFER_MAX_TIME = 1507

        const val TYPE_BOTTOM_CENTER_SHOW_TIME_MAX = 1600
        const val TYPE_BOTTOM_CENTER_SHOW_TIME_MIN = 1601
        const val TYPE_BOTTOM_CENTER_LINE_HEIGHT = 1602
        const val TYPE_BOTTOM_CENTER_LINE_COUNT = 1603
        const val TYPE_BOTTOM_CENTER_LINE_MARGIN = 1604
        const val TYPE_BOTTOM_CENTER_MARGIN_BOTTOM = 1605
        const val TYPE_BOTTOM_CENTER_BUFFER_SIZE = 1606
        const val TYPE_BOTTOM_CENTER_BUFFER_MAX_TIME = 1607

        const val TYPE_MASK_VALUE_CHANGE = 1700
    }

    val debug = DebugConfig(this)

    val common = CommonConfig(this)

    val text = TextConfig(this)

    val underline = UnderlineConfig(this)

    val scroll = ScrollLayerConfig(this)

    val top = TopCenterLayerConfig(this)

    val bottom = BottomCenterLayerConfig(this)

    val mask = MaskConfig(this)

    ////////////////////////////////////////////////////
    //               Config Definition                //
    ////////////////////////////////////////////////////

    /**
     * Configuration for Debug
     */
    class DebugConfig(private val config: AbsConfig) {
        /**
         * Draw bounds of the lines and items in layers,
         * Works like the 'Show Layout Bounds' option in Android Developer Settings.
         */
        var showBounds = false
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_DEBUG_SHOW_LAYOUT_BOUNDS)
            }

        /**
         * Priority constant for the Logger, default is [Log.INFO]
         */
        var logLevel = Logger.logLevel
            set(value) {
                field = value
                Logger.logLevel = value
                config.notifyConfigChanged(TYPE_DEBUG_LOGGER_LEVEL)
            }

        /**
         * Print the time cost of draw() method on the screen.
         */
        var showDrawTimeCost = false
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_DEBUG_SHOW_DRAW_TIME_COST)
            }
    }

    /**
     * Configuration for common usage.
     */
    class CommonConfig(private val config: AbsConfig) {
        /**
         * Overall transparency, value is in the range [0..255]:
         *   1. If common.alpha < 255, it will overwrite all other alphas,
         *      including text.alpha and alpha in any other color ints.
         *   2. This alpha will be applied to "DanmakuView", but not drawItems,
         *      Trigger Route:
         *       -> config.notifyConfigChanged(TYPE_COMMON_ALPHA)
         *       -> DanmakuController.onConfigChanged()
         *       -> mDanmakuView.alpha = config.common.alpha / 255f
         */
        var alpha = 255
            set(value) {
                field = if (value < 0) 0 else if (value > 255) 255 else value
                config.notifyConfigChanged(TYPE_COMMON_ALPHA)
            }

        /**
         * The speed of playback in percent, used for time axis synchronous by DataManager.
         * Default is 100 (means 100%), value must be greater than or equal to zero.
         *
         * Notice: this value only affect the DataManager and its timeline.
         * If you want to speed up the Danmaku scrolling when the playSpeed increased,
         * you need to change the config.scroll.moveTime manually
         */
        var playSpeed = 100
            set(value) {
                field = if (value <= 0) 100 else value
                config.notifyConfigChanged(TYPE_COMMON_PLAY_SPEED)
            }

        /**
         * The discard rule when the buffer size is out of limit.
         * The rule is used in the minBy() function, that is to say the minimal one will be discard first.
         * By default, items in the buffer will be discard by their showAtTime.
         */
        var bufferDiscardRule: ((DanmakuData?) -> Comparable<*>) = { it?.showAtTime ?: 0 }
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_COMMON_BUFFER_DISCARD_RULE)
            }

        /**
         * Custom buffer expire check, default expire time is by bufferMaxTime
         */
        var bufferExpireRule: ((DanmakuData?, Long) -> Boolean)? = null
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_COMMON_BUFFER_EXPIRE_CHECK)
            }

        /**
         * whether to display top center danmaku
         */
        var topVisible = true
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_COMMON_TOP_CENTER_VISIBLE_CHANGE)
            }

        /**
         * whether to display bottom center danmaku
         */
        var bottomVisible = true
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_COMMON_BOTTOM_CENTER_VISIBLE_CHANGE)
            }

        /**
         * Item discard listener
         */
        var discardListener: ((DanmakuData?, Int) -> Unit)? = null

        /**
         * Pause invalidate when there's no danmaku on screen
         */
        var pauseInvalidateWhenBlank = true
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_COMMON_PAUSE_INVALIDATE_WHEN_BLANK)
            }

        /**
         * RGB brightness scale in [0.4f..1f] (1f keeps original colors without scaling).
         * Applied per draw item to avoid attaching a full-screen offscreen RenderEffect on HDR video.
         */
        var brightnessFactor = 1f
            set(value) {
                val normalized = if (value.isFinite()) value.coerceIn(0.4f, 1f) else 1f
                if (field == normalized) return
                field = normalized
                config.notifyConfigChanged(TYPE_COMMON_BRIGHTNESS)
            }
    }

    /**
     * Configuration for text drawing.
     */
    class TextConfig(private val config: AbsConfig) {
        /**
         * Text size of the TextDrawItem.
         * Value in pixel units.
         */
        var size = 48f
            set(value) {
                field = if (value <= 0) 48f else value
                config.notifyConfigChanged(TYPE_TEXT_SIZE)
            }

        /**
         * Text color of the TextDrawItem.
         * Allow alpha values, but with a lower priority than [CommonConfig.alpha]
         */
        var color = Color.WHITE
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_TEXT_COLOR)
            }

        /**
         * Text typeface of the TextDrawItem.
         */
        var typeface: Typeface? = Typeface.DEFAULT
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_TEXT_TYPEFACE)
            }

        /**
         * Stroke width of of the TextDrawItem.
         * Value in pixel units.
         */
        var strokeWidth = 2.75f
            set(value) {
                field = if (value < 0) 2.75f else value
                config.notifyConfigChanged(TYPE_TEXT_STROKE_WIDTH)
            }

        /**
         * Stroke color of of the TextDrawItem.
         * Allow alpha values, but with a lower priority than [CommonConfig.alpha]
         */
        var strokeColor = Color.argb(97, 0, 0, 0)
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_TEXT_STROKE_COLOR)
            }

        /**
         * Set whether the TextDrawItem includes extra top and bottom padding to make
         * room for accents that go above the normal ascent and descent.
         * The default is true.
         * Works like the [android.widget.TextView.setIncludeFontPadding]
         */
        var includeFontPadding = true
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_TEXT_INCLUDE_FONT_PADDING)
            }
    }

    /**
     * Configuration for text underline
     */
    class UnderlineConfig(private val config: AbsConfig) {
        /**
         * Underline width of of the TextDrawItem.
         * Value in pixel units.
         */
        var width = 0f
            set(value) {
                field = if (value < 0) 0f else value
                config.notifyConfigChanged(TYPE_UNDERLINE_WIDTH)
            }

        /**
         * Underline color of of the TextDrawItem.
         * Allow alpha values, but with a lower priority than [CommonConfig.alpha].
         */
        var color = Color.WHITE
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_UNDERLINE_COLOR)
            }

        /**
         * Underline stroke width of the TextDrawItem.
         * Value in pixel units.
         */
        var strokeWidth = 1f
            set(value) {
                field = if (value < 0) 1f else value
                config.notifyConfigChanged(TYPE_UNDERLINE_STROKE_WIDTH)
            }

        /**
         * Underline stroke color of the TextDrawItem.
         * Allow alpha values, but with a lower priority than [CommonConfig.alpha].
         */
        var strokeColor = Color.argb(97, 0, 0, 0)
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_UNDERLINE_STROKE_COLOR)
            }

        /**
         * Margin between underline and text.
         * Value in pixel units.
         */
        var marginTop = 0f
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_UNDERLINE_MARGIN_TOP)
            }
    }

    /**
     * Configuration of the ScrollLayer
     */
    class ScrollLayerConfig(private val config: AbsConfig) {
        /**
         * Time cost of the scrolling item move through the screen.
         * Value in millisecond units.
         *
         * For user experience reasons, speed of item is based on the screen width.
         * That is to say, longer item move faster and shorter item move slower.
         */
        var moveTime = 8000L
            set(value) {
                field = if (value <= 0) 8000L else value
                config.notifyConfigChanged(TYPE_SCROLL_MOVE_TIME)
            }

        /**
         * The height of the scroll line in pixel units.
         * Item will be drawn from the top of the line.
         * Gravity will be supported in the future.
         */
        var lineHeight = 54f
            set(value) {
                field = if (value <= 0) 54f else value
                config.notifyConfigChanged(TYPE_SCROLL_LINE_HEIGHT)
            }

        /**
         * The line count of the scroll layer.
         * Lines will be arranged from top to bottom.
         */
        var lineCount = 4
            set(value) {
                field = if (value < 0) 4 else value
                config.notifyConfigChanged(TYPE_SCROLL_LINE_COUNT)
            }

        /**
         * Margin between lines.
         * Value in pixel units.
         */
        var lineMargin = 18f
            set(value) {
                field = if (value < 0) 18f else value
                config.notifyConfigChanged(TYPE_SCROLL_LINE_MARGIN)
            }

        /**
         * Margin from DanmakuView's top.
         * Value in pixel units.
         */
        var marginTop = 0f
            set(value) {
                field = if (value < 0) 0f else value
                config.notifyConfigChanged(TYPE_SCROLL_MARGIN_TOP)
            }

        /**
         * Minimum margin of items horizontal in the same line.
         * Value in pixel units.
         */
        var itemMargin = 24f
            set(value) {
                field = if (value < 0) 24f else value
                config.notifyConfigChanged(TYPE_SCROLL_ITEM_MARGIN)
            }

        /**
         * The size of buffer when there are no enough space to add item.
         * When the limit size of buffer is reached, [CommonConfig.bufferDiscardRule] will be used to discard the item in the buffer.
         * Large buffer size will have slight negative impact on performance.
         */
        var bufferSize = 8
            set(value) {
                field = if (value < 0) 8 else value
                config.notifyConfigChanged(TYPE_SCROLL_BUFFER_SIZE)
            }

        /**
         * The maximum time the item stays in the buffer.
         * Items that exceed the time will be discarded regardless the [CommonConfig.bufferDiscardRule].
         * Value in milliseconds.
         */
        var bufferMaxTime = 4000L
            set(value) {
                field = if (value <= 0) 4000L else value
                config.notifyConfigChanged(TYPE_SCROLL_BUFFER_MAX_TIME)
            }
    }

    /**
     * Configuration of the TopCenterLayer
     */
    class TopCenterLayerConfig(private val config: AbsConfig) {
        /**
         * The maximal time a item will be shown.
         * The show time of a item may be in range [showTimeMin, showTimeMax].
         */
        var showTimeMax = 4000L
            set(value) {
                field = if (value <= 0) 4000L else value
                config.notifyConfigChanged(TYPE_TOP_CENTER_SHOW_TIME_MAX)
            }

        /**
         * The minimum time a item will be shown.
         * When a new item needs to be displayed,
         * only items whose display time exceeds showTimeMin will be replaced.
         * The show time of a item may be in range [showTimeMin, showTimeMax].
         */
        var showTimeMin = 2000L
            set(value) {
                field = if (value <= 0) 4000L else value
                config.notifyConfigChanged(TYPE_TOP_CENTER_SHOW_TIME_MIN)
            }

        /**
         * If LineHeight is larger than the item inside, item will be drawn at the top of the line.
         * Gravity will be supported in future.
         * Value in pixel units.
         */
        var lineHeight = 54f
            set(value) {
                field = if (value <= 0) 54f else value
                config.notifyConfigChanged(TYPE_TOP_CENTER_LINE_HEIGHT)
            }

        /**
         * The line count of the scroll layer.
         * Lines will be arranged from top to bottom.
         */
        var lineCount = 2
            set(value) {
                field = if (value < 0) 4 else value
                config.notifyConfigChanged(TYPE_TOP_CENTER_LINE_COUNT)
            }

        /**
         * Margin between lines.
         * Value in pixel units.
         */
        var lineMargin = 18f
            set(value) {
                field = if (value < 0) 18f else value
                config.notifyConfigChanged(TYPE_TOP_CENTER_LINE_MARGIN)
            }

        /**
         * Margin from DanmakuView's bottom.
         * Value in pixel units.
         */
        var marginTop = 0f
            set(value) {
                field = if (value < 0) 0f else value
                config.notifyConfigChanged(TYPE_TOP_CENTER_MARGIN_TOP)
            }

        /**
         * The size of buffer when there are no enough space to add item.
         * When the limit size of buffer is reached, [CommonConfig.bufferDiscardRule] will be used to discard the item in the buffer.
         * Large buffer size will have slight negative impact on performance.
         */
        var bufferSize = 4
            set(value) {
                field = if (value < 0) 4 else value
                config.notifyConfigChanged(TYPE_TOP_CENTER_BUFFER_SIZE)
            }

        /**
         * The maximum time the item stays in the buffer.
         * Items that exceed the time will be discarded regardless the [CommonConfig.bufferDiscardRule].
         * Value in milliseconds.
         */
        var bufferMaxTime = 2000L
            set(value) {
                field = if (value <= 0) 2000L else value
                config.notifyConfigChanged(TYPE_TOP_CENTER_BUFFER_MAX_TIME)
            }
    }

    /**
     * Configuration of the BottomCenterLayer
     */
    class BottomCenterLayerConfig(private val config: AbsConfig) {
        /**
         * The maximal time a item will be shown.
         * The show time of a item may be in range [showTimeMin, showTimeMax].
         */
        var showTimeMax = 4000L
            set(value) {
                field = if (value <= 0) 4000L else value
                config.notifyConfigChanged(TYPE_BOTTOM_CENTER_SHOW_TIME_MAX)
            }

        /**
         * The minimum time a item will be shown.
         * When a new item needs to be displayed,
         * only items whose display time exceeds showTimeMin will be replaced.
         * The show time of a item may be in range [showTimeMin, showTimeMax].
         */
        var showTimeMin = 2000L
            set(value) {
                field = if (value <= 0) 4000L else value
                config.notifyConfigChanged(TYPE_BOTTOM_CENTER_SHOW_TIME_MIN)
            }

        /**
         * If LineHeight is larger than the item inside, item will be drawn at the top of the line.
         * Gravity will be supported in future.
         * Value in pixel units.
         */
        var lineHeight = 54f
            set(value) {
                field = if (value <= 0) 54f else value
                config.notifyConfigChanged(TYPE_BOTTOM_CENTER_LINE_HEIGHT)
            }

        /**
         * The line count of the scroll layer.
         * Lines will be arranged from top to bottom.
         */
        var lineCount = 2
            set(value) {
                field = if (value < 0) 4 else value
                config.notifyConfigChanged(TYPE_BOTTOM_CENTER_LINE_COUNT)
            }

        /**
         * Margin between lines.
         * Value in pixel units.
         */
        var lineMargin = 18f
            set(value) {
                field = if (value < 0) 18f else value
                config.notifyConfigChanged(TYPE_BOTTOM_CENTER_LINE_MARGIN)
            }

        /**
         * Margin from DanmakuView's bottom.
         * Value in pixel units.
         */
        var marginBottom = 18f
            set(value) {
                field = if (value < 0) 0f else value
                config.notifyConfigChanged(TYPE_BOTTOM_CENTER_MARGIN_BOTTOM)
            }

        /**
         * The size of buffer when there are no enough space to add item.
         * When the limit size of buffer is reached, [CommonConfig.bufferDiscardRule] will be used to discard the item in the buffer.
         * Large buffer size will have slight negative impact on performance.
         */
        var bufferSize = 4
            set(value) {
                field = if (value < 0) 4 else value
                config.notifyConfigChanged(TYPE_BOTTOM_CENTER_BUFFER_SIZE)
            }

        /**
         * The maximum time the item stays in the buffer.
         * Items that exceed the time will be discarded regardless the [CommonConfig.bufferDiscardRule].
         * Value in milliseconds.
         */
        var bufferMaxTime = 2000L
            set(value) {
                field = if (value <= 0) 2000L else value
                config.notifyConfigChanged(TYPE_BOTTOM_CENTER_BUFFER_MAX_TIME)
            }
    }

    /**
     * Configuration of the Mask.
     */
    class MaskConfig(private val config: AbsConfig) {
        /**
         * Danmaku Mask need to call Paint.setXfermode(Xfermode xfermode) to avoid danmaku cover people.
         * this value need to set enable to use canvas.saveLayer() to make Paint.setXfermode(Xfermode xfermode) work normally.
         */
        var enable: Boolean = false
            set(value) {
                field = value
                config.notifyConfigChanged(TYPE_MASK_VALUE_CHANGE)
            }
    }
}
