<div align="center">

<img src="docs/images/233娘.jpeg" height="96" alt="BiliPai" />

# BiliPai

**原生、纯净、可扩展的第三方 Bilibili Android 客户端**

<sub>面向日常使用的现代 Android 客户端：视频、番剧、直播、动态、下载、插件与大屏体验。</sub>

<p>
  <a href="README.md">简体中文</a> ·
  <a href="README_EN.md">English</a>
</p>

<p>
  <img src="https://img.shields.io/badge/Release-0.2.3--beta.33-007AFF?style=flat-square&labelColor=ffffff" alt="Release 0.2.3-beta.33" />
  <img src="https://img.shields.io/badge/Android-8.0%2B-34C759?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/License-Non--Commercial-FF3B30?style=flat-square" alt="Non-Commercial License" />
  <img src="https://img.shields.io/github/stars/jay3-yy/BiliPai?style=flat-square&color=FF9500&labelColor=ffffff" alt="Stars" />
</p>

<p>
  <a href="https://t.me/bilipai666">
    <img src="https://img.shields.io/badge/Telegram-频道-5AC8FA?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram channel" />
  </a>
  <a href="https://t.me/bilipai888/1">
    <img src="https://img.shields.io/badge/Telegram-交流群-007AFF?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram group" />
  </a>
  <a href="https://x.com/YangY_0x00">
    <img src="https://img.shields.io/badge/X-@YangY__0x00-000000?style=for-the-badge&logo=x&logoColor=white" alt="X account" />
  </a>
</p>

<sub>README 更新：2026-09-11 · 当前构建版本以 app/build.gradle.kts 为准 · 已发布版本以 <a href="CHANGELOG.md">CHANGELOG.md</a> 为准</sub>

</div>

---

## 仓库导航

