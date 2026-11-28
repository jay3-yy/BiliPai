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

/**
 * Created by dss886 on 2019-05-06.
 */
abstract class AbsConfig {

    private val mListenerList = java.util.concurrent.CopyOnWriteArrayList<ConfigChangeListener>()

    /** 由 [DanmakuController] 注入渲染互斥锁，确保所有层的配置变更与后台渲染线程严格互斥。 */
    internal var notificationLock: Any? = null

    /** 在释放 [notificationLock] 后执行的宿主刷新钩子，避免锁内触发 View 调度。 */
    internal var onAfterConfigChanged: ((Int) -> Unit)? = null

    fun addListener(listener: ConfigChangeListener) {
        mListenerList.add(listener)
    }

    @Suppress("unused")
    fun removeListener(listener: ConfigChangeListener) {
        mListenerList.remove(listener)
    }

    internal fun notifyConfigChanged(type: Int) {
        val lock = notificationLock
        if (lock != null) {
            synchronized(lock) {
                mListenerList.forEach {
                    it.onConfigChanged(type)
                }
            }
        } else {
            mListenerList.forEach {
                it.onConfigChanged(type)
            }
        }
        onAfterConfigChanged?.invoke(type)
    }

}
