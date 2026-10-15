package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.util.PinyinUtils

enum class SettingsSearchTarget {
    INTERFACE_THEME,
    HOME_FEED,
    NAVIGATION,
    PLAYBACK_QUALITY,
    FULLSCREEN_GESTURE,
    INTERACTION_COMMENT,
    DATA_BACKUP,
    PRIVACY_PERMISSION,
    DIAGNOSTICS,
    ABOUT_SUPPORT,
    APPEARANCE,
    ANIMATION,
    PLAYBACK,
    BOTTOM_BAR,
    PERMISSION,
    MESSAGE_NOTIFICATION,
    BLOCKED_LIST,
    SETTINGS_SHARE,
    WEBDAV_BACKUP,
    DOWNLOAD_PATH,
    IMAGE_SAVE_PATH,
    CLEAR_CACHE,
    PLUGINS,
    EXPORT_LOGS,
    OPEN_SOURCE_LICENSES,
    OPEN_SOURCE_HOME,
    CHECK_UPDATE,
    VIEW_RELEASE_NOTES,
    REPLAY_ONBOARDING,
    TIPS,
    OPEN_LINKS,
    DONATE,
    TELEGRAM,
    TWITTER,
    DISCLAIMER
}

data class SettingsSearchResult(
    val target: SettingsSearchTarget,
    val title: String,
    val subtitle: String,
    val section: String,
    val focusId: String? = null,
    val settingId: String = target.name.lowercase(),
    val path: String = section,
    val currentValue: String = "",
    val page: SettingsRootCategory? = null,
)

internal data class SettingsSearchEntry(
    val target: SettingsSearchTarget,
    val title: String,
    val subtitle: String,
    val section: String,
    val aliases: List<String>,
    val focusId: String? = null,
    val settingId: String = target.name.lowercase(),
    val page: SettingsRootCategory? = null,
    val path: String? = null,
)

