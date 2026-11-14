<div align="center">

<img src="docs/images/233娘.jpeg" height="96" alt="BiliPai" />

# BiliPai

**原生、纯净、可扩展的第三方 BiliBili Android 客户端**

[简体中文](README.md) · [English](README_EN.md)

<img src="https://img.shields.io/badge/Source-0.8.0%20%28575%29-007AFF?style=flat-square" alt="Source 0.8.0 (575)" />
<img src="https://img.shields.io/badge/Android-8.0%2B-34C759?style=flat-square" alt="Android 8.0+" />
<img src="https://img.shields.io/badge/License-GPLv3-FF3B30?style=flat-square" alt="GPLv3" />

[下载](#下载与安装) · [Wiki](https://github.com/jay3-yy/BiliPai/wiki) · [Telegram 频道](https://t.me/bilipai666) · [Telegram 交流群](https://t.me/bilipai888/1)

</div>

基于 Kotlin 与 Jetpack Compose 打造，支持视频、番剧、直播、动态、消息与离线缓存，适配手机、平板与折叠屏。

## 主要功能

| 功能 | 说明 |
| --- | --- |
| 视频播放 | DASH、高清画质、弹幕、手势、倍速、播放记忆、后台播放与画中画 |
| 听视频 | 沉浸式与黑胶模式、歌词、队列、定时关闭、系统媒体控制与 Android Auto |
| 桌面小组件 | 当前标题、播放控制、音频续播，以及稍后再看与搜索快捷入口 |
| 字幕与笔记 | 本地字幕导入导出、样式与时间调整；私有笔记、时间点与 AI 总结草稿 |
| 番剧与直播 | 追番、自适应选集、大屏分栏与横屏布局；直播分区与实时弹幕 |
| 动态与消息 | 关注动态、评论回复、图片预览与私信 |
| 搜索与缓存 | 视频、UP 主与番剧搜索；离线下载、断点续传与本地播放 |
| 外观与布局 | Google Material 3、Miuix、可选液态玻璃与弹性动效、动态取色、首页规则网格与大屏布局 |
| 扩展能力 | 插件、DLNA / Google Cast 投屏、WebDAV 设置备份 |

部分画质需要登录或相应账号权限。外观、播放、弹幕与后台行为可在设置中调整。桌面小组件与车载入口见[系统媒体使用说明](docs/wiki/SYSTEM_MEDIA.md)。

## 界面预览

<div align="center">

**液态玻璃设计，贯穿全局**

截图仅展示部分界面下的液态玻璃效果。

<img src="docs/images/screenshot-glass-1.png" height="400" alt="BiliPai 首页" />
<img src="docs/images/screenshot-glass-2.png" height="400" alt="BiliPai 外观设置" />
<img src="docs/images/screenshot-glass-3.png" height="400" alt="BiliPai 视频详情" />
<img src="docs/images/screenshot-glass-4.png" height="400" alt="BiliPai 搜索" />

</div>

界面随版本、主题和设备尺寸有所不同。

截图之外，应用还提供弹幕轻触快捷操作、竖屏上滑全屏与动态环境光、听视频、AI 总结笔记、字幕导入导出、插件扩展、DLNA / Google Cast 投屏与 WebDAV 设置备份等；部分功能与 Android 版本相关。

## 下载与安装

从 [Telegram 频道](https://t.me/bilipai666) 获取安装包与发布说明，交流反馈可加入 [Telegram 群组](https://t.me/bilipai888/1)。

- 系统要求：Android 8.0+
- 登录方式：TV 扫码、密码、短信验证码或 Cookie 导入
- 安装 APK 时需允许对应来源安装应用

## 插件

内置空降助手、去广告、Anime4K、弹幕增强、夜间护眼、推荐与投屏等插件，并提供多种扩展方式：

- JSON / `.bp` 规则：通过 URL 导入，用于内容过滤与弹幕处理等。
- `.bilipai.js` 插件：扩展内容源，支持直播源、影视目录与网页抓取。
- `.bpskin` 皮肤包：导入背景、颜色与部分动效资源，不执行代码。
- `.bpplugin` Kotlin 插件包：目前支持包预览与能力授权，尚不执行外部 Dex。

在插件中心启用“去广告增强”，可在“视频标签屏蔽词”中添加、删除关键词，按视频实际标签过滤首页推荐、搜索结果和详情相关推荐。修改规则后刷新对应列表生效；未配置标签词时不增加标签请求，查询失败或超时时保留未确认命中的视频。

导入第三方插件前，请检查来源与权限声明。开发说明见 [插件开发指南](docs/PLUGIN_DEVELOPMENT.md)、[Kotlin 插件 SDK](plugins/sdk/README.md) 与 [JS 插件示例](examples/plugins/README.md)。

## 最近更新

- 去广告增强支持视频标签屏蔽词；推荐卡片按播放尺寸显示「竖屏」标识；动态页可关闭顶部 UP 主头像栏。
- 平板嵌入与全屏播放器共用横屏弹幕样式；播放器新增独立的加载与缓冲提示。
- 短视频返回首页后重新进入时续接未浏览内容；未真实播放不再写入观看历史，返回详情不再停留在封面。
- 评论翻译识别中文回复前缀与中外文混排，中文为主的评论不再出现翻译入口；楼中楼保持滚动位置，点赞状态不再回退。
- 搜索输入单向同步查询，修复光标、选区与输入法组合文字被异步回写重置。
- 全屏切小窗回首页释放方向锁；稍后再看卡片补齐移除操作，Miuix 悬浮底栏磨砂覆盖两种材质。

完整记录见 [CHANGELOG.md](CHANGELOG.md)。

## 构建

环境：JDK 21、Android SDK 37。

```bash
git clone https://github.com/jay3-yy/BiliPai.git
cd BiliPai
./gradlew :app:assembleDev
```

Miuix 依赖从 GitHub Packages 获取，需在本机 `~/.gradle/gradle.properties` 配置 `gpr.user`、`gpr.key`（或设置 `GITHUB_ACTOR`、`GITHUB_TOKEN`）。APK 输出至 `app/build/outputs/bilipai/dev/`。`app/google-services.json` 为可选配置，缺失时跳过 Firebase。

## 参与贡献

欢迎通过 [Issue](https://github.com/jay3-yy/BiliPai/issues/new/choose) 反馈问题、提出建议，也欢迎提交 [Pull Request](https://github.com/jay3-yy/BiliPai/pulls)，一起完善功能和翻译。

## 致谢

- [Kotlin](https://kotlinlang.org/) — 开发语言与协程
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — 声明式 UI 框架
- [AndroidX Media3](https://github.com/androidx/media) — Media3 / ExoPlayer 播放引擎
- [FFmpeg](https://ffmpeg.org/) — 音频解码
- [Room](https://developer.android.com/training/data-storage/room) / [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) — 本地数据与偏好存储
- [Retrofit](https://github.com/square/retrofit) / [OkHttp](https://github.com/square/okhttp) — 网络请求
- [Coil](https://github.com/coil-kt/coil) — 图片加载
- [Lottie](https://github.com/airbnb/lottie-android) — 矢量动画
- [MaterialKolor](https://github.com/jordond/MaterialKolor) — 动态主题配色
- [Compose Rich Editor](https://github.com/MohamedRejeb/Compose-Rich-Editor) — 富文本编辑
- [DanmakuRenderEngine](https://github.com/bytedance/DanmakuRenderEngine) — 弹幕渲染引擎
- [BiliBili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) — B 站 API 文档
- [BiliBiliSponsorBlock](https://github.com/hanydd/BilibiliSponsorBlock) — 空降助手数据与 API
- [Miuix](https://github.com/compose-miuix-ui/miuix) — 界面组件库
- [Haze](https://github.com/chrisbanes/haze) — 毛玻璃效果
- [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus) — 全局应用交互参考
- [Bili Pilot](https://github.com/siwei-yuan/bili-pilot) — CDN 选线参考
- [金标联盟（ITGSA）SharedViewKit](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo) — 视频卡片系统级转场 SDK 与接入参考
- 小米澎湃 OS 系统动效 — 卡片运动参数参考

其余依赖与来源见 [第三方声明](THIRD_PARTY_NOTICES.md)。

## 许可证与声明

本项目采用 [GPLv3](LICENSE)。分发时请遵守许可证，提供对应源代码并保留版权声明。

本项目为非官方客户端，与 BiliBili 无隶属关系。内容版权归对应权利方所有，使用时请遵守相关法律法规与平台规则。登录信息保存在本地；使用统计可在设置中管理。

<div align="center">

<sub>源码版本 0.8.0（575） · 更新于 2026-11-14 · Made by [YangY](https://x.com/YangY_0x00)</sub>

</div>
