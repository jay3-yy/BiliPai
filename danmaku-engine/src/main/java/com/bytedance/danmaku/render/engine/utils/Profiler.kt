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
package com.bytedance.danmaku.render.engine.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.bytedance.danmaku.render.engine.control.DanmakuConfig
import java.text.DecimalFormat

/**
 * Created by dss886 on 2021/07/08.
 * A performance profiler.
 */
class Profiler(private val config: DanmakuConfig) {

    private val mDebugPaint by lazy(LazyThreadSafetyMode.NONE) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    }
    private val mNsFormat by lazy(LazyThreadSafetyMode.NONE) {
        DecimalFormat("0,000,000 ns")
    }
    private val mMsFormat by lazy(LazyThreadSafetyMode.NONE) {
        DecimalFormat("0.000 ms")
    }

    internal fun isProfilingEnabled(): Boolean =
        config.debug.showDrawTimeCost || Logger.isVerboseLoggable() || Logger.isWarnLoggable()

    internal fun needsStageBreakdown(): Boolean = Logger.isVerboseLoggable()

    fun profilerDrawTimeCost(
        canvas: Canvas,
        t0: Long,
        t1: Long,
        t2: Long,
        t3: Long,
        t4: Long,
    ) {
        if (Logger.isVerboseLoggable()) {
            Logger.v(LOG_TAG_PERFORMANCE, "draw(): " +
                    "all=${mNsFormat.format(t4 - t0)}, " +
                    "query=${mNsFormat.format(t1 - t0)}, " +
                    "add=${mNsFormat.format(t2 - t1)}, " +
                    "typesetting=${mNsFormat.format(t3 - t2)}, " +
                    "draw=${mNsFormat.format(t4 - t3)}")
        }

        val totalInMs = (t4 - t0).toFloat() / 1000_000

        if (Logger.isWarnLoggable() && totalInMs > 8) {
            Logger.w(LOG_TAG_PERFORMANCE, "The time cost of the DanmakuView's draw() method is more than " +
                    "8ms(${mMsFormat.format(totalInMs)}), it may cause performance issues!")
        }

        if (config.debug.showDrawTimeCost) {
            mDebugPaint.color = Color.argb(128, 0, 255, 0)
            mDebugPaint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, 280f, 44f, mDebugPaint)
            mDebugPaint.color = Color.argb(255, 255, 0, 0)
            mDebugPaint.textSize = 32f
            canvas.drawText("draw(): " + mMsFormat.format(totalInMs), 10f, - mDebugPaint.fontMetrics.ascent, mDebugPaint)
        }
    }

    fun profilerDrawTimeCost(canvas: Canvas, vararg time: Long) {
        if (time.size != 5) {
            return
        }
        profilerDrawTimeCost(canvas, time[0], time[1], time[2], time[3], time[4])
    }
}
