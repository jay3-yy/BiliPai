# 六大明星食谱（Cookbook）

不知从何下手？这里精选了覆盖 BiliPai **全部六个宿主黄金插槽** 的标准样板。每一道食谱都不超过 65 行代码，点开即可在工坊在线修改参数并生成手机安装二维码：

<CookbookGrid />

---

## 食谱选型速查表

| 你想解决的真实痛点 | 对应插槽 | 是否需要写 JS | 推荐参考食谱 |
| :--- | :--- | :---: | :--- |
| 删掉夸张前缀、折叠短视频、给长视频打角标 | `feed.pipe` | 否（纯 JSON） | [1. 去标题党与热点折叠](./declickbait) |
| 用大模型总结视频字幕、导出笔记到 Obsidian | `detail.companion` | 是（流式 SSE） | [2. 视频页 AI 省流课代表](./ai-summary) |
| 给港澳台番剧补弹幕、加载外部双语字幕 | `player.track` | 是（异步拉取） | [3. 弹弹Play 外挂弹幕混流](./external-danmaku) |
| 逃离信息茧房、挖掘高赞播比小众宝藏 UP | `home.lens` | 是（纯本地算分） | [4. 「冷门宝藏」推荐透镜](./hidden-gems-lens) |
| 提取热评时间轴置顶、折叠抽奖刷屏 | `community.craft` | 是（纯本地扫描） | [5. 评论区「指路课代表」置顶](./comment-rep) |
| 定制樱粉/冷杉主题并保留液态玻璃实时折射 | `theme.skin` | 否（纯 JSON） | [6. 三材质光学主题装扮](./three-material-skin) |
