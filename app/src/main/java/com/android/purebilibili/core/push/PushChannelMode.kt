package com.android.purebilibili.core.push

enum class PushChannelMode(val wireValue: String) {
    DISABLED("disabled"),
    UNIFIED_PUSH("unified_push"),
    FCM_RELAY("fcm_relay");

    companion object {
        fun fromWireValue(raw: String?): PushChannelMode =
            entries.firstOrNull { it.wireValue == raw } ?: DISABLED
    }
}

enum class PushIngressSource(val label: String) {
    UNIFIED_PUSH("UnifiedPush"),
    FCM("FCM"),
    LOCAL_TEST("本地测试"),
}
