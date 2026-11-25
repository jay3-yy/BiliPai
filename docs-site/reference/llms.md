# AI 辅助开发资产（`llms.txt` & Skills）

BiliPai 文档站原生面向 AI 时代构建。即使你从未写过一行 JavaScript，也可以利用以下官方上下文资产，让 **DeepSeek、Claude、ChatGPT、豆包、Cursor 或 Antigravity** 一次性生成零幻觉、可直接扫码装机的 `.bpx` 扩展。

---

## 1. 在线一键生成精准 Prompt（推荐零基础玩家）

前往 [**在线工坊（Playground）**](/playground)，在顶部点击你想做的场景（如「去标题党」「AI 省流总结」「评论区课代表」），网页会自动为你拼好一段**已裁剪掉无关噪声的胶囊级提示词**。

将其粘贴给任意 AI 聊天助手，再把 AI 回复的代码粘回工坊校验扫码即可。

---

## 2. 标准上下文文件直链

如果你使用支持读取 URL 或上传知识库文件的 AI 工具，直接投喂以下文件：

- **索引文件**：[`/llms.txt`](/llms.txt) —— 轻量目录导航，适合支持按需抓取的 Agent。
- **单文件全量规范**：[`/llms-full.txt`](/llms-full.txt) —— 包含完整 `BpxManifest` TypeScript 定义、`BiliPai.*` 沙盒桥接口、禁用浏览器全局对象清单及六大插槽最小完整样板。

---

## 3. 桌面 AI 编程助手专用 Skill

在 BiliPai 仓库内已内置 `.agents/skills/bpx-authoring/SKILL.md`。使用 Claude Code、Cursor 或 Antigravity 打开仓库（或将该文件放入你的全局 Skills 目录）后，直接输入自然语言需求：

```text
帮我写一个 BiliPai 扩展：当视频时长超过 20 分钟且位于知识区时，在卡片右上角打上「硬核深读」角标，并把标题开头的【xxx】前缀去掉。
```

AI 会自动遵循三条合规红线、精确域名白名单与三材质宿主渲染契约产出标准 `.bpx` 代码。
