# 3 分钟破冰上手：三级创作阶梯

无论你是只想定制一条去标题党规则的普通 B 站用户，还是熟悉 TypeScript 的脚本开发者，都可以选择最顺手的路径在 **3～5 分钟**内跑通第一个 `.bpx` 扩展。

---

## 路径 A：AI 一句话共创流（零编程基础首选）

1. 打开 [**在线校验扫码台**](/playground)，在顶部的「AI 配方生成器」点选你想改造的位置（例如 **「视频页半屏伴侣面板」** 或 **「推荐流净化与角标」**）。
2. 在需求框写下你的想法（例如：`帮我把标题带「震惊」「家人们谁懂」的前缀删掉，并给超过 30 分钟的视频打上「深度长片」角标`），点击 **「复制完整 AI 提示词」**。
3. 把提示词发给 DeepSeek、Claude、豆包或 ChatGPT，复制它吐出的代码块。
4. 粘回 [**在线校验扫码台**](/playground)：
   - 若出现校验提示，点击错误卡片右侧的 **「复制 AI 修复指令」** 发回聊天框即可一轮修好；
   - 校验全绿后，用手机打开 BiliPai 扫描右侧自动生成的 `bilipai://` 二维码，核验权限后即刻原位生效！

---

## 路径 B：浏览器改食谱直出（2 分钟微调）

1. 在 [**六大明星食谱**](/cookbook/) 中挑一个最接近你想法的成品，点击 **「在工坊打开并扫码」**。
2. 直接在网页编辑器里改两行关键词、API 域名或主题色值，记得把 `author.name` 和 `author.biliMid` 改成你自己的昵称与 B 站 UID。
3. 右侧二维码会随输入实时更新（压缩体积 `≤ 2048 Bytes` 时支持纯离线扫码；超长脚本或皮肤包点击 **「导出 .bpx 文件」** 发送到手机打开即可）。

---

## 路径 C：本地工程化热重载流（进阶开发者）

需要编写多模块逻辑、调试 SSE/WebSocket 或制作包含多张贴图的主题装扮包时，使用仓库自带的 CLI 工具链：

```bash
# 1. 使用脚手架初始化带类型提示的 .bpx 工程
node plugins/tools/create-bpx.mjs community.myname.awesome_ext

# 2. 本地运行与投稿机器人完全一致的静态合规体检
node plugins/tools/validate-bpx.mjs ./awesome_ext
```

配合仓库内置的 [`.agents/skills/bpx-authoring/SKILL.md`](https://github.com/jay3-yy/BiliPai/blob/main/.agents/skills/bpx-authoring/SKILL.md) 或 [`/llms-full.txt`](/llms-full.txt)，在 Cursor / Claude Code / Antigravity 中可获得完整的沙盒 API 补全与红线检查。
