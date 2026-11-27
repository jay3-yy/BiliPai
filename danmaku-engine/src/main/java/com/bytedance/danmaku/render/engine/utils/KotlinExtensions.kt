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

import android.os.Build
import android.view.View
import com.bytedance.danmaku.render.engine.DanmakuRenderHost

/**
 * Created by dss886 on 2019/4/14.
 */

fun View.postInvalidateCompat() {
    if (this is DanmakuRenderHost) {
        requestRenderFrame()
    } else if (Build.VERSION.SDK_INT >= 16) {
        postInvalidateOnAnimation()
    } else {
        postInvalidate()
    }
}

/** A single cancellable wake-up for the next timeline item. */
fun View.postDanmakuFrameAfter(delayMs: Long) {
    if (this is DanmakuRenderHost) requestRenderFrame(delayMs)
    else postDelayed({ postInvalidateCompat() }, delayMs)
}

fun View.cancelDanmakuFrames() {
    (this as? DanmakuRenderHost)?.cancelRenderFrames()
}
