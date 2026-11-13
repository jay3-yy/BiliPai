# Changelog

本文件记录 AI 总结插件的迭代。版本号与宿主内置插件（`AiSummaryBuiltinPlugin.version`）独立递增。

## 0.3.3 — 每条要点有自己的时间

- 要点模型从 `List<String>` 升级为 `SummaryPoint(text, startSec)`，缓存读写同步升级（老格式仍可读）。
- 官方兜底保留 `part_outline[].timestamp`；模型写在要点开头的 `[mm:ss]` 会被解析并**从正文去掉**。
- 新增 `PointTimer`：自带时间 → 字幕行二元组匹配 → 章节区间插值；同一章节内重复时间会被重新分配。
- Prompt 要求 MAP 每条要点标注其对应字幕行的时间，REDUCE 原样保留；`PROMPT_VERSION` → 8。

## 0.3.2 — 缓存对无字幕（ASR）视频生效

- 新增不依赖字幕哈希的预检缓存键，在取字幕/下载音频**之前**先查一次。
- ASR 转写单独缓存，换模型/换详略时不重复下载音频与转写。
- 旧缓存命中时自动补写预检键，平滑升级。

## 0.3.1 — 长生成超时与截断容错

- 修复 OkHttp 只设 `callTimeout`、默认 `readTimeout` 10s 导致长生成必超时的问题，
  现在 call/connect/read/write 统一按设置页「单次超时」。
- 输出被截断时不再展示半截 JSON：抢救 `overview` → 紧凑格式重试一次 → 逐段要点兜底。

## 0.3.0 — 可调总结详略

- 新增 `SummaryDetailLevel`（精简 / 标准 / 详细），与「风格」正交：
  风格决定结构，详略决定信息密度与篇幅，冲突时以详略为准。
- Prompt 按档位控制单段要点数、总览长度、章节数与每章要点数，并按档位调整 token 预算。
- 缓存键纳入详略档位，切换档位不会命中旧结果。

## 0.2.0 — 无字幕视频的 ASR 兜底

- 新增 `GroqAsrClient`：下载 B 站 DASH 音轨（选最低码率）→ Whisper `verbose_json` 转写 → 走同一条总结流水线。
- 转写结果带 `start/end`，因此章节与要点时间戳仍然可用。
- 设置页可配 ASR Base URL / 模型 / 语言 / 代理 / 单次上限，并提供连通性测试。
- 音轨下载使用桌面 UA：B 站 CDN 对移动端 UA 会拒绝带 `nbs=1` 的地址。

## 0.1.0 — 首个可用版本

- 新增独立 module `ai-summary-plugin`：配置存储（Keystore AES-GCM）、服务商模板、
  WBI 签名、字幕接口、Chat Completions 客户端、结果缓存。
- map-reduce 流水线：按字符预算分段、并发限流、单段失败只丢该段、合并成
  `overview + chapters + terms + quotes + verdict`。
- 三种总结风格（快速总结 / 完整笔记 / 章节大纲）与容错解析（代码围栏、截断 JSON、纯文本兜底）。
- 宿主接线：编译期内置插件把结果转成 `AiSummaryResponse`，复用现有图标、抽屉、卡片与时间戳跳转。
- 无 Key 时不改变任何原有行为。