private val SETTINGS_SEARCH_INDEX: List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(
        settingId = "legacy.001",
        target = SettingsSearchTarget.INTERFACE_THEME,
        title = "界面与主题",
        subtitle = "界面风格、颜色、字体和启动画面",
        section = "设置",
        aliases = listOf("界面与主题", "界面", "主题", "ui预设", "md3", "miuix", "字体", "dpi", "动态图标", "应用图标", "开屏", "开屏壁纸")
    ),
    SettingsSearchEntry(
        settingId = "legacy.002",
        target = SettingsSearchTarget.HOME_FEED,
        title = "首页与推荐",
        subtitle = "首页卡片、推荐内容、动态和更新时间表",
        section = "设置",
        aliases = listOf("番剧影视更新时间表", "列表顶栏显示", "首页与推荐", "列表页顶栏折叠", "搜索顶栏", "收藏顶栏", "历史记录顶部栏", "稍后再看顶部栏", "列表顶部栏", "仅回顶显示", "上滑时显示", "首页", "推荐", "推荐流", "首页展示", "首页壁纸", "壁纸效果", "刷新数量", "动态栏位", "动态顶栏", "追番时间表", "影视时间表", "电影时间线", "展示番剧影视时间表", "首页顶栏收起", "动态图片", "动态详情图片", "缩略图", "展开大图", "展开图片", "图文动态", "图片展示")
    ),
    SettingsSearchEntry(
        settingId = "legacy.003",
        target = SettingsSearchTarget.NAVIGATION,
        title = "导航与标签",
        subtitle = "调整底栏、分类标签和侧边栏",
        section = "设置",
        aliases = listOf("导航与标签", "导航", "底栏", "底部栏", "顶部标签", "顶部标签页", "首页搜索框", "全局顶栏显示", "首页顶栏显示", "始终显示", "首页顶栏收起", "顶栏收起", "标签排序", "平板侧边栏", "侧边导航栏", "底栏顺序", "底栏项目", "底栏搜索入口", "搜索入口", "悬浮搜索", "搜索分类", "搜索分类顺序")
    ),
    SettingsSearchEntry(
        settingId = "legacy.004",
        target = SettingsSearchTarget.PLAYBACK_QUALITY,
        title = "播放与画质",
        subtitle = "画质、倍速、字幕和连续播放",
        section = "设置",
        aliases = listOf("播放与画质", "播放", "解码", "画质", "音质", "默认画质", "默认音质", "Hi-Res", "杜比", "最高画质", "自动最高画质", "省流量", "定向流量", "字幕", "倍速", "自动连播", "动态环境光", "环境光", "光晕", "ambient", "ambilight")
    ),
    SettingsSearchEntry(
        settingId = "legacy.005",
        target = SettingsSearchTarget.FULLSCREEN_GESTURE,
        title = "全屏与手势",
        subtitle = "全屏方向、截图、锁定按钮、亮度、音量与进度手势",
        section = "设置",
        aliases = listOf("全屏", "全屏方向", "自动横竖屏", "锁定按钮", "截图", "截图按钮", "应用内截图", "应用内干净截图", "手选区域", "区域截图", "三指截图", "亮度", "音量", "进度手势", "手势")
    ),
    SettingsSearchEntry(
        settingId = "legacy.006",
        target = SettingsSearchTarget.INTERACTION_COMMENT,
        title = "互动与评论",
        subtitle = "评论显示、点赞收藏、视频简介和笔记",
        section = "设置",
        aliases = listOf("互动与评论", "互动", "评论", "楼中楼", "评论楼中楼", "评论检测", "发评反诈", "评论发送检测", "评论装扮", "个性装扮", "视频详情评论数", "评论标签数量", "简介评论数量", "ai总结", "视频总结", "双击点赞", "收藏", "收藏夹", "快速收藏", "点按收藏", "收藏点按", "默认收藏夹", "视频简介", "简介默认展开", "视频笔记", "显示视频笔记", "默认折叠视频笔记", "笔记折叠", "视频标签", "视频标签大小", "标签大小", "标签紧凑", "标签更小", "tag")
    ),
    SettingsSearchEntry(
        settingId = "legacy.007",
        target = SettingsSearchTarget.INTERACTION_COMMENT,
        title = "评论 IP 属地",
        subtitle = "有数据时自动显示，无需开启；B站未返回时无法强制显示",
        section = "设置",
        aliases = listOf("IP属地", "IP归属地", "显示IP", "评论地区", "评论定位"),
        focusId = SettingsSearchFocusIds.PLAYBACK_INTERACTION,
    ),
    SettingsSearchEntry(
        settingId = "legacy.008",
        target = SettingsSearchTarget.INTERACTION_COMMENT,
        title = "完整评论时间",
        subtitle = "显示年月日和时分秒；关闭后显示“多久前”",
        section = "设置",
        aliases = listOf("详细评论时间显示", "评论时间", "详细评论时间", "评论发布时间", "完整时间", "绝对时间"),
        focusId = SettingsSearchFocusIds.PLAYBACK_INTERACTION,
    ),
    SettingsSearchEntry(
        settingId = "legacy.009",
        target = SettingsSearchTarget.DATA_BACKUP,
        title = "存储与备份",
        subtitle = "保存位置、缓存、设置分享和云备份",
        section = "设置",
        aliases = listOf("数据与备份", "数据", "备份", "设置分享", "webdav", "云备份", "下载位置", "下载目录", "清除缓存", "清缓存", "缓存")
    ),
    SettingsSearchEntry(
        settingId = "legacy.010",
        target = SettingsSearchTarget.PRIVACY_PERMISSION,
        title = "隐私与权限",
        subtitle = "历史记录、系统权限和屏蔽名单",
        section = "设置",
        aliases = listOf("隐私与权限", "我的回顾", "回顾", "阅读统计", "观看统计", "搜索框默认词", "搜索框推荐词", "默认搜索词", "一小时前搜索", "搜索提示", "个性化搜索推荐", "搜索推荐词", "推荐词", "搜索联想词", "搜索建议", "联想开关", "隐私", "无痕", "权限", "权限管理", "黑名单", "屏蔽", "拉黑")
    ),
    SettingsSearchEntry(
        settingId = "legacy.011",
        target = SettingsSearchTarget.DIAGNOSTICS,
        title = "问题排查",
        subtitle = "记录和导出信息，帮助排查问题",
        section = "设置",
        aliases = listOf(
            "记录崩溃信息",
            "详细运行日志",
            "记录播放问题",
            "画质切换失败时提示",
            "诊断与开发",
            "诊断",
            "开发",
            "崩溃追踪",
            "崩溃日志",
            "闪退",
            "卡死",
            "无响应",
            "anr",
            "native崩溃",
            "native crash",
            "oom",
            "内存不足",
            "系统杀进程",
            "进程退出原因",
            "使用情况统计",
            "增强诊断日志",
            "详细日志",
            "性能诊断",
            "隐私脱敏",
            "播放器诊断日志",
            "画质降档诊断弹窗",
            "降档弹窗",
            "仅提示一次",
            "仅弹窗一次",
            "导出日志",
            "日志",
        )
    ),
    SettingsSearchEntry(
        settingId = "legacy.012",
        target = SettingsSearchTarget.ABOUT_SUPPORT,
        title = "关于与支持",
        subtitle = "版本更新、使用帮助和官方渠道",
        section = "设置",
        aliases = listOf("SHA-256", "provenance", "安装包来源验证", "版本来源", "关于与支持", "关于", "支持", "版本", "更新", "开源", "发布渠道", "小贴士", "默认打开链接", "telegram", "twitter", "捐赠", "打赏")
    ),
    SettingsSearchEntry(
        settingId = "legacy.013",
        target = SettingsSearchTarget.APPEARANCE,
        title = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
        subtitle = "主题、字体、缩放、开屏与应用图标",
        section = "常规",
        // 泛入口别名：具体子项词（主题色/hex/md3颜色/字体大小/dpi/开屏壁纸等）交由
        // 更具体的子项条目承接，避免泛条目靠堆叠别名压过具体结果。
        aliases = listOf(
            "文字粗细",
            "参与随机的壁纸",
            "首页卡片淡入",
            "外观",
            "主题",
            "图标",
            "模糊",
            "皮肤",
            "玻璃",
            "液态玻璃",
            "毛玻璃",
            "动态取色",
            "自定义颜色",
            "主题色",
            "动态颜色",
            "语言",
            "字体",
            "应用字体",
            "本地字体",
            "导入字体",
            "自定义字体",
            "ttf",
            "otf",
            "界面缩放",
            "开屏",
            "自定义壁纸",
            "相册壁纸",
            "应用图标",
            "md3",
            "material",
            "android",
            "安卓",
            "原生",
        )
    ),
    SettingsSearchEntry(
        settingId = "legacy.014",
        target = SettingsSearchTarget.PLAYBACK,
        title = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        subtitle = "解码、手势、后台播放",
        section = "常规",
        aliases = listOf(
            "记录播放问题",
            "记住上次倍速",
            "画质切换失败时提示",
            "评论检查记录",
            "点竖屏视频直接全屏",
            "打开应用直接刷视频",
            "状态栏背景模糊",
            "浏览评论时缩小视频",
            "底部视频入口",
            "重新打开后继续播放",
            "倍速提示背景深浅",
            "播放",
            "解码",
            "硬件解码",
            "编码",
            "avc",
            "hevc",
            "播放速度",
            "倍速",
            "默认播放速度",
            "倍速列表",
            "长按倍速",
            "长按临时加速",
            "记忆上次播放速度",
            "续播",
            "续播弹窗",
            "刚刚看过",
            "看过视频定位",
            "UP主页定位",
            "自动连播",
            "自动播放下一个",
            "连续播放",
            "列表连续播放",
            "收藏夹连续播放",
            "播放顺序",
            "随机播放",
            "顺序播放",
            "后台播放",
            "后台播放模式",
            "离开播放页后停止",
            "停止播放",
            "音频焦点",
            "听视频",
            "画中画",
            "pip",
            "小窗",
            "自动进入画中画",
            "自动进入全屏",
            "自动退出全屏",
            "全屏",
            "全屏方向",
            "固定全屏比例",
            "横屏适配",
            "平板评论区宽度",
            "评论区宽度",
            "楼中楼",
            "评论楼中楼",
            "评论检测",
            "发评反诈",
            "评论发送检测",
            "评论装扮",
            "个性装扮",
            "评论区个性装扮",
            "图片长按保存",
            "长按保存图片",
            "查看图片保存",
            "图片3D翻页",
            "图片翻页动画",
            "图片平面横滑",
            "播放页隐藏状态栏",
            "隐藏状态栏",
            "状态栏",
            "自动横竖屏",
            "自动旋转",
            "全屏手势反向",
            "锁定按钮",
            "截图按钮",
            "应用内干净截图",
            "应用内截图",
            "三指下滑截图",
            "右上角双指长按",
            "截图触发方式",
            "截图范围",
            "手选区域",
            "区域截图",
            "全屏显示时间",
            "全屏显示电量",
            "互动按钮",
            "观看人数",
            "底部进度条",
            "播放器缩小策略",
            "上滑隐藏播放器",
            "暂停时缩小",
            "暂停评论缩小",
            "缩小后自动暂停",
            "自动暂停",
            "竖屏上滑进入全屏",
            "中部滑动切换全屏",
            "亮度",
            "音量",
            "系统亮度",
            "左右侧滑动",
            "双击点赞",
            "ai总结",
            "ai 总结",
            "视频总结",
            "总结",
            "字幕",
            "自动启用字幕",
            "最高画质",
            "默认画质",
            "无线网络默认画质",
            "流量默认画质",
            "默认音质",
            "Hi-Res",
            "杜比音质",
            "省流量模式",
            "定向流量",
            "b站定向流量",
            "详细统计信息",
            "播放器诊断日志",
            "画质降档诊断弹窗",
            "降档弹窗",
            "高画质不可用弹窗",
            "仅提示一次",
            "仅弹窗一次",
            "点击视频直接播放",
            "视频简介",
            "默认展开视频简介",
            "简介默认展开",
            "视频标签",
            "视频标签大小",
            "标签大小",
            "标签紧凑",
            "标签更小",
            "tag",
            "手势",
        )
    ),
    SettingsSearchEntry(
        settingId = "legacy.015",
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
        subtitle = "底栏、顶部标签、平板侧边栏",
        section = "常规",
        aliases = listOf(
            "导航",
            "导航设置",
            "底栏",
            "标签栏",
            "导航栏",
            "tab",
            "顶部标签",
            "顶部标签页",
            "首页搜索框",
            "首页顶栏收起",
            "顶栏收起",
            "侧边导航栏",
            "侧边栏",
            "平板导航",
            "底部导航",
            "底部栏",
            "底栏顺序",
            "底栏图标",
            "底栏文字",
            "底栏项目",
            "底栏隐藏",
            "底栏显示",
            "悬浮底栏",
        )
    ),
    SettingsSearchEntry(
        settingId = "legacy.016",
        target = SettingsSearchTarget.PERMISSION,
        title = settingsDestinationCopy(SettingsSearchTarget.PERMISSION).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.PERMISSION).summary,
        section = "隐私与安全",
        aliases = listOf("发送通知", "权限", "存储权限", "通知权限", "相册权限", "文件权限", "系统设置权限")
    ),
    SettingsSearchEntry(
        settingId = "legacy.017",
        target = SettingsSearchTarget.MESSAGE_NOTIFICATION,
        title = settingsDestinationCopy(SettingsSearchTarget.MESSAGE_NOTIFICATION).title,
        subtitle = "后台检查私信、互动消息、关注更新与开播提醒",
        section = "隐私与安全",
        aliases = listOf("保持后台运行", "消息通知", "后台通知", "私信通知", "私信", "回复我的", "@我", "收到的赞", "系统通知", "开播提醒", "关注更新", "新消息提醒", "常驻后台", "后台消息", "通知")
    ),
    SettingsSearchEntry(
        settingId = "legacy.018",
        target = SettingsSearchTarget.BLOCKED_LIST,
        title = settingsDestinationCopy(SettingsSearchTarget.BLOCKED_LIST).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.BLOCKED_LIST).summary,
        section = "隐私与安全",
        aliases = listOf("黑名单", "屏蔽", "up", "拉黑", "屏蔽up", "已屏蔽up", "屏蔽用户")
    ),
    SettingsSearchEntry(
        settingId = "legacy.019",
        target = SettingsSearchTarget.SETTINGS_SHARE,
        title = settingsDestinationCopy(SettingsSearchTarget.SETTINGS_SHARE).title,
        subtitle = "把可分享的设置导出给他人，或从文件一键导入",
        section = "数据与存储",
        aliases = listOf("设置分享", "分享设置", "导入", "导出", "json", "配置分享", "设置包", "备份设置", "恢复设置")
    ),
    SettingsSearchEntry(
        settingId = "legacy.020",
        target = SettingsSearchTarget.WEBDAV_BACKUP,
        title = settingsDestinationCopy(SettingsSearchTarget.WEBDAV_BACKUP).title,
        subtitle = "把设置和插件配置备份到自己的云盘并随时恢复",
        section = "数据与存储",
        aliases = listOf("云端文件夹", "每天自动备份", "刷新备份列表", "webdav", "云备份", "备份", "恢复", "自动备份", "测试连接", "远端目录", "服务器", "用户名")
    ),
    SettingsSearchEntry(
        settingId = "legacy.021",
        target = SettingsSearchTarget.DOWNLOAD_PATH,
        title = settingsDestinationCopy(SettingsSearchTarget.DOWNLOAD_PATH).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.DOWNLOAD_PATH).summary,
        section = "数据与存储",
        aliases = listOf("下载", "目录", "路径", "导出目录", "下载目录", "存储位置", "文件夹")
    ),
    SettingsSearchEntry(
        settingId = "legacy.022",
        target = SettingsSearchTarget.IMAGE_SAVE_PATH,
        title = settingsDestinationCopy(SettingsSearchTarget.IMAGE_SAVE_PATH).title,
        subtitle = "选择动态图片、头像和评论图片保存目录",
        section = "数据与存储",
        aliases = listOf("图片保存", "保存目录", "保存位置", "相册", "图片目录", "图片文件夹", "动态图片", "头像保存", "bili")
    ),
    SettingsSearchEntry(
        settingId = "legacy.023",
        target = SettingsSearchTarget.CLEAR_CACHE,
        title = settingsDestinationCopy(SettingsSearchTarget.CLEAR_CACHE).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.CLEAR_CACHE).summary,
        section = "数据与存储",
        aliases = listOf(
            "缓存",
            "清理",
            "释放空间",
            "清缓存",
            "删除缓存",
            "空间清理",
            "自动清理",
            "每周清理",
            "每月清理",
            "缓存容量",
            "缓存上限",
            "容量上限",
            "5gb",
            "自动清理阈值",
        )
    ),
    SettingsSearchEntry(
        settingId = "legacy.024",
        target = SettingsSearchTarget.PLUGINS,
        title = settingsDestinationCopy(SettingsSearchTarget.PLUGINS).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.PLUGINS).summary,
        section = "开发者选项",
        aliases = listOf("插件", "扩展", "json", "脚本", "规则", "屏蔽规则")
    ),
    SettingsSearchEntry(
        settingId = "legacy.025",
        target = SettingsSearchTarget.EXPORT_LOGS,
        title = settingsDestinationCopy(SettingsSearchTarget.EXPORT_LOGS).title,
        subtitle = "导出已脱敏的运行记录，用于反馈和排查问题",
        section = "开发者选项",
        aliases = listOf(
            "日志",
            "log",
            "logs",
            "反馈",
            "诊断",
            "导出log",
            "导出日志",
            "分享日志",
            "播放器日志",
            "崩溃日志",
            "闪退日志",
            "anr日志",
            "进程退出记录",
            "问题反馈",
        )
    ),
    SettingsSearchEntry(
        settingId = "legacy.026",
        target = SettingsSearchTarget.OPEN_SOURCE_LICENSES,
        title = settingsDestinationCopy(SettingsSearchTarget.OPEN_SOURCE_LICENSES).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.OPEN_SOURCE_LICENSES).summary,
        section = "关于",
        aliases = listOf("license", "许可证", "开源协议")
    ),
    SettingsSearchEntry(
        settingId = "legacy.027",
        target = SettingsSearchTarget.OPEN_SOURCE_HOME,
        title = settingsDestinationCopy(SettingsSearchTarget.OPEN_SOURCE_HOME).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.OPEN_SOURCE_HOME).summary,
        section = "关于",
        aliases = listOf("github", "git", "仓库", "源码")
    ),
    SettingsSearchEntry(
        settingId = "legacy.028",
        target = SettingsSearchTarget.CHECK_UPDATE,
        title = settingsDestinationCopy(SettingsSearchTarget.CHECK_UPDATE).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.CHECK_UPDATE).summary,
        section = "关于",
        aliases = listOf("更新渠道", "更新", "升级", "新版本", "检查", "自动检查更新", "版本更新", "检测渠道", "测试版", "正式版", "预发布", "beta", "稳定版")
    ),
    SettingsSearchEntry(
        settingId = "legacy.029",
        target = SettingsSearchTarget.VIEW_RELEASE_NOTES,
        title = settingsDestinationCopy(SettingsSearchTarget.VIEW_RELEASE_NOTES).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.VIEW_RELEASE_NOTES).summary,
        section = "关于",
        aliases = listOf("更新日志", "changelog", "版本说明")
    ),
    SettingsSearchEntry(
        settingId = "legacy.030",
        target = SettingsSearchTarget.REPLAY_ONBOARDING,
        title = settingsDestinationCopy(SettingsSearchTarget.REPLAY_ONBOARDING).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.REPLAY_ONBOARDING).summary,
        section = "关于",
        aliases = listOf("新手引导", "教程", "引导", "使用须知", "用户协议")
    ),
    SettingsSearchEntry(
        settingId = "legacy.031",
        target = SettingsSearchTarget.TIPS,
        title = settingsDestinationCopy(SettingsSearchTarget.TIPS).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.TIPS).summary,
        section = "帮助与工具",
        aliases = listOf("贴士", "技巧", "帮助", "隐藏操作", "摸鱼模式", "空降助手", "自动连播", "自动横竖屏")
    ),
    SettingsSearchEntry(
        settingId = "legacy.032",
        target = SettingsSearchTarget.OPEN_LINKS,
        title = settingsDestinationCopy(SettingsSearchTarget.OPEN_LINKS).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.OPEN_LINKS).summary,
        section = "帮助与工具",
        aliases = listOf("链接", "默认打开", "deep link")
    ),
    SettingsSearchEntry(
        settingId = "legacy.033",
        target = SettingsSearchTarget.DONATE,
        title = settingsDestinationCopy(SettingsSearchTarget.DONATE).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.DONATE).summary,
        section = "关注作者",
        aliases = listOf("打赏", "赞助", "支持")
    ),
    SettingsSearchEntry(
        settingId = "legacy.034",
        target = SettingsSearchTarget.TELEGRAM,
        title = settingsDestinationCopy(SettingsSearchTarget.TELEGRAM).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.TELEGRAM).summary,
        section = "关注作者",
        aliases = listOf("telegram", "tg", "频道", "交流群", "bilipai666", "bilipai888")
    ),
    SettingsSearchEntry(
        settingId = "legacy.035",
        target = SettingsSearchTarget.TWITTER,
        title = settingsDestinationCopy(SettingsSearchTarget.TWITTER).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.TWITTER).summary,
        section = "关注作者",
        aliases = listOf("twitter", "x", "推特")
    ),
    SettingsSearchEntry(
        settingId = "legacy.036",
        target = SettingsSearchTarget.DISCLAIMER,
        title = settingsDestinationCopy(SettingsSearchTarget.DISCLAIMER).title,
        subtitle = settingsDestinationCopy(SettingsSearchTarget.DISCLAIMER).summary,
        section = "关于",
        aliases = listOf("声明", "发布渠道", "安全")
    ),
    SettingsSearchEntry(
        settingId = "legacy.037",
        target = SettingsSearchTarget.APPEARANCE,
        title = "自定义主题颜色",
        subtitle = "使用取色器、输入色值或选择预设颜色",
        section = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
        aliases = listOf("自定义md3颜色", "自定义颜色", "md3颜色", "主题色", "hex", "material you"),
        focusId = SettingsSearchFocusIds.APPEARANCE_THEME
    ),
    SettingsSearchEntry(
        settingId = "appearance.dark_theme_schedule",
        target = SettingsSearchTarget.APPEARANCE,
        title = "定时深色模式",
        subtitle = "自选开始和结束时间，支持跨午夜自动切换",
        section = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
        aliases = listOf("自动深色", "夜间模式", "深色时间", "定时夜间", "深色定时"),
        focusId = "appearance.dark_theme_schedule",
    ),
    SettingsSearchEntry(
        settingId = "legacy.038",
        target = SettingsSearchTarget.APPEARANCE,
        title = "界面风格与明暗模式",
        subtitle = "切换界面风格、明暗模式、颜色来源和应用语言",
        section = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
        // 只保留本项专属别名；MD3 颜色/取色类词归「自定义 MD3 颜色」，避免重叠稀释精准度
        aliases = listOf("界面风格", "界面预设 / 主题模式", "界面预设", "主题模式", "深色风格", "应用语言", "语言"),
        focusId = SettingsSearchFocusIds.APPEARANCE_THEME
    ),
    SettingsSearchEntry(
        settingId = "legacy.039",
        target = SettingsSearchTarget.APPEARANCE,
        title = "液态玻璃",
        subtitle = "首页顶栏、搜索框、底栏和评论底栏使用玻璃效果",
        section = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
        aliases = listOf(
            "安卓液态玻璃",
            "安卓原生液态玻璃",
            "全局液态玻璃",
            "评论区液态玻璃",
            "Android Native 液态玻璃",
            "顶部标签栏液态玻璃",
            "顶部 Dock 液态玻璃",
            "顶部dock栏液态玻璃",
            "首页搜索框液态玻璃",
            "底部导航栏液态玻璃",
            "底栏液态玻璃",
        ),
        focusId = SettingsSearchFocusIds.APPEARANCE_THEME
    ),
    SettingsSearchEntry(
        settingId = "legacy.040",
        target = SettingsSearchTarget.HOME_FEED,
        title = "卡片背景模糊",
        subtitle = "独立控制视频卡片信息区的壁纸模糊",
        section = settingsDestinationCopy(SettingsSearchTarget.HOME_FEED).title,
        aliases = listOf("卡片毛玻璃", "卡片模糊", "磨砂卡片", "视频卡片毛玻璃"),
        focusId = SettingsSearchFocusIds.HOME_OVERVIEW,
    ),
    SettingsSearchEntry(
        settingId = "legacy.041",
        target = SettingsSearchTarget.HOME_FEED,
        title = "卡片跟随背景变色",
        subtitle = "独立控制视频卡片跟随壁纸或封面颜色",
        section = settingsDestinationCopy(SettingsSearchTarget.HOME_FEED).title,
        aliases = listOf("卡片动态取色", "卡片取色", "封面取色", "视频卡片动态取色"),
        focusId = SettingsSearchFocusIds.HOME_OVERVIEW,
    ),
    SettingsSearchEntry(
        settingId = "legacy.042",
        target = SettingsSearchTarget.APPEARANCE,
        title = "屏幕刷新率",
        subtitle = "跟随系统自动调节，或手动选择设备支持的显示模式",
        section = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
        aliases = listOf("屏幕帧率", "刷新率", "高刷新率", "高刷", "帧率", "显示模式", "自动帧率"),
        focusId = SettingsSearchFocusIds.APPEARANCE_THEME
    ),
    SettingsSearchEntry(
        settingId = "legacy.043",
        target = SettingsSearchTarget.APPEARANCE,
        title = "字体与显示大小",
        subtitle = "分别调整文字大小、界面缩放和精细显示比例",
        section = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
        aliases = listOf("字体大小", "界面缩放", "dpi", "显示与排版", "应用内dpi", "缩放"),
        focusId = SettingsSearchFocusIds.APPEARANCE_DISPLAY
    ),
    SettingsSearchEntry(
        settingId = "legacy.044",
        target = SettingsSearchTarget.APPEARANCE,
        title = "启动壁纸与动画",
        subtitle = "选择启动壁纸和动画",
        section = settingsDestinationCopy(SettingsSearchTarget.APPEARANCE).title,
        aliases = listOf("启动动画", "开屏壁纸 / 启动画面", "开屏壁纸", "自定义壁纸", "相册壁纸", "启动画面", "随机壁纸", "开屏图标遮罩动画", "图标遮罩动画", "显示开屏图标", "隐藏开屏图标", "开屏图标动画", "启动壁纸"),
        focusId = SettingsSearchFocusIds.APPEARANCE_SPLASH
    ),
    SettingsSearchEntry(
        settingId = "legacy.045",
        target = SettingsSearchTarget.ANIMATION,
        title = "动画、振动与玻璃效果",
        subtitle = "页面动画、玻璃效果与振动反馈",
        section = "动画与触感",
        aliases = listOf(
            "返回时模糊背景",
            "加载占位动画",
            "动画与触感",
            "动画与效果 / 触感反馈 / 文字复制",
            "界面入场动画",
            "打开页面时淡入",
            "进场动画",
            "首页卡片淡入",
            "返回内容跟随进度",
            "返回时渐变为卡片",
            "视频返回跟手姿态",
            "返回时倾斜",
            "视频返回跟手位移",
            "返回时跟手",
            "预见式返回最大进度",
            "返回预览幅度",
            "全屏滑动返回",
            "页面内右滑返回",
            "用当前画面过渡",
            "玻璃形变",
            "文字对比度",
            "页面切换动画",
            "卡片展开动画",
            "首页动画强度",
            "动效与触感",
            "骨架呼吸动画",
            "骨架屏",
            "加载动画",
            "呼吸脉冲",
            "动画与效果",
            "触感反馈",
            "点按文字复制",
            "全局复制",
            "剪贴板",
            "动画设置",
            "页面动画",
            "玻璃效果",
            "返回过渡模糊",
            "Miuix 过渡模糊",
            "miuix模糊",
            "返回动画模糊",
        ),
        focusId = SettingsSearchFocusIds.ANIMATION_VISUAL_EFFECTS
    ),
    SettingsSearchEntry(
        settingId = "legacy.046",
        target = SettingsSearchTarget.HOME_FEED,
        title = "首页与列表",
        subtitle = "展示样式、视频卡片排版、首页壁纸效果、推荐流卡片宽度",
        section = "首页设置",
        aliases = listOf("推荐卡片宽度", "双指调整列数", "番剧影视更新时间表", "首页展示", "首页与列表", "展示样式", "网格列数", "双指缩放", "双指缩放网格列数", "捏合缩放", "缩放列数", "一键回顶", "回到顶部", "搜索回顶", "评论区回顶", "展示番剧影视时间表", "追番时间表", "影视时间表", "电影时间线", "首页壁纸", "首页壁纸效果", "原图壁纸", "壁纸模糊", "强模糊", "推荐流卡片宽度", "首页卡片宽度", "卡片宽度", "完整卡片", "完整标题", "完整内容", "紧凑排版", "统计信息贴封面", "UP主标识", "UP标识", "up主标识", "up标识", "UP主头像", "UP头像", "up主头像", "up头像", "隐藏头像", "发布时间", "上传时间", "隐藏发布时间", "去掉发布时间", "编辑资料", "编辑资料按钮", "隐藏编辑资料", "修改资料"),
        focusId = SettingsSearchFocusIds.HOME_OVERVIEW
    ),
    SettingsSearchEntry(
        settingId = "legacy.047",
        target = SettingsSearchTarget.PLAYBACK,
        title = "视频解码",
        subtitle = "选择优先的视频编码，并设置无法播放时的备用编码",
        section = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        aliases = listOf("硬件解码 / 编码偏好", "硬件解码", "首选编码", "次选编码", "hevc", "avc", "av1", "解码"),
        focusId = SettingsSearchFocusIds.PLAYBACK_DECODER
    ),
    SettingsSearchEntry(
        settingId = "legacy.048",
        target = SettingsSearchTarget.PLAYBACK,
        title = "播放速度",
        subtitle = "编辑播放器倍速列表、默认速度和长按临时加速",
        section = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        aliases = listOf("记住上次倍速", "播放速度", "倍速", "倍速列表", "默认播放速度", "长按倍速", "长按临时加速", "记忆上次播放速度"),
        focusId = SettingsSearchFocusIds.PLAYBACK_SPEED
    ),
    SettingsSearchEntry(
        settingId = "legacy.049",
        target = SettingsSearchTarget.PLAYBACK,
        title = "小窗与后台",
        subtitle = "设置离开播放页后停止、后台继续、进入小窗，以及听视频默认歌词界面",
        section = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        aliases = listOf("点底部视频入口听视频", "听视频时自动隐藏标题", "重新打开后继续播放", "底部视频入口", "后台播放 / 画中画 / 小窗 / 歌词界面", "后台播放", "画中画", "pip", "小窗", "小窗画中画", "音频焦点", "自动进入画中画", "离开播放页后停止", "视频小横条", "听视频小横条", "听视频横条", "当前视频条", "点击小横条", "小横条跳转详情", "小横条跳转听视频", "now playing", "歌词界面", "听视频歌词", "沉浸歌词", "沉浸式歌词", "经典歌词", "逐字歌词", "逐字", "halcyon", "歌词样式"),
        focusId = SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER
    ),
    SettingsSearchEntry(
        settingId = "legacy.050",
        target = SettingsSearchTarget.PLAYBACK,
        title = "手势灵敏度",
        subtitle = "调整进度、音量和亮度手势的响应速度",
        section = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        aliases = listOf("手势灵敏度", "手势控制", "灵敏度"),
        focusId = SettingsSearchFocusIds.PLAYBACK_GESTURE
    ),
    SettingsSearchEntry(
        settingId = "legacy.051",
        target = SettingsSearchTarget.PLAYBACK,
        title = "播放操作与内容显示",
        subtitle = "自动播放、双击操作、字幕、弹幕和笔记",
        section = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        aliases = listOf("双击快进或后退", "浏览评论时收起顶栏", "自动连播 / 跳过片头片尾 / 双击操作 / 弹幕 / 字幕 / 笔记", "自动连播", "自动播放下一个", "进入视频自动播放", "进入视频不要自动播放", "不要自动播放", "自动跳过片头片尾", "跳过片头", "跳过片尾", "跳过op", "跳过ed", "双击点赞", "双击跳转", "取消双击跳转", "关闭双击跳转", "双击快进", "双击后退", "快进秒数", "后退秒数", "关注点赞弹幕", "关注弹幕", "点赞弹幕", "三连弹幕", "弹幕屏蔽", "弹幕同步", "弹幕云同步", "同步弹幕设置", "弹幕设置同步", "网页版弹幕", "字幕", "自动启用字幕", "ai总结", "视频简介", "默认展开视频简介", "简介默认展开", "视频标签", "视频标签大小", "标签大小", "标签紧凑", "标签更小", "tag", "视频笔记", "显示视频笔记", "默认折叠视频笔记", "笔记折叠", "播放器缩小策略", "竖屏视频缩小", "竖屏评论区缩小", "评论上滑缩小播放器", "详情页控件随滚动隐藏", "详情页标签栏隐藏", "评论排序隐藏", "下滑隐藏详情控件", "回顶显示详情控件", "横屏视频缩小", "上滑隐藏播放器", "暂停时缩小", "暂停评论缩小", "缩小后自动暂停", "自动暂停", "相关推荐暂停", "点击视频直接播放"),
        focusId = SettingsSearchFocusIds.PLAYBACK_INTERACTION
    ),
    SettingsSearchEntry(
        settingId = "legacy.052",
        target = SettingsSearchTarget.PLAYBACK,
        title = "全屏与大屏布局",
        subtitle = "设置进入和退出全屏的方式，以及平板播放页布局",
        section = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        aliases = listOf("浏览时缩小视频", "图片立体翻页", "状态栏背景模糊", "用模糊画面填充黑边", "精简播放按钮", "环境光省电模式", "显示已加载回复数", "进度条显示弹幕热度", "自动横竖屏 / 全屏方向 / 平板布局", "自动横竖屏", "自动旋转", "全屏方向", "固定全屏比例", "全屏手势反向", "自动进入全屏", "自动退出全屏", "横屏适配", "平板评论区宽度", "评论区宽度", "评论折叠数量", "评论回复预览", "评论预览数量", "楼中楼", "评论楼中楼", "楼中楼已加载数量", "已加载条数", "评论检测", "发评反诈", "评论发送检测", "评论装扮", "个性装扮", "评论区个性装扮", "图片长按保存", "长按保存图片", "查看图片保存", "播放页隐藏状态栏", "隐藏状态栏", "状态栏", "进度条峰值弹幕", "峰值弹幕", "弹幕热度曲线", "紧凑播放器控件", "紧凑布局", "紧凑控件", "隐藏分享", "隐藏顶栏分享", "顶栏分享", "播放器间距", "控件间距", "播放器控件布局"),
        focusId = SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
    ),
    SettingsSearchEntry(
        settingId = "legacy.053",
        target = SettingsSearchTarget.PLAYBACK,
        title = "网络与画质",
        subtitle = "自动最高画质、默认画质、默认音质、定向流量",
        section = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        aliases = listOf("CDN", "播放线路", "网络与画质", "自动最高画质", "默认画质", "无线网络默认画质", "流量默认画质", "默认音质", "音质", "Hi-Res", "杜比音质", "跟随上次选择", "定向流量", "b站定向流量"),
        focusId = SettingsSearchFocusIds.PLAYBACK_NETWORK
    ),
    SettingsSearchEntry(
        settingId = "legacy.054",
        target = SettingsSearchTarget.PLAYBACK,
        title = "省流量模式",
        subtitle = "限制视频画质；可另选降低封面清晰度",
        section = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        aliases = listOf("省流量", "省流量模式", "节省流量"),
        focusId = SettingsSearchFocusIds.PLAYBACK_DATA_SAVER
    ),
    SettingsSearchEntry(
        settingId = "legacy.055",
        target = SettingsSearchTarget.PLAYBACK,
        title = "播放信息与问题记录",
        subtitle = "显示播放状态并记录黑屏、卡顿等问题的排查信息",
        section = settingsDestinationCopy(SettingsSearchTarget.PLAYBACK).title,
        aliases = listOf("记录播放问题", "播放器诊断 / 统计信息", "播放器诊断日志", "详细统计信息", "调试", "日志"),
        focusId = SettingsSearchFocusIds.PLAYBACK_DEBUG
    ),
    SettingsSearchEntry(
        settingId = "legacy.056",
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "底栏与搜索按钮",
        subtitle = "底栏位置、图标动画和搜索按钮",
        section = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
        aliases = listOf(
            "切换时放大图标",
            "底栏搜索按钮",
            "用底栏搜索当前列表",
            "悬浮底栏 / 搜索联动",
            "悬浮底栏",
            "底栏搜索",
            "底栏搜索联动",
            "搜索入口",
            "悬浮搜索",
            "导航图标交叉缩放",
            "图标放大缩小",
            "选中图标 1.10 倍",
            "视频小横条联动",
            "底栏收拢",
            "列表精简搜索",
            "精简搜索",
            "隐藏列表搜索栏",
            "隐藏搜索栏",
            "收藏搜索",
            "历史搜索",
            "稍后看搜索",
            "稍后再看搜索",
            "页内搜索",
        ),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_BEHAVIOR
    ),
    SettingsSearchEntry(
        settingId = "legacy.057",
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "底栏显示与文字",
        subtitle = "底部导航、标签显示",
        section = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
        aliases = listOf(
            "底栏显示模式 / 标签样式",
            "显示模式",
            "标签样式",
            "底栏显示模式",
            "底栏标签样式",
        ),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_DISPLAY
    ),
    SettingsSearchEntry(
        settingId = "legacy.058",
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "顶部标签管理",
        subtitle = "显示、隐藏和排序标签，并设置首页右上角按钮",
        section = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
        aliases = listOf(
            "隐藏首页分类栏",
            "完全隐藏顶部标签",
            "隐藏顶部标签",
            "隐藏标签",
            "顶部标签",
            "顶部标签样式",
            "顶部标签管理",
            "标签排序",
            "标签显示",
            "推荐分类",
            "直播标签",
            "首页右上角",
            "首页右上角入口",
            "首页右上角消息",
            "消息入口",
            "设置图标",
            "右上角设置",
            "右上角消息",
        ),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_TOP_TABS
    ),
    SettingsSearchEntry(
        settingId = "legacy.059",
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "搜索分类栏顺序",
        subtitle = "调整搜索结果页顶部分类标签的显示顺序",
        section = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
        aliases = listOf(
            "搜索分类",
            "搜索分类栏",
            "搜索分类顺序",
            "分类顺序",
            "搜索标签顺序",
            "搜索Tab",
            "搜索Tab顺序",
            "UP主分类",
            "搜索结果分类",
        ),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_SEARCH_TABS
    ),
    SettingsSearchEntry(
        settingId = "legacy.060",
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "平板侧边导航栏",
        subtitle = "设置平板上是否使用侧边栏以及是否显示账号切换",
        section = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
        aliases = listOf("平板布局", "平板导航", "侧边导航栏", "侧边栏"),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_TABLET
    ),
    SettingsSearchEntry(
        settingId = "legacy.061",
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "当前底栏预览",
        subtitle = "查看当前显示项目和排列顺序",
        section = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
        aliases = listOf("当前底栏", "底栏预览", "底栏顺序"),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_CURRENT
    ),
    SettingsSearchEntry(
        settingId = "legacy.062",
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "可用底栏项目",
        subtitle = "选择底栏显示哪些项目，并调整顺序",
        section = settingsDestinationCopy(SettingsSearchTarget.BOTTOM_BAR).title,
        aliases = listOf("可用项目", "底栏项目", "显示隐藏项目", "底栏图标", "底栏文字"),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_AVAILABLE
    )
)

