<div align="center">

<img src="docs/images/233娘.jpeg" height="96" alt="BiliPai" />

# BiliPai

**原生、纯净、可扩展的第三方 Bilibili Android 客户端**

<sub>面向日常使用的现代 Android 客户端：视频、番剧、直播、动态、下载、插件与大屏体验。</sub>

<p>
  <a href="README.md">简体中文</a> ·
  <a href="README_EN.md">English</a> ·
  <a href="docs/wiki/README_v8.0.6_legacy.md">旧版 README</a>
</p>

<p>
  <img src="https://img.shields.io/badge/Version-9.8.8-007AFF?style=flat-square&labelColor=ffffff" alt="Version 9.8.8" />
  <img src="https://img.shields.io/badge/Android-8.0%2B-34C759?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/License-Non--Commercial-FF3B30?style=flat-square" alt="Non-Commercial License" />
  <img src="https://img.shields.io/github/stars/jay3-yy/BiliPai?style=flat-square&color=FF9500&labelColor=ffffff" alt="Stars" />
  <a href="https://github.com/jay3-yy/BiliPai/releases">
    <img src="https://img.shields.io/github/downloads/jay3-yy/BiliPai/total?style=flat-square&color=34C759&label=%E6%80%BB%E4%B8%8B%E8%BD%BD%E9%87%8F&labelColor=ffffff" alt="总下载量" />
  </a>
  <a href="https://github.com/jay3-yy/BiliPai/releases/tag/v9.8.8">
    <img src="https://img.shields.io/github/downloads/jay3-yy/BiliPai/v9.8.8/total?style=flat-square&color=5AC8FA&label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC%E4%B8%8B%E8%BD%BD%E9%87%8F&labelColor=ffffff" alt="最新版本下载量" />
  </a>
</p>

<p>
  <a href="https://github.com/jay3-yy/BiliPai/releases">
    <img src="https://img.shields.io/badge/Download-Latest_Release-007AFF?style=for-the-badge&labelColor=ffffff" alt="Download latest release" />
  </a>
  <a href="https://t.me/BiliPaii">
    <img src="https://img.shields.io/badge/Telegram-交流群-5AC8FA?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram group" />
  </a>
  <a href="https://t.me/BiliPai">
    <img src="https://img.shields.io/badge/Telegram-频道-5AC8FA?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram channel" />
  </a>
  <a href="https://x.com/YangY_0x00">
    <img src="https://img.shields.io/badge/X-@YangY__0x00-000000?style=for-the-badge&logo=x&logoColor=white" alt="X account" />
  </a>
</p>

<sub>README 更新：2026-07-07 · 当前构建版本以 app/build.gradle.kts 为准 · 发布记录以 <a href="CHANGELOG.md">CHANGELOG.md</a> 为准</sub>

</div>

---

## 项目定位

BiliPai 是一个基于 Kotlin 与 Jetpack Compose 的第三方 Bilibili Android 客户端，覆盖视频、番剧、直播、动态、消息、离线缓存等日常使用流程，并支持插件扩展与平板/折叠屏等大屏形态。

- **日常使用优先**：首页、搜索、视频详情、番剧、直播、动态、消息、个人中心与离线缓存覆盖主流程。
- **播放体验优先**：DASH、高清画质、弹幕、手势、后台播放、画中画、听视频模式与横竖屏策略持续优化。
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
<img src="docs/images/screenshot4.png" height="440" alt="BiliPai screenshot 4" />
<img src="docs/images/screenshot5.png" height="440" alt="BiliPai screenshot 5" />
<img src="docs/images/screenshot6.png" height="440" alt="BiliPai screenshot 6" />
<img src="docs/images/screenshot7.png" height="440" alt="BiliPai screenshot 7" />

</div>

## 下载与安装

