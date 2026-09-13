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
    val focusId: String? = null
)

private data class SettingsSearchEntry(
    val target: SettingsSearchTarget,
    val title: String,
    val subtitle: String,
    val section: String,
    val aliases: List<String>,
    val focusId: String? = null
)

private val SETTINGS_SEARCH_INDEX: List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(
        target = SettingsSearchTarget.INTERFACE_THEME,
        title = "界面与主题",
        subtitle = "界面风格、主题颜色、字体、显示大小、应用图标与启动画面",
        section = "设置",
        aliases = listOf("界面", "主题", "ui预设", "md3", "miuix", "字体", "dpi", "动态图标", "应用图标", "开屏", "开屏壁纸")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.HOME_FEED,
        title = "首页与推荐",
        subtitle = "首页展示、推荐流、刷新数量、动态栏位、番剧影视时间表、全局顶栏显示与首页壁纸",
        section = "设置",
        aliases = listOf("首页", "推荐", "推荐流", "首页展示", "首页壁纸", "壁纸效果", "刷新数量", "动态栏位", "动态顶栏", "追番时间表", "影视时间表", "电影时间线", "展示番剧影视时间表", "首页顶栏收起")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.NAVIGATION,
        title = "导航与标签",
        subtitle = "底栏、顶部标签、平板侧边栏与底栏项目顺序",
        section = "设置",
        aliases = listOf("导航", "底栏", "底部栏", "顶部标签", "顶部标签页", "首页搜索框", "全局顶栏显示", "首页顶栏显示", "列表顶部栏", "历史记录顶部栏", "稍后再看顶部栏", "仅回顶显示", "始终显示", "首页顶栏收起", "顶栏收起", "标签排序", "平板侧边栏", "侧边导航栏", "底栏顺序", "底栏项目", "底栏搜索入口", "搜索入口", "悬浮搜索")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK_QUALITY,
        title = "播放与画质",
        subtitle = "视频解码、画质、字幕、倍速、连播与省流量设置",
        section = "设置",
        aliases = listOf("播放", "解码", "画质", "音质", "默认画质", "默认音质", "Hi-Res", "杜比", "最高画质", "自动最高画质", "省流量", "定向流量", "字幕", "倍速", "自动连播")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.FULLSCREEN_GESTURE,
        title = "全屏与手势",
        subtitle = "全屏方向、截图、锁定按钮、亮度、音量与进度手势",
        section = "设置",
        aliases = listOf("全屏", "全屏方向", "自动横竖屏", "锁定按钮", "截图", "截图按钮", "应用内截图", "应用内干净截图", "手选区域", "区域截图", "三指截图", "亮度", "音量", "进度手势", "手势")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.INTERACTION_COMMENT,
        title = "互动与评论",
        subtitle = "评论发送检测、评论装扮、AI 总结、双击点赞、视频简介与笔记",
        section = "设置",
        aliases = listOf("互动", "评论", "楼中楼", "评论楼中楼", "评论检测", "发评反诈", "评论发送检测", "评论装扮", "个性装扮", "ai总结", "视频总结", "双击点赞", "视频简介", "简介默认展开", "视频笔记", "显示视频笔记", "默认折叠视频笔记", "笔记折叠")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.DATA_BACKUP,
        title = "数据与备份",
        subtitle = "设置分享、WebDAV、下载位置与清除缓存",
        section = "设置",
        aliases = listOf("数据", "备份", "设置分享", "webdav", "云备份", "下载位置", "下载目录", "清除缓存", "清缓存", "缓存")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PRIVACY_PERMISSION,
        title = "隐私与权限",
        subtitle = "隐私无痕、权限管理与黑名单",
        section = "设置",
        aliases = listOf("搜索框推荐词", "默认搜索词", "一小时前搜索", "搜索提示", "搜索推荐词", "搜索发现推荐", "推荐词", "搜索联想词", "搜索建议", "联想开关", "隐私", "无痕", "权限", "权限管理", "黑名单", "屏蔽", "拉黑")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.DIAGNOSTICS,
        title = "诊断与开发",
        subtitle = "崩溃追踪、增强诊断日志、播放器诊断与日志导出",
        section = "设置",
        aliases = listOf(
            "诊断", "开发", "崩溃追踪", "崩溃日志", "闪退", "卡死", "无响应", "anr",
            "native崩溃", "native crash", "oom", "内存不足", "系统杀进程", "进程退出原因",
            "使用情况统计", "增强诊断日志", "详细日志", "性能诊断", "隐私脱敏",
            "播放器诊断日志", "画质降档诊断弹窗", "降档弹窗", "仅提示一次", "仅弹窗一次",
            "导出日志", "日志"
        )
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.ABOUT_SUPPORT,
        title = "关于与支持",
        subtitle = "版本、更新、开源、发布渠道、小贴士、默认打开链接、社群与捐赠",
        section = "设置",
        aliases = listOf("关于", "支持", "版本", "更新", "开源", "发布渠道", "小贴士", "默认打开链接", "telegram", "twitter", "捐赠", "打赏")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.APPEARANCE,
        title = "外观设置",
        subtitle = "主题、字体、缩放、开屏与应用图标",
        section = "常规",
        // 泛入口别名：具体子项词（主题色/hex/md3颜色/字体大小/dpi/开屏壁纸等）交由
        // 更具体的子项条目承接，避免泛条目靠堆叠别名压过具体结果。
        aliases = listOf(
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
            "原生"
        )
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "播放设置",
        subtitle = "解码、手势、后台播放",
        section = "常规",
        aliases = listOf(
            "播放",
            "解码",
            "硬件解码",
            "编码",
            "avc",
            "hevc",
            "播放速度",
            "倍速",
            "默认播放速度",
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
            "手势"
        )
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "导航设置",
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
            "悬浮底栏"
        )
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PERMISSION,
        title = "权限管理",
        subtitle = "查看每项系统权限的用途和当前授权状态",
        section = "隐私与安全",
        aliases = listOf("权限", "存储权限", "通知权限", "相册权限", "文件权限", "系统设置权限")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.BLOCKED_LIST,
        title = "黑名单管理",
        subtitle = "管理已屏蔽的 UP 主",
        section = "隐私与安全",
        aliases = listOf("黑名单", "屏蔽", "up", "拉黑", "屏蔽up", "已屏蔽up", "屏蔽用户")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.SETTINGS_SHARE,
        title = "设置分享",
        subtitle = "把可分享的设置导出给他人，或从文件一键导入",
        section = "数据与存储",
        aliases = listOf("设置分享", "分享设置", "导入", "导出", "json", "配置分享", "设置包", "备份设置", "恢复设置")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.WEBDAV_BACKUP,
        title = "WebDAV 云备份",
        subtitle = "把设置和插件配置备份到自己的云盘并随时恢复",
        section = "数据与存储",
        aliases = listOf("webdav", "云备份", "备份", "恢复", "自动备份", "测试连接", "远端目录", "服务器", "用户名")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.DOWNLOAD_PATH,
        title = "下载位置",
        subtitle = "选择视频等下载内容的保存目录",
        section = "数据与存储",
        aliases = listOf("下载", "目录", "路径", "导出目录", "下载目录", "存储位置", "文件夹")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.IMAGE_SAVE_PATH,
        title = "图片保存位置",
        subtitle = "选择动态图片、头像和评论图片保存目录",
        section = "数据与存储",
        aliases = listOf("图片保存", "保存目录", "保存位置", "相册", "图片目录", "图片文件夹", "动态图片", "头像保存", "bili")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.CLEAR_CACHE,
        title = "清除缓存",
        subtitle = "清理应用缓存并设置自动清理周期与容量上限",
        section = "数据与存储",
        aliases = listOf(
            "缓存", "清理", "释放空间", "清缓存", "删除缓存", "空间清理", "自动清理",
            "每周清理", "每月清理", "缓存容量", "缓存上限", "容量上限", "5gb", "自动清理阈值"
        )
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLUGINS,
        title = "插件中心",
        subtitle = "安装、启用和管理扩展功能",
        section = "开发者选项",
        aliases = listOf("插件", "扩展", "json", "脚本", "规则", "屏蔽规则")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.EXPORT_LOGS,
        title = "导出日志",
        subtitle = "导出已脱敏的运行记录，用于反馈和排查问题",
        section = "开发者选项",
        aliases = listOf(
            "日志", "log", "logs", "反馈", "诊断", "导出log", "导出日志", "分享日志",
            "播放器日志", "崩溃日志", "闪退日志", "anr日志", "进程退出记录", "问题反馈"
        )
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.OPEN_SOURCE_LICENSES,
        title = "开源许可证",
        subtitle = "查看应用使用的开源项目及其许可协议",
        section = "关于",
        aliases = listOf("license", "许可证", "开源协议")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.OPEN_SOURCE_HOME,
        title = "开源主页",
        subtitle = "GitHub",
        section = "关于",
        aliases = listOf("github", "git", "仓库", "源码")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.CHECK_UPDATE,
        title = "检查更新",
        subtitle = "立即检查是否有可用的新版本",
        section = "关于",
        aliases = listOf("更新", "升级", "新版本", "检查", "自动检查更新", "版本更新", "检测渠道", "测试版", "正式版", "预发布", "beta", "稳定版")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.VIEW_RELEASE_NOTES,
        title = "查看更新日志",
        subtitle = "查看当前版本和最近版本的功能变化",
        section = "关于",
        aliases = listOf("更新日志", "changelog", "版本说明")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.REPLAY_ONBOARDING,
        title = "重看使用须知",
        subtitle = "开源约定与官方渠道",
        section = "关于",
        aliases = listOf("新手引导", "教程", "引导", "使用须知", "用户协议")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.TIPS,
        title = "小贴士 & 隐藏操作",
        subtitle = "了解不容易发现的快捷操作和进阶功能",
        section = "帮助与系统",
        aliases = listOf("贴士", "技巧", "帮助", "隐藏操作", "摸鱼模式", "空降助手", "自动连播", "自动横竖屏")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.OPEN_LINKS,
        title = "默认打开链接",
        subtitle = "设置应用链接支持",
        section = "帮助与系统",
        aliases = listOf("链接", "默认打开", "deep link")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.DONATE,
        title = "打赏作者",
        subtitle = "自愿支持项目后续持续开发和维护",
        section = "关注作者",
        aliases = listOf("打赏", "赞助", "支持")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.TELEGRAM,
        title = "Telegram 频道 / 交流群",
        subtitle = "@bilipai666 · @bilipai888",
        section = "关注作者",
        aliases = listOf("telegram", "tg", "频道", "交流群", "bilipai666", "bilipai888")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.TWITTER,
        title = "Twitter / X",
        subtitle = "@YangY_0x00",
        section = "关注作者",
        aliases = listOf("twitter", "x", "推特")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.DISCLAIMER,
        title = "发布渠道声明",
        subtitle = "GitHub · Telegram 频道与群组",
        section = "关于",
        aliases = listOf("声明", "发布渠道", "安全")
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.APPEARANCE,
        title = "自定义主题颜色",
        subtitle = "使用取色器、输入色值或选择预设颜色",
        section = "外观设置",
        aliases = listOf("自定义md3颜色", "自定义颜色", "md3颜色", "主题色", "hex", "material you"),
        focusId = SettingsSearchFocusIds.APPEARANCE_THEME
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.APPEARANCE,
        title = "界面预设 / 主题模式",
        subtitle = "切换界面风格、明暗模式、颜色来源和应用语言",
        section = "外观设置",
        // 只保留本项专属别名；MD3 颜色/取色类词归「自定义 MD3 颜色」，避免重叠稀释精准度
        aliases = listOf("界面预设", "主题模式", "深色风格", "应用语言", "语言"),
        focusId = SettingsSearchFocusIds.APPEARANCE_THEME
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.APPEARANCE,
        title = "安卓液态玻璃",
        subtitle = "统一应用到首页顶部标签栏、搜索框、底部导航栏和评论区底栏",
        section = "外观设置",
        aliases = listOf(
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
        target = SettingsSearchTarget.APPEARANCE,
        title = "字体与显示大小",
        subtitle = "分别调整文字大小、界面缩放和精细显示比例",
        section = "外观设置",
        aliases = listOf("字体大小", "界面缩放", "dpi", "显示与排版", "应用内dpi", "缩放"),
        focusId = SettingsSearchFocusIds.APPEARANCE_DISPLAY
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.APPEARANCE,
        title = "开屏壁纸 / 启动画面",
        subtitle = "开屏壁纸、自定义壁纸、随机壁纸、图标遮罩动画",
        section = "外观设置",
        aliases = listOf("开屏壁纸", "自定义壁纸", "相册壁纸", "启动画面", "随机壁纸", "开屏图标遮罩动画", "图标遮罩动画", "显示开屏图标", "隐藏开屏图标", "开屏图标动画", "启动壁纸"),
        focusId = SettingsSearchFocusIds.APPEARANCE_SPLASH
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.ANIMATION,
        title = "动画与效果 / 触感反馈 / 文字复制",
        subtitle = "页面动画、玻璃效果、触感反馈与点按文字复制",
        section = "动画与效果",
        aliases = listOf(
            "骨架呼吸动画", "骨架屏", "加载动画", "呼吸脉冲",
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
        target = SettingsSearchTarget.HOME_FEED,
        title = "首页与列表",
        subtitle = "展示样式、视频卡片排版、首页壁纸效果、推荐流卡片宽度",
        section = "首页设置",
        aliases = listOf("首页展示", "首页与列表", "展示样式", "一键回顶", "回到顶部", "搜索回顶", "评论区回顶", "展示番剧影视时间表", "追番时间表", "影视时间表", "电影时间线", "首页壁纸", "首页壁纸效果", "原图壁纸", "壁纸模糊", "强模糊", "推荐流卡片宽度", "首页卡片宽度", "卡片宽度", "完整卡片", "完整标题", "完整内容", "紧凑排版", "统计信息贴封面", "UP主标识", "UP标识", "up主标识", "up标识", "UP主头像", "UP头像", "up主头像", "up头像", "隐藏头像", "编辑资料", "编辑资料按钮", "隐藏编辑资料", "修改资料"),
        focusId = SettingsSearchFocusIds.HOME_OVERVIEW
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "硬件解码 / 编码偏好",
        subtitle = "选择优先的视频编码，并设置无法播放时的备用编码",
        section = "播放设置",
        aliases = listOf("硬件解码", "首选编码", "次选编码", "hevc", "avc", "av1", "解码"),
        focusId = SettingsSearchFocusIds.PLAYBACK_DECODER
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "播放速度",
        subtitle = "默认播放速度、记忆上次播放速度",
        section = "播放设置",
        aliases = listOf("播放速度", "倍速", "默认播放速度", "记忆上次播放速度"),
        focusId = SettingsSearchFocusIds.PLAYBACK_SPEED
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "后台播放 / 画中画 / 小窗",
        subtitle = "设置离开播放页后停止、后台继续或进入小窗",
        section = "播放设置",
        aliases = listOf("后台播放", "画中画", "pip", "小窗", "小窗画中画", "音频焦点", "自动进入画中画", "离开播放页后停止", "视频小横条", "听视频小横条", "听视频横条", "当前视频条", "点击小横条", "小横条跳转详情", "小横条跳转听视频", "now playing"),
        focusId = SettingsSearchFocusIds.PLAYBACK_MINI_PLAYER
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "手势灵敏度",
        subtitle = "调整进度、音量和亮度手势的响应速度",
        section = "播放设置",
        aliases = listOf("手势灵敏度", "手势控制", "灵敏度"),
        focusId = SettingsSearchFocusIds.PLAYBACK_GESTURE
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "自动连播 / 跳过片头片尾 / 双击操作 / 弹幕 / 字幕 / 笔记",
        subtitle = "管理视频播放中的快捷操作、字幕、弹幕和内容辅助功能",
        section = "播放设置",
        aliases = listOf("自动连播", "自动播放下一个", "进入视频自动播放", "进入视频不要自动播放", "不要自动播放", "自动跳过片头片尾", "跳过片头", "跳过片尾", "跳过op", "跳过ed", "双击点赞", "双击跳转", "取消双击跳转", "关闭双击跳转", "双击快进", "双击后退", "快进秒数", "后退秒数", "关注点赞弹幕", "关注弹幕", "点赞弹幕", "三连弹幕", "弹幕屏蔽", "弹幕同步", "弹幕云同步", "同步弹幕设置", "弹幕设置同步", "网页版弹幕", "字幕", "自动启用字幕", "ai总结", "视频简介", "默认展开视频简介", "简介默认展开", "视频笔记", "显示视频笔记", "默认折叠视频笔记", "笔记折叠", "播放器缩小策略", "竖屏视频缩小", "竖屏评论区缩小", "评论上滑缩小播放器", "详情页控件随滚动隐藏", "详情页标签栏隐藏", "评论排序隐藏", "下滑隐藏详情控件", "回顶显示详情控件", "横屏视频缩小", "上滑隐藏播放器", "暂停时缩小", "暂停评论缩小", "缩小后自动暂停", "自动暂停", "相关推荐暂停", "点击视频直接播放"),
        focusId = SettingsSearchFocusIds.PLAYBACK_INTERACTION
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "自动横竖屏 / 全屏方向 / 平板布局",
        subtitle = "设置进入和退出全屏的方式，以及平板播放页布局",
        section = "播放设置",
        aliases = listOf("自动横竖屏", "自动旋转", "全屏方向", "固定全屏比例", "全屏手势反向", "自动进入全屏", "自动退出全屏", "横屏适配", "平板评论区宽度", "评论区宽度", "评论折叠数量", "评论回复预览", "评论预览数量", "楼中楼", "评论楼中楼", "楼中楼已加载数量", "已加载条数", "评论检测", "发评反诈", "评论发送检测", "评论装扮", "个性装扮", "评论区个性装扮", "图片长按保存", "长按保存图片", "查看图片保存", "播放页隐藏状态栏", "隐藏状态栏", "状态栏", "进度条峰值弹幕", "峰值弹幕", "弹幕热度曲线"),
        focusId = SettingsSearchFocusIds.PLAYBACK_FULLSCREEN
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "网络与画质",
        subtitle = "自动最高画质、默认画质、默认音质、定向流量",
        section = "播放设置",
        aliases = listOf("网络与画质", "自动最高画质", "默认画质", "无线网络默认画质", "流量默认画质", "默认音质", "音质", "Hi-Res", "杜比音质", "跟随上次选择", "定向流量", "b站定向流量"),
        focusId = SettingsSearchFocusIds.PLAYBACK_NETWORK
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "省流量模式",
        subtitle = "降低移动网络下的画质和首页图片清晰度",
        section = "播放设置",
        aliases = listOf("省流量", "省流量模式", "节省流量"),
        focusId = SettingsSearchFocusIds.PLAYBACK_DATA_SAVER
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.PLAYBACK,
        title = "播放器诊断 / 统计信息",
        subtitle = "显示播放状态并记录黑屏、卡顿等问题的排查信息",
        section = "播放设置",
        aliases = listOf("播放器诊断日志", "详细统计信息", "调试", "日志"),
        focusId = SettingsSearchFocusIds.PLAYBACK_DEBUG
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "悬浮底栏 / 搜索联动",
        subtitle = "底栏形态、图标交叉缩放、搜索与视频小横条联动",
        section = "导航设置",
        aliases = listOf(
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
        ),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_BEHAVIOR
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "底栏显示模式 / 标签样式",
        subtitle = "底部导航、标签显示",
        section = "导航设置",
        aliases = listOf(
            "显示模式",
            "标签样式",
            "底栏显示模式",
            "底栏标签样式"
        ),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_DISPLAY
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "顶部标签管理",
        subtitle = "显示、隐藏和排序标签，并设置首页右上角按钮",
        section = "导航设置",
        aliases = listOf(
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
            "右上角消息"
        ),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_TOP_TABS
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "平板侧边导航栏",
        subtitle = "设置平板上是否使用侧边栏以及是否显示账号切换",
        section = "导航设置",
        aliases = listOf("平板布局", "平板导航", "侧边导航栏", "侧边栏"),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_TABLET
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "当前底栏预览",
        subtitle = "查看当前显示项目和排列顺序",
        section = "导航设置",
        aliases = listOf("当前底栏", "底栏预览", "底栏顺序"),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_CURRENT
    ),
    SettingsSearchEntry(
        target = SettingsSearchTarget.BOTTOM_BAR,
        title = "可用底栏项目",
        subtitle = "选择底栏显示哪些项目，并调整顺序",
        section = "导航设置",
        aliases = listOf("可用项目", "底栏项目", "显示隐藏项目", "底栏图标", "底栏文字"),
        focusId = SettingsSearchFocusIds.BOTTOM_BAR_AVAILABLE
    )
)