| 我想要…… | 快速入口 |
| --- | --- |
| 下载与体验 | [下载说明](#下载与安装) · [版本变更](CHANGELOG.md) · [Telegram 频道](https://t.me/bilipai666) · [常见问题](docs/wiki/FAQ.md) |
| 了解项目 | [真机预览](#真机预览) · [核心能力](#核心能力) · [功能矩阵](docs/wiki/FEATURE_MATRIX.md) · [路线图](docs/wiki/ROADMAP.md) |
| 阅读文档 | [Wiki 首页](docs/wiki/README.md) · [架构说明](docs/wiki/ARCHITECTURE.md) · [QA 手册](docs/wiki/QA.md) |
| 开发与构建 | [构建说明](#构建) · [代码结构规范](STRUCTURE_GUIDELINES.adoc) · [版本规范](docs/wiki/VERSIONING.md) · [发布流程](docs/wiki/RELEASE_WORKFLOW.md) |
| 开发插件 | [JSON 插件](docs/PLUGIN_DEVELOPMENT.md) · [原生插件](docs/NATIVE_PLUGIN_DEVELOPMENT.md) · [Plugin SDK](plugins/sdk/README.md) · [示例](plugins/samples/) |
| 参与项目 | [贡献说明](#参与贡献) · [提交 Issue](https://github.com/jay3-yy/BiliPai/issues/new/choose) · [Pull Requests](https://github.com/jay3-yy/BiliPai/pulls) · [AI / LLM 入口](llms.txt) |

## 项目定位

BiliPai 是一个基于 Kotlin 与 Jetpack Compose 的第三方 Bilibili Android 客户端，覆盖视频、番剧、直播、动态、消息、离线缓存等日常使用流程，并支持插件扩展与平板/折叠屏等大屏形态。

- **日常使用优先**：首页、搜索、视频详情、番剧、直播、动态、消息、个人中心与离线缓存覆盖主流程。
- **播放体验优先**：DASH、高清画质、弹幕、手势、后台播放、画中画、听视频模式与横竖屏策略持续优化。
- **倍速集中管理**：在「设置 → 播放与画质 → 播放速度」编辑播放器倍速列表，选择默认播放速度；长按临时加速可用独立的 1.0x–8.0x 滑块、常用倍速快捷点，或点按当前数值直接输入。播放器菜单和双指调速共用列表。
- **原生体验优先**：Material You、Material 3、Miuix、液态玻璃、平板/折叠屏布局与系统媒体控制接入。
- **可扩展优先**：内置插件稳定分发，JSON 规则插件可 URL 导入，源码级插件能力持续推进。
- **隐私克制**：登录信息保存在本地，权限尽量收敛，使用统计仅记录匿名日活与基础使用情况。

> [!IMPORTANT]
> 应用默认设置面向通用场景。安装后建议进入 **设置** 调整外观、动画、播放、弹幕、后台播放和插件选项。

## 真机预览

以下图片来自当前真机截图目录，后续 README 改版也会继续沿用这组资产。

<div align="center">

<img src="docs/images/screenshot1.png" height="440" alt="BiliPai screenshot 1" />
<img src="docs/images/screenshot2.png" height="440" alt="BiliPai screenshot 2" />
<img src="docs/images/screenshot3.png" height="440" alt="BiliPai screenshot 3" />
<img src="docs/images/screenshot4.png" height="440" alt="BiliPai screenshot 4" />
<img src="docs/images/screenshot5.png" height="440" alt="BiliPai screenshot 5" />
<img src="docs/images/screenshot6.png" height="440" alt="BiliPai screenshot 6" />
<img src="docs/images/screenshot7.png" height="440" alt="BiliPai screenshot 7" />
<img src="docs/images/screenshot8.png" height="440" alt="BiliPai screenshot 8" />
<img src="docs/images/screenshot9.png" height="440" alt="BiliPai screenshot 9" />

</div>

## 下载与安装

| 项目 | 说明 |
| --- | --- |
| 源码 | [GitHub](https://github.com/jay3-yy/BiliPai) |
| 公告 / 发布 | [Telegram 频道 @bilipai666](https://t.me/bilipai666) |
| 交流 | [Telegram 群组 @bilipai888](https://t.me/bilipai888/1) |
| 系统要求 | Android 8.0+ / API 26+ |
| 推荐系统 | Android 12+，可获得更完整的 Material You 与动态取色体验 |
| CPU 架构 | 以 Release 实际产物为准，优先面向 64 位设备 |
| 登录方式 | TV 扫码、手机号密码、短信验证码、Cookie 导入 |

安装 APK 时可能需要允许“安装未知来源应用”。请从官方频道或群组获取安装包，并核对发布说明中的版本与校验信息。

## 核心能力

| 模块 | 能力 |
| --- | --- |
| 视频播放 | DASH 自适应码率、4K / 1080P60 / HDR、弹幕、手势、倍速、后台播放、画中画、播放记忆 |
| 视频笔记 | 私有笔记、新建/编辑/删除、AI 总结生成草稿、富文本编辑、时间点、Markdown 中间格式、系统分享 |
| 听视频 | 沉浸式 / 黑胶唱片模式、歌词、播放列表、定时关闭、系统媒体中心联动 |
| 番剧影视 | 选集面板、季度/版本切换、横屏顶部操作、追番与播放进度 |
| 直播 | 分区浏览、HLS 播放、实时弹幕、动态卡片跳转直播间 |
| 动态消息 | 关注流、GIF、图片预览/保存、消息分类、富文本链接跳转 |
| 搜索空间 | 视频 / UP 主 / 番剧检索，UP 空间搜索，历史记录与实时建议 |
| 离线缓存 | 清晰度选择、断点续传、本地播放管理、音视频合并 |
| 插件系统 | 内置插件、JSON 规则插件、源码级原生插件、外部包格式预览 |
| 投屏与备份 | DLNA、Google Cast、WebDAV 设置备份与恢复 |
| 大屏适配 | 平板/折叠屏侧边栏、影院布局、横竖屏方向策略 |

## 体验设计

BiliPai 的界面围绕“内容优先、控制轻量、动效克制”调整。

- **Material You / Android 原生**：支持动态主题色、Material 3 与 Miuix 子风格、排版和 motion 策略。
- **Liquid Glass**：底栏、顶部区域、播放器面板等关键层接入毛玻璃/液态玻璃视觉。
- **iOS 风格底栏**：胶囊指示器、阻尼回弹、模糊背景与大屏侧边栏之间保持统一。
- **播放器覆盖层**：控制栏、弹幕、预览图、手势区域和横屏信息栏分层处理，减少互相遮挡。
- **可调而非强制**：外观、动画、播放器、弹幕、插件和后台行为均尽量提供设置入口。

## 插件生态

| 形态 | 当前状态 | 文档 |
| --- | --- | --- |
| 内置插件 | 随主应用分发，当前注册空降助手、去广告、Anime4K、弹幕增强、夜间护眼、今日推荐单、CDN 属地优选、初见推荐、DLNA 与 Google Cast 共 10 个插件 | 应用内插件中心 |
| JSON / `.bp` 规则插件 | 支持 URL 导入，适合推荐流过滤、弹幕过滤与高亮 | [JSON 插件开发](docs/PLUGIN_DEVELOPMENT.md) |
| 外部 `.bpplugin` 包 | SDK、包格式、manifest、签名校验已就绪；外部 Dex 执行仍处于预览阶段 | [Plugin SDK](plugins/sdk/README.md) |
| 源码级原生插件 | 适合复杂播放器、推荐、弹幕能力，需要重新编译 APK | [原生插件开发](docs/NATIVE_PLUGIN_DEVELOPMENT.md) |

> [!CAUTION]
> 导入第三方插件前请审阅规则和能力声明，尤其是 `NETWORK`、`LOCAL_HISTORY_READ`、`LOCAL_FEEDBACK_READ`、`PLAYER_CONTROL` 等敏感能力。

> 初见推荐致谢原作者 wangdaodao 的 [TabulaBili](https://github.com/wangdaodaodao/TabulaBili) 与 tjsky 的 [TabulaBili-Plus](https://github.com/tjsky/TabulaBili)，BiliPai 仅实现 Android 端内置插件形态。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 | Kotlin |
| 构建基线 | AGP 9.3.1、Gradle 9.5、Kotlin 2.4、JDK 21、compileSdk 37 |
| UI | Jetpack Compose、Material 3、Miuix、Compose Cupertino、MVVM |
| 导航 | Navigation3 runtime/UI 1.2.0-alpha07、NavigationEvent 1.2.0-alpha03 |
| 网络 | Retrofit、OkHttp、Kotlinx Serialization |
| 存储 | Room、DataStore |
| 媒体 | AndroidX Media3 / ExoPlayer、MediaCodec |
| 弹幕 | DanmakuRenderEngine、自研弹幕策略与覆盖层 |
| 视觉 | Haze 2、Miuix Backdrop / Liquid Glass |
| 动画 | Compose Animation / SharedTransition、Lottie、自研 shimmer 与粒子效果 |
| 图片 | Coil Compose |
| 后台任务 | WorkManager |

## 项目结构

```text
BiliPai/
├── app/                         # Android 应用壳、业务功能与绝大多数运行时代码
│   └── src/main/java/com/android/purebilibili/
│       ├── app/                 # Application、启动初始化与顶层装配
│       ├── core/                # 网络、存储、播放器、插件、主题和 UI 公共能力
│       ├── data/                # API/数据库模型与 Repository
│       ├── domain/              # 可复用 UseCase 与纯业务规则
│       ├── feature/             # 视频、首页、动态、直播、设置等业务场景
│       ├── navigation/          # 路由兼容、入口策略与顶层导航装配
│       └── navigation3/         # NavKey、返回栈、Entry/Scene 与预测返回
├── design-system/               # 三套风格共享的主题、组件、动效、模糊与适配策略
├── settings-core/               # 可复用设置策略
├── network-core/                # 可复用网络回退与推荐策略
├── plugin-sdk/                  # 推荐、播放器、弹幕插件接口与能力声明
├── baselineprofile/             # 启动、首页、设置和视频详情性能基准
├── docs/                        # Wiki、插件开发文档与截图资源
├── plugins/                     # SDK 文档、JSON/源码示例、皮肤示例与社区索引
└── scripts/                     # CI、发布、性能采集与 Baseline Profile 工具
```

## 构建

```bash
git clone https://github.com/jay3-yy/BiliPai.git
cd BiliPai
./gradlew :app:compileDebugKotlin
```

本地开发使用 JDK 21；Android Studio、Android SDK 与 Gradle 环境需兼容 AGP 9.3.1 和 compileSdk 37。如需生成可安装的本地测试 APK，可运行：

```bash
./gradlew :app:assembleDev
```

完成后可安装交付包位于 `app/build/outputs/bilipai/dev/BiliPai-0.2.3-beta.33-dev.apk`。正式发布构建对应输出 `app/build/outputs/bilipai/release/BiliPai-0.2.3-beta.33.apk`；AGP 内部的 `app-*.apk` 不作为交付文件。

`google-services.json` 是可选项：放入 `app/` 后启用 Firebase Crashlytics / Analytics；缺失时构建脚本会跳过相关能力。

## 文档入口

| 内容 | 链接 |
| --- | --- |
| Wiki 首页 | [docs/wiki/README.md](docs/wiki/README.md) |
| 当前路线图 | [docs/wiki/ROADMAP.md](docs/wiki/ROADMAP.md) |
| AI / LLM 入口 | [llms.txt](llms.txt) · [docs/wiki/AI.md](docs/wiki/AI.md) |
| 功能矩阵 | [docs/wiki/FEATURE_MATRIX.md](docs/wiki/FEATURE_MATRIX.md) |
| 架构说明 | [docs/wiki/ARCHITECTURE.md](docs/wiki/ARCHITECTURE.md) |
| QA 手册 | [docs/wiki/QA.md](docs/wiki/QA.md) |
| 用户问答 | [docs/wiki/FAQ.md](docs/wiki/FAQ.md) |
| 发布流程 | [docs/wiki/RELEASE_WORKFLOW.md](docs/wiki/RELEASE_WORKFLOW.md) |
| 版本规范 | [docs/wiki/VERSIONING.md](docs/wiki/VERSIONING.md) |
| 变更日志 | [CHANGELOG.md](CHANGELOG.md) |

## 最近更新

当前构建为 `0.2.3-beta.33 / versionCode 357`；最新完整发布记录为 `v0.2.3-beta.33`。源码与变更以 GitHub 和 [CHANGELOG.md](CHANGELOG.md) 为准；安装包与公告见 [Telegram 频道](https://t.me/bilipai666) / [交流群](https://t.me/bilipai888/1)：

- 分段导航消除滑动直角露出，分类与筛选栏升级为自适应滑动液态轨道，长标签文本完整展示不截断。
- 搜索结果页适配平板与折叠屏栅格排版，自适应多列布局并支持用户自选列数。
- 动态详情与增量刷新机制重构，容灾策略提升离线稳定性，增强富文本与评论面板交互。
- 弹幕屏蔽规则支持正则表达式与高级前缀语法，智能保留花括号量词逗号。
- 修复三星 Android 13 闪退与折叠屏外屏横屏启动抖动，卡片预测性返回曲线与圆角更平滑。
- 完整 beta.26 → beta.27 说明见 [CHANGELOG.md](CHANGELOG.md)。

## 路线图

| 状态 | 方向 |
| --- | --- |
| 已完成基线 | 首页、播放、番剧、直播、动态、消息、离线、听视频、视频笔记、投屏、WebDAV、多账号会话、插件、大屏与三套视觉风格 |
| 当前 P0 | 视频整卡/预测返回全入口验收、转场稳态性能、Navigation3 1.2 真机回归、AGP 9 单元测试链路恢复 |
| 后续 | 外部插件可控执行、多账户数据隔离、收藏夹管理、完整本地化与历史云同步评估 |

完整优先级、完成条件与非目标见 [路线图](docs/wiki/ROADMAP.md)。

## 参与贡献

欢迎提交 Issue 和 Pull Request。

1. Fork 本仓库。
2. 从主分支创建 `feature/xxx` 或 `fix/xxx` 分支。
3. 保持改动聚焦，补充必要测试或说明。
4. 提交 PR，并描述改动目的、影响范围和验证结果。

维护者会优先处理可复现的问题、清晰的功能补全、真实设备反馈和带有验证记录的修复。

## 致谢

BiliPai 依赖并参考了多个优秀开源项目：

| 项目 | 用途 |
| --- | --- |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | 声明式 UI 框架 |
| [AndroidX Media](https://github.com/androidx/media) | Media3 / ExoPlayer 播放引擎 |
| [DanmakuRenderEngine](https://github.com/bytedance/DanmakuRenderEngine) | 高性能弹幕渲染参考 |
| [bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) | B 站 API 文档 |
| [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus) | 播放链路与移动端体验参考 |
| [Bili Pilot](https://github.com/siwei-yuan/bili-pilot) | 签名 CDN 候选、分片级选线与预缓存设计参考；BiliPai 为独立 Kotlin 实现，未复制其 JavaScript 代码 |
| [BilibiliSponsorBlock](https://github.com/hanydd/BilibiliSponsorBlock) | 空降助手数据与 API |
| [Haze](https://github.com/chrisbanes/haze) | 毛玻璃效果 |
| [Compose Cupertino](https://github.com/alexzhirkevich/compose-cupertino) | iOS 风格组件 |
| [Miuix](https://github.com/compose-miuix-ui/miuix) | Miuix 风格组件 |
| [BiliPai-miuix](https://github.com/Piracola/BiliPai-miuix) | UI 组件 facade / design-system 重构贡献（@piracola） |
| [Lottie](https://github.com/airbnb/lottie-android) | 矢量动画 |
| [Coil](https://github.com/coil-kt/coil) | 图片加载 |
| [Retrofit](https://github.com/square/retrofit) / [OkHttp](https://github.com/square/okhttp) | 网络请求 |
| [Room](https://developer.android.com/training/data-storage/room) / [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | 本地数据与偏好存储 |

如有遗漏，欢迎通过 Issue 或 PR 补充。

## 免责声明

> [!CAUTION]
>
> 1. 本项目按 GPLv3 开源；使用、修改与分发时请遵守许可证及相关法律法规。
> 2. 数据来源于 Bilibili 官方公开接口或用户登录后的正常访问能力，版权归对应权利方所有。
> 3. 登录信息仅保存在本地，不会主动上传隐私数据。
> 4. 使用本应用观看、下载或分享内容时，请遵守相关法律法规与平台规则。
> 5. 如涉及版权或权益问题，请联系维护者处理。

## 许可证

[GNU General Public License v3.0](LICENSE)

你可以依照 GPLv3 使用、研究、修改与分发本项目。分发修改版本或二进制作品时，须按 GPLv3 提供对应源代码并保留许可证与版权声明。

<div align="center">

Made by <a href="https://x.com/YangY_0x00">YangY</a>

<sub>( ゜- ゜)つロ 干杯~</sub>

</div>
