# AI 总结插件（BYOK）

给 BiliPai 的「AI 总结」增加一个**由用户自己的模型生成**的数据源：

- 入口、抽屉、卡片、时间戳跳转全部复用宿主现有实现，**不新增任何 UI 入口**；
- 数据源从「B 站官方 `conclusion/get`」换成「抓字幕 → 分段 → 调模型 → 合并」，任意有字幕的视频都能总结；
- 官方接口退居**无字幕时的兜底**（可在设置里关掉）；
- 不填 API Key 时完全走原逻辑，行为零变化。

## 它解决了什么

BiliPai 原生的 AI 总结直接读 `GET /x/web-interface/view/conclusion/get`，也就是 B 站**已经生成好**的摘要。
好处是零成本，代价是：

- 覆盖率 = 官方灰度覆盖率，大量视频点开是「暂不支持」；
- 内容是 B 站二次压缩过的摘要 + 提纲，信息密度低、没有逐条时间戳。

本插件在同一个抽屉里换成自己的流水线：字幕（`/x/player/wbi/v2`，失败再试旧端点）→ 按字符预算分段 →
并发调模型提炼要点 → 合并成「总览 + 章节 + 术语 + 金句 + 观看建议」，每条要点带自己的时间戳。

## 功能

- 内置 8 个服务商模板：DeepSeek / 硅基流动 / 智谱 GLM / Moonshot Kimi / 阿里云百炼 / OpenAI / Ollama 本地 / 自定义（任意 OpenAI 兼容中转），所有字段可手动覆盖。
- 设置页可 `GET /models` 拉取服务商真实模型列表，避免把模型名写死。
- 兼容层：`response_format=json_object` 被拒（400）时自动去掉重发；推理模型不传 `temperature`；OpenAI o 系列改用 `max_completion_tokens`。
- 三种总结风格（快速总结 / 完整笔记 / 章节大纲）× 三档详略（精简 / 标准 / 详细）：风格决定结构，详略决定信息密度，冲突时以详略为准。
- map-reduce 长视频处理：按字符预算切段、句边界断开、段间重叠、并发限流、单段失败只丢该段。
- **每条要点带自己的时间戳**：优先用模型标注的字幕时间，其次与字幕行做二元组匹配，最后在章节区间内插值；官方兜底路径保留 `part_outline[].timestamp`。
- 输出被截断时不展示半截 JSON：先抢救 `overview`，再要求模型用紧凑格式重试一次，最后用逐段要点兜底。
- 无字幕视频可选 **ASR 兜底**：下载音轨 → Whisper 转写 → 走同一条总结流水线（默认关闭）。
- 缓存：结果缓存键含模型 / 风格 / 详略 / Prompt 版本 / 字幕哈希；ASR 转写单独缓存，换模型不重复下载音频。
- API Key 用 **Android Keystore AES-GCM** 加密存储，日志、诊断、缓存均不含明文。
- 可把结果写成 Markdown 并写入 BiliPai 的视频笔记草稿（可选）。

## 集成方式

本插件以**编译期内置插件**形式接入（插件 SDK 不支持 UI 注入，外部 `.bpplugin` 运行时不执行 Dex），
共 6 处改动：

| 文件 | 改动 |
|---|---|
| `settings.gradle.kts` | `include(":ai-summary-plugin")` |
| `app/build.gradle.kts` | `implementation(project(":ai-summary-plugin"))` |
| `core/plugin/BuiltinPluginIds.kt` | 新增 `AI_SUMMARY_BYOK` |
| `app/PureApplication.kt` | 注入 Context 并注册插件 |
| `feature/plugin/AiSummaryBuiltinPlugin.kt` | 新增：把 `SummaryResult` 适配成宿主的 `AiSummaryResponse` |
| `feature/video/viewmodel/VideoPlaybackViewModel.kt` | `loadAiSummary()` 里数据源二选一 + 进度/失败文案 |

`AiSummaryBuiltinPlugin` 负责两件事：把 `SummaryResult` 转成宿主同构的 `AiSummaryResponse`
（`overview` → `model_result.summary`，`chapters` → `outline`，每条要点用自己的 `startSec` 作为
`part_outline[].timestamp`），以及暴露 `isConfigured()` / `fetchSummary()` 给 ViewModel 调用。

数据源选择逻辑（`loadAiSummary()` 内）：

