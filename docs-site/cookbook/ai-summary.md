# 食谱 2：视频页 AI 省流课代表（`detail.companion`）

- **交付形态**：`.bpx.js`（QuickJS 后台沙盒 + `BiliPai.http.streamSse` 流式桥）
- **原位挂载**：视频详情页简介下方「省流课代表」胶囊 → 同窗半屏玻璃面板
- **一键实操**：[⚡ 在校验扫码台打开此食谱](/playground?recipe=ai-summary)

## 设计亮点

1. **BYOK（自带密钥）零服务端成本**：通过 `settings` 声明 `type: "secret"` 的 `apiKey`，BiliPai 自动在安装核验单首屏与扩展设置页渲染脱敏输入框，密钥仅加密保存在用户本机隔离存储。
2. **逐字流式半屏渲染**：调用 `emit({ blocks: [{ type: "markdown", content }] })` 时，宿主的 `AppModalBottomSheet`（自动适配液态玻璃折射底板与 MD3/Miuix 圆角）会平滑刷新正文，无需等待整段生成完毕。
3. **主线程安全时间戳跳转**：返回 `{ type: "timestamps", items: [{ timeSec: 195, label: "架构核心" }] }` 后，用户点击任意时间芯片，由宿主调度器在主线程驱动播放器精准跳转，彻底杜绝跨线程操作播放器的崩溃隐患。

## 二次改造灵感（可直接发给 AI）

- 将接口切换为 Gemini、OpenAI 或局域网本地 Ollama；
- 在底部 `actions` 增加一枚「剪藏到 Obsidian」按钮，生成 `obsidian://new?name=...&content=...` 唤起链。