internal fun resolveSettingsSearchResults(
    query: String,
    maxResults: Int = 8
): List<SettingsSearchResult> {
    val normalizedQuery = normalizeSettingsSearchText(query)
    if (normalizedQuery.isBlank()) return emptyList()
    if (maxResults <= 0) return emptyList()

    return SETTINGS_SEARCH_INDEX
        .mapNotNull { entry ->
            scoreSettingsSearchMatch(entry, normalizedQuery)?.let { score ->
                // 带上别名数量：同分时别名更克制（更少）的条目通常更精准，
                // 避免泛条目靠堆叠别名压过具体子项。
                Triple(score, entry.aliases.size, SettingsSearchResult(
                    target = entry.target,
                    title = entry.title,
                    subtitle = entry.subtitle,
                    section = entry.section,
                    focusId = entry.focusId
                ))
            }
        }
        .sortedWith(
            compareByDescending<Triple<Int, Int, SettingsSearchResult>> { it.first }
                .thenBy { it.second }            // 别名更少者优先（更精准）
                .thenByDescending { it.third.title.length } // 再按标题更具体者优先
                .thenBy { it.third.title }
        )
        .map { it.third }
        // 同一 target 只保留最优一条，避免同一设置页被多个重叠别名重复列出。
        .distinctBy { it.target }
        .take(maxResults)
}