```kotlin
val result = if (AiSummaryBuiltinPlugin.isConfigured()) {
    AiSummaryBuiltinPlugin.fetchSummary(bvid, cid, upMid, videoTitle) { stage ->
        publishAiSummaryProgress(bvid, stage)          // 真实阶段写进 loading 卡片
    }.getOrElse { VideoRepository.getAiSummary(bvid, cid, upMid) }   // 失败回落官方
} else {
    VideoRepository.getAiSummary(bvid, cid, upMid)     // 没配 Key：完全走原逻辑
}
```

## 配置

插件中心 → AI 总结 → 详情页。最小配置只有三项：

| 字段 | 例子 | 说明 |
|---|---|---|
| 服务商模板 | DeepSeek | 一键填好 baseUrl / 默认模型 / 建议并发 |
| API Base URL | `https://api.deepseek.com/v1` | 写到 `/v1` 或厂商等价前缀，不要带 `/chat/completions` |
| API Key | `sk-…` | 加密存本机，不上传、不进日志 |
| 模型名 | `deepseek-chat` | 模板带出候选，可手动改 |

高级参数：温度、每段字符预算（默认 4500）、重叠字符数（160）、最大并发（2）、单次超时（90s）、
重试次数（2）、总结风格、详略档位、缓存开关、无字幕时是否降级官方总结、是否写入笔记草稿、ASR 相关配置。

## 处理流程

```
点击 AI 总结
  → 未配置 Key？→ 走宿主原有官方逻辑（行为不变）
  → 查本地缓存 ──命中──→ 直接展示
  → GET /x/player/wbi/v2（WBI 签名 + 登录 Cookie）
       └ 失败 → GET /x/player/v2（旧端点兜底）
       └ 仍失败 → ASR（可选）→ 官方 conclusion/get（可关闭）
  → subtitle_url → body[{from,to,content}]
  → SubtitleSegmenter：按字符预算切段、句末断开、重叠
  → MAP：并发调模型，每段返回 {points[], timestamps[]}
  → REDUCE：合并 → {overview, chapters[], terms[], quotes[], verdict}
  → PointTimer：补齐每条要点自己的时间
  → 时间戳夹紧到视频时长 → 写缓存 → 可选写笔记草稿
  → 转成宿主 AiSummaryResponse，交给现有抽屉渲染
```

任何一段模型调用失败只丢该段；全部失败才报错。模型不返回合法 JSON 时自动走纯文本兜底。

## 目录

```
src/main/kotlin/com/bilipai/aisummary/
├── model/       数据类：配置、字幕、分段、结果、UI 状态
├── settings/    服务商模板 + 加密配置存储
├── util/        HTTP 抽象（OkHttp 实现）+ WBI 签名
├── data/        B 站字幕 API / 官方总结兜底 / Chat Completions 客户端 / 结果缓存 / ASR
├── pipeline/    分段器 / Prompt 库 / 容错解析 / 逐条时间 / 主流程
└── ui/          底部抽屉 / 状态控制器 / 设置页
src/test/        JUnit 单测（分段、解析、请求体、缓存键、端到端假 HTTP）
tools/           selfcheck.py：无 Gradle 环境下的等价逻辑自测 + 源码静态检查
samples/         示例配置与可选接线样例（笔记草稿 sink）
```

## 测试

```bash
./gradlew :ai-summary-plugin:testDebugUnitTest   # 44 个 JUnit 用例
python3 tools/selfcheck.py                       # 不需要 Android 环境
```

覆盖：分段不丢行/不死循环、时间戳解析、JSON 容错与截断抢救、WBI 签名表、请求体不含 Key、
全角字符归一化、ASR `verbose_json` 解析、超限拒绝、来源标签、三档详略 Prompt 与缓存键、
预检/ASR 转写缓存、逐条要点时间（内联抽取 / 重复重分配 / 字幕匹配 / 官方时间保留）。

## 已知限制

- 只处理有字幕的视频；无字幕需要开启 ASR（默认关闭，会消耗 ASR 额度与流量）。
- ASR 单次上限受服务商限制（Groq 免费档 25MB），超长视频需要更高档位或切分。
- 未做流式输出，长视频首次生成需要十几秒到一分钟。
- B 站字幕/取流接口为未公开 Web 接口，需要 WBI 签名与登录态，存在风控与变更风险。
- 逐条要点时间在模型没给准时间时是**插值**结果，用于定位，允许有偏差。

## 许可证

与 BiliPai 一致，GPL-3.0。