internal fun resolveSettingsSearchResults(
    query: String,
    maxResults: Int = 20,
): List<SettingsSearchResult> {
    val normalizedQuery = normalizeSettingsSearchText(query)
    if (normalizedQuery.isBlank() || maxResults <= 0) return emptyList()
    val concreteEntries = settingsItemDirectory.map { item ->
        val legacy = SETTINGS_SEARCH_INDEX.firstOrNull { it.title == item.title }
        SettingsSearchEntry(
            target = item.target,
            title = item.title,
            subtitle = settingSearchDescription(item.settingId).ifBlank { legacy?.subtitle.orEmpty() },
            section = item.page.title,
            aliases = legacy?.aliases.orEmpty() + item.aliases + when (item.settingId) {
                "playback.click_to_play_enabled", "playback.auto_play_enabled",
                "playback.startup_auto_play_enabled", "playback.external_playlist_auto_continue_enabled" -> listOf("自动播放")
                "playback.next_watch_hint_enabled" -> listOf("下一集", "相关推荐", "倒计时", "接下来观看", "片尾提示")
                "playback.image_preview3d_page_enabled" -> listOf("图片立体翻页3D", "3D翻页")
                "playback.comment_fraud_detection_enabled" -> listOf("发评反诈", "评论检测", "评论检查")
                "playback.comment_member_decorations_enabled" -> listOf("评论装扮", "个性装扮")
                "playback.image_preview_long_press_save_enabled" -> listOf("图片长按保存", "长按保存图片")
                "playback.video_info_default_expanded" -> listOf("简介默认展开")
                "playback.sub_reply_loaded_count_enabled" -> listOf("楼中楼已加载数量", "已加载条数")
                "playback.hide_interactive_command_danmaku" -> listOf("关注点赞弹幕", "关注弹幕", "点赞弹幕", "三连弹幕", "三连", "隐藏三连", "三连互动提示", "屏蔽互动提示")
                else -> emptyList()
            },
            focusId = item.sectionKey,
            settingId = item.settingId,
            page = item.page.takeIf { item.openCategory || item.target == SettingsSearchTarget.PLAYBACK || it == SettingsRootCategory.GLASS_ADVANCED },
            path = settingItemPath(item),
        )
    }
    val concreteTitles = concreteEntries.map { it.title }.toSet()
    val assignedTerms = concreteEntries.flatMap { it.aliases + it.title }.map(::normalizeSettingsSearchText).toSet()
    val legacyEntries = SETTINGS_SEARCH_INDEX.filterNot { it.title in concreteTitles }.map { entry ->
        entry.copy(aliases = entry.aliases.filterNot { normalizeSettingsSearchText(it) in assignedTerms })
    }
    return (concreteEntries + legacyEntries)
        .mapNotNull { entry -> scoreSettingsSearchMatch(entry, normalizedQuery)?.let { it to entry } }
        .sortedWith(compareByDescending<Pair<SettingsMatch, SettingsSearchEntry>> { it.first.tier }
            .thenByDescending { it.first.coverage }
            .thenBy { it.second.settingId })
        .distinctBy { it.second.settingId }
        .take(maxResults)
        .map { (_, entry) ->
            val category = entry.page ?: resolveSettingsRootCategoryForSearchTarget(entry.target)
            SettingsSearchResult(
                target = entry.target,
                title = entry.title,
                subtitle = entry.subtitle,
                section = entry.section,
                focusId = entry.focusId ?: resolveSettingsSceneDetailFocus(entry.target)?.focusId,
                settingId = entry.settingId,
                path = entry.path ?: if (entry.page != null) {
                    val owner = when (entry.page) {
                        SettingsRootCategory.VIDEO_DECODER -> SettingsRootCategory.PLAYBACK_QUALITY.title
                        SettingsRootCategory.PLAYER_DIAGNOSTICS -> SettingsRootCategory.SYSTEM_ABOUT.title
                        SettingsRootCategory.GLASS_ADVANCED -> SettingsRootCategory.APPEARANCE_THEME.title
                        else -> entry.page.title
                    }
                    val group = when (entry.focusId) {
                        SettingsSearchFocusIds.PLAYBACK_SPEED -> "播放速度"
                        SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER -> "小窗与后台"
                        SettingsSearchFocusIds.PLAYBACK_NETWORK -> "网络与画质"
                        SettingsSearchFocusIds.PLAYBACK_DATA_SAVER -> "省流量"
                        SettingsSearchFocusIds.PLAYBACK_DECODER -> "视频解码"
                        SettingsSearchFocusIds.PLAYBACK_DEBUG -> "播放器诊断"
                        "animation_glass" -> if (entry.page == SettingsRootCategory.GLASS_ADVANCED) "玻璃高级调节" else null
                        else -> null
                    }
                    listOfNotNull("设置", owner, group).joinToString(" / ")
                } else listOfNotNull("设置", category?.title, entry.section.takeIf { it != category?.title && it != "设置" }).joinToString(" / "),
                page = entry.page,
            )
        }
}