private fun scoreSettingsSearchMatch(entry: SettingsSearchEntry, query: String): Int? {
    val title = normalizeSettingsSearchText(entry.title)
    val subtitle = normalizeSettingsSearchText(entry.subtitle)
    val section = normalizeSettingsSearchText(entry.section)
    val aliases = entry.aliases.map(::normalizeSettingsSearchText)

    if (title.startsWith(query)) return 160
    if (aliases.any { it.startsWith(query) }) return 140
    if (title.contains(query)) return 120
    if (aliases.any { it.contains(query) }) return 100
    // 允许用户直接输入自然句，例如「怎么关闭自动播放」「我想调小字体」。
    // 之前只检查「索引文案是否包含查询词」，这类句子会完全漏掉。
    // 忽略单字别名，避免「我」「开」之类泛词造成大量误命中。
    val containedTerms = buildList {
        if (title.length >= 2 && query.contains(title)) add(title)
        aliases.filterTo(this) { alias -> alias.length >= 2 && query.contains(alias) }
    }
    if (containedTerms.isNotEmpty()) {
        val mostSpecificLength = containedTerms.maxOf(String::length)
        return 92 + mostSpecificLength.coerceAtMost(24)
    }
    if (matchesSettingsSearchPinyin(entry.title, query)) return 90
    if (entry.aliases.any { matchesSettingsSearchPinyin(it, query) }) return 80
    if (subtitle.contains(query)) return 70
    if (section.contains(query)) return 50
    return null
}

private fun normalizeSettingsSearchText(value: String): String {
    return value
        .trim()
        .lowercase()
        .replace(Regex("[\\s/&+\\-_:：·()（）]+"), "")
}

private fun matchesSettingsSearchPinyin(value: String, query: String): Boolean {
    return PinyinUtils.matches(
        text = value.replace(" ", ""),
        query = query
    )
}
