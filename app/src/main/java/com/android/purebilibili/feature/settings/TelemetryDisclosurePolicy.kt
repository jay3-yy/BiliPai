package com.android.purebilibili.feature.settings

internal data class TelemetryDisclosureSection(
    val title: String,
    val items: List<String>,
)

internal const val TELEMETRY_GLOBAL_SETTING_ID = "telemetry.global_enabled"

internal fun resolveGlobalTelemetryEnabled(
    crashTrackingEnabled: Boolean,
    analyticsEnabled: Boolean,
): Boolean = crashTrackingEnabled || analyticsEnabled

internal fun resolveGlobalTelemetrySubtitle(
    crashTrackingEnabled: Boolean,
    analyticsEnabled: Boolean,
): String = when {
    crashTrackingEnabled && analyticsEnabled ->
        "已开启崩溃报告与匿名使用统计，不包含账号与观看内容；可随时一键全部关闭"
    crashTrackingEnabled ->
        "仅开启崩溃报告，已关闭使用统计；可随时关闭全部遥测"
    analyticsEnabled ->
        "仅开启匿名使用统计，已关闭崩溃报告；可随时关闭全部遥测"
    else ->
        "已关闭全部遥测上报，不会向 Firebase 发送崩溃报告或使用统计"
}

internal val TELEMETRY_DISCLOSURE_SECTIONS: List<TelemetryDisclosureSection> = listOf(
    TelemetryDisclosureSection(
        title = "1. 崩溃与稳定性报告（开启时收集）",
        items = listOf(
            "应用版本号、构建类型、系统语言与进程名称",
            "设备品牌、型号与 Android 系统版本",
            "发生崩溃时的线程名、异常堆栈摘要与最近访问的页面类型",
            "去除查询参数后的接口错误路径，以及 Android 17+ 内存退出摘要",
        ),
    ),
    TelemetryDisclosureSection(
        title = "2. 匿名使用统计（开启时收集）",
        items = listOf(
            "每日匿名活跃心跳（仅含应用版本、构建类型与本地日期）",
            "核心页面浏览类型（如首页、动态、搜索、播放页、下载、听视频、插件中心与设置页）、前后台切换与会话时长",
            "播放、搜索、下载、分享、备份/导入、通知与手柄控制等功能的匿名使用次数与操作类型",
            "少量界面与播放偏好枚举（界面风格、深浅主题、液态玻璃、场景配方、硬件解码、跳过片头片尾、双击点赞）及返回动效耗时区间",
        ),
    ),
    TelemetryDisclosureSection(
        title = "3. 明确不收集的信息",
        items = listOf(
            "不收集你的 B 站账号 UID、Cookie、Token 或任何登录凭据",
            "不收集观看的具体视频 BV 号、视频标题、UP 主名称或直播间信息",
            "不收集搜索关键词、评论内容、弹幕内容或私信内容",
        ),
    ),
    TelemetryDisclosureSection(
        title = "4. 随时可关闭与本地日志边界",
        items = listOf(
            "你可以随时关闭「遥测与数据收集」总开关，或单独关闭某一项",
            "关闭后立即停止向 Firebase 上报，并清空本地未发送的缓冲事件",
            "「问题排查」中的本地日志导出仅在你手动点击时生成，绝不自动上传",
        ),
    ),
)
