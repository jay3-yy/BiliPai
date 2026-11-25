# 食谱 3：弹弹Play 外挂弹幕混流（`player.track`）

- **交付形态**：`.bpx.js`（QuickJS 沙盒 + 精确域名白名单）
- **原位挂载**：播放器弹幕渲染层与更多菜单轨道开关
- **一键实操**：[⚡ 在校验扫码台打开此食谱](/playground?recipe=external-danmaku)

## 设计亮点

观看港澳台番剧、海外纪录片或冷门生肉搬运时，主站弹幕往往寥寥无几：
1. **精确域名沙盒**：清单仅申请 `networkDomains: ["api.dandanplay.net"]` 与 `videoContext: ["metadata"]`，不接触任何用户账号凭证。
2. **标准弹幕混流结构**：只需返回 `{ danmaku: [{ timeMs, text, mode, color }] }`，宿主弹幕引擎自动按时间轴与原生弹幕池归并渲染，并遵守用户当前的弹幕字号、不透明度、防挡字幕与同屏密度设置。

## 二次改造灵感（可直接发给 AI）

- 同时返回 `segments` 时间轴区间，为番剧自动标记 OP / ED 跳过点；
- 接入开源双语字幕站接口，返回 `subtitles` 轨道供播放器原生字幕菜单切换。
