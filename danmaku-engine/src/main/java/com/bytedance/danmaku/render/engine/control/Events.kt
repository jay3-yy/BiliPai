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

import androidx.core.util.Pools
import com.bytedance.danmaku.render.engine.data.DanmakuData

/**
 * Created by dss886 on 2019/9/27.
 */
object Events {

    private val mPools = Pools.SynchronizedPool<DanmakuEvent>(20)

    fun obtainEvent(what: Int, data: DanmakuData? = null, param: Any? = null): DanmakuEvent {
        var event = mPools.acquire()
        if (event == null) {
            event = DanmakuEvent(what, data, param)
        } else {
            event.what = what
            event.data = data
            event.param = param
        }
        return event
    }

    fun recycleEvent(event: DanmakuEvent) {
        event.reset()
        try {
            mPools.release(event)
        } catch (ignore: IllegalStateException) {
            //Ignored case: "event" may be already released and "Already in the pool!"
        }
    }

}