| 项目 | 说明 |
| --- | --- |
| 最新版本 | [GitHub Releases](https://github.com/jay3-yy/BiliPai/releases) |
| 系统要求 | Android 8.0+ / API 26+ |
| 推荐系统 | Android 12+，可获得更完整的 Material You 与动态取色体验 |
| CPU 架构 | 以 Release 实际产物为准，优先面向 64 位设备 |
| 登录方式 | 扫码登录 |

安装 APK 时可能需要允许“安装未知来源应用”。如果遇到播放画质、登录状态或缓存问题，请先确认当前版本、网络环境和账号权限。

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
| 内置插件 | 随主应用稳定分发，覆盖空降助手、去广告、弹幕增强、夜间护眼、今日推荐单、CDN 属地优选、初见推荐 | 应用内插件中心 |
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
| UI | Jetpack Compose、Material 3、Miuix、MVVM |
| 网络 | Retrofit、OkHttp、Kotlinx Serialization |
| 存储 | Room、DataStore |
| 媒体 | AndroidX Media3 / ExoPlayer、MediaCodec |
| 弹幕 | DanmakuFlameMaster、DanmakuRenderEngine |
| 视觉 | Haze、AndroidLiquidGlass、Compose Cupertino、Miuix |
| 动画 | Lottie Compose、Orbital、Compose Shimmer |
| 图片 | Coil Compose |
| 后台任务 | WorkManager |

## 项目结构

```text
BiliPai/
├── app/                 # 主应用、功能 UI、播放器、导航、ViewModel、策略与测试
├── settings-core/       # 可复用设置与偏好存储逻辑
├── network-core/        # 网络策略与底层网络支持
├── baselineprofile/     # Macrobenchmark 与 Baseline Profile
├── docs/                # Wiki、开发文档、截图资源
├── plugins/             # 插件 SDK、示例、社区目录
└── scripts/             # 发布、性能和辅助脚本
```

## 构建

```bash
git clone https://github.com/jay3-yy/BiliPai.git
cd BiliPai
./gradlew :app:compileDebugKotlin
```

本地开发建议使用 JDK 21+ 与 Android Studio 2024.1+。如需打包 APK，可运行：

```bash
./gradlew :app:assembleDebug
```

`google-services.json` 是可选项：放入 `app/` 后启用 Firebase Crashlytics / Analytics；缺失时构建脚本会跳过相关能力。

## 文档入口

| 内容 | 链接 |
| --- | --- |
| Wiki 首页 | [docs/wiki/README.md](docs/wiki/README.md) |
| AI / LLM 入口 | [llms.txt](llms.txt) · [docs/wiki/AI.md](docs/wiki/AI.md) |
| 功能矩阵 | [docs/wiki/FEATURE_MATRIX.md](docs/wiki/FEATURE_MATRIX.md) |
| 架构说明 | [docs/wiki/ARCHITECTURE.md](docs/wiki/ARCHITECTURE.md) |
| QA 手册 | [docs/wiki/QA.md](docs/wiki/QA.md) |
| 用户问答 | [docs/wiki/FAQ.md](docs/wiki/FAQ.md) |
| 发布流程 | [docs/wiki/RELEASE_WORKFLOW.md](docs/wiki/RELEASE_WORKFLOW.md) |
| 变更日志 | [CHANGELOG.md](CHANGELOG.md) |
| 旧版 README | [docs/wiki/README_v8.0.6_legacy.md](docs/wiki/README_v8.0.6_legacy.md) |

## 最近更新

当前仓库版本号已更新到 `9.8.8 / versionCode 247`。公开发布说明请以 [CHANGELOG.md](CHANGELOG.md) 为准；最新完整记录为 `v9.8.8`：

- 液态玻璃滑动性能优化：切断底栏无效重组、缓存 RenderEffect、顶部 chrome 折射延迟读取。
- 视频卡片过渡统一为 Compose + backdrop 模糊链路，修复返回落位、共享边界与背景模糊稳定性。
- 视频详情 Tab 指示器与 Pager 实时同步，分段控件增加物理 settle 回弹。

## 路线图

| 状态 | 方向 |
| --- | --- |
| 已完成 | 首页推荐、视频播放、番剧、直播、动态、消息、个人中心、离线缓存、插件系统、大屏适配、视频笔记、听视频模式、液态玻璃视觉、预测返回与共享元素过渡 |
| 进行中 | 滑动与过渡性能持续优化、iOS/Material 3/Miuix 风格统一、插件 SDK 与外部包能力细化、Wiki 文档站与回归清单完善 |
| 计划中 | 观看历史云同步、收藏夹管理、多账户切换、英文/繁体中文体验补全、外部 Dex 插件正式化 |

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
| [DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster) | B 站弹幕渲染能力 |
| [DanmakuRenderEngine](https://github.com/bytedance/DanmakuRenderEngine) | 高性能弹幕渲染参考 |
| [bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) | B 站 API 文档 |
| [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus) | 播放链路与移动端体验参考 |
| [BilibiliSponsorBlock](https://github.com/hanydd/BilibiliSponsorBlock) | 空降助手数据与 API |
| [Haze](https://github.com/chrisbanes/haze) | 毛玻璃效果 |
| [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | 液态玻璃效果 |
| [Compose Cupertino](https://github.com/alexzhirkevich/compose-cupertino) | iOS 风格组件 |
| [Miuix](https://github.com/compose-miuix-ui/miuix) | Miuix 风格组件 |
| [Lottie](https://github.com/airbnb/lottie-android) | 矢量动画 |
| [Coil](https://github.com/coil-kt/coil) | 图片加载 |
| [Orbital](https://github.com/skydoves/Orbital) | 共享元素过渡 |
| [Retrofit](https://github.com/square/retrofit) / [OkHttp](https://github.com/square/okhttp) | 网络请求 |
| [Room](https://developer.android.com/training/data-storage/room) / [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | 本地数据与偏好存储 |

如有遗漏，欢迎通过 Issue 或 PR 补充。

## 免责声明

> [!CAUTION]
>
> 1. 本项目仅供学习交流，严禁用于商业用途。
> 2. 数据来源于 Bilibili 官方公开接口或用户登录后的正常访问能力，版权归对应权利方所有。
> 3. 登录信息仅保存在本地，不会主动上传隐私数据。
> 4. 使用本应用观看、下载或分享内容时，请遵守相关法律法规与平台规则。
> 5. 如涉及版权或权益问题，请联系维护者处理。

## 许可证

[BiliPai 非商业授权协议 1.0](LICENSE)

你可以自由使用、复制、修改、编译和分发本项目或其修改版本；修改后的版本可以闭源分发，无需公开源代码，也无需在作品、文档、界面或发布说明中加入致谢。

本项目及其修改版本不得用于盈利、付费分发、商业服务、广告变现或其他商业目的。如需商业使用，必须事先取得版权持有人的单独书面授权。

## Star History

<div align="center">

[![Star History Chart](https://api.star-history.com/svg?repos=jay3-yy/BiliPai&type=Date)](https://github.com/jay3-yy/BiliPai/stargazers)

</div>

---

<div align="center">

Made by <a href="https://x.com/YangY_0x00">YangY</a>

<sub>( ゜- ゜)つロ 干杯~</sub>

</div>
