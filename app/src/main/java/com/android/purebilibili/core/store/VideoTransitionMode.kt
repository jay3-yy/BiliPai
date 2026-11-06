package com.android.purebilibili.core.store

/** 系统优先保留应用内承接；自研模式始终在当前导航窗口内完成开合。 */
enum class VideoTransitionMode(val storageValue: String) {
    SYSTEM_PREFERRED("system_preferred"),
    APP("app");

    companion object {
        fun fromStorageValue(value: String?): VideoTransitionMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM_PREFERRED
    }
}