private data class SettingsMatch(val tier: Int, val coverage: Int)

private fun scoreSettingsSearchMatch(entry: SettingsSearchEntry, query: String): SettingsMatch? {
    val title = normalizeSettingsSearchText(entry.title)
    val aliases = entry.aliases.map(::normalizeSettingsSearchText).filter(String::isNotBlank)
    if (title == query) return SettingsMatch(7, query.length)
    if (query in aliases) return SettingsMatch(6, query.length)
    val fullTerms = (aliases + title).filter { it.length >= 2 && query.contains(it) }
    if (fullTerms.isNotEmpty()) return SettingsMatch(5, fullTerms.maxOf(String::length))
    if (title.startsWith(query) || aliases.any { it.startsWith(query) }) return SettingsMatch(4, query.length)
    if (title.contains(query) || aliases.any { it.contains(query) }) return SettingsMatch(3, query.length)
    if (matchesSettingsSearchPinyin(entry.title, query) || entry.aliases.any { matchesSettingsSearchPinyin(it, query) }) {
        return SettingsMatch(2, query.length)
    }
    if (normalizeSettingsSearchText(entry.subtitle).contains(query) ||
        normalizeSettingsSearchText(entry.section).contains(query)) return SettingsMatch(1, query.length)
    return null
}

private fun normalizeSettingsSearchText(value: String): String =
    value.trim().lowercase().replace(Regex("[\\s/&+\\-_:：·()（）]+"), "")

private fun matchesSettingsSearchPinyin(value: String, query: String): Boolean =
    PinyinUtils.matches(text = value.replace(" ", ""), query = query)

/** Show the matching state description instead of every alternate toggle state. */
internal fun resolveSettingsSearchDescriptionSnippet(description: String, query: String): String {
    val normalizedQuery = normalizeSettingsSearchText(query)
    val paragraphs = description.split('；', '\n').filter(String::isNotBlank)
    val matched = paragraphs.firstOrNull {
        normalizedQuery.isNotBlank() && normalizeSettingsSearchText(it).contains(normalizedQuery)
    } ?: paragraphs.firstOrNull().orEmpty()
    if (matched.length <= 140) return matched
    val queryIndex = matched.indexOf(query.trim(), ignoreCase = true).coerceAtLeast(0)
    val start = (queryIndex - 40).coerceAtLeast(0)
    val end = (start + 140).coerceAtMost(matched.length)
    return (if (start > 0) "…" else "") + matched.substring(start, end) + if (end < matched.length) "…" else ""
}
