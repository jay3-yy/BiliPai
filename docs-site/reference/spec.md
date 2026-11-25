# `.bpx` v1 契约与 Schema 字典

本页是 BiliPai 扩展系统 2.0（`bpxVersion: 1`）的唯一技术规范参考。AI 辅助开发可直接将全量单文件上下文 [`/llms-full.txt`](/llms-full.txt) 投喂给任意大模型。

---

## 1. 交付形态与识别规则

BiliPai 统一安装器支持三种等价的 `.bpx` 载体，用户无需区分导入入口：

| 形态 | 适用场景 | 运行时路径 | 二维码离线直装 |
| :--- | :--- | :--- | :--- |
| **`.bpx.json`** | 纯过滤/改标题规则、纯色板 Token 皮肤 | **Kotlin 原生零分配快路径**（不启动 JS 引擎） | 支持（压后 `≤ 2KB`） |
| **`.bpx.js`** | AI 伴侣、外挂弹幕、算法透镜、评论提炼、跨源频道 | **QuickJS 后台隔离沙盒**（冷启动 `< 3ms`） | 支持（压后 `≤ 2KB`） |
| **`.bpx` (ZIP)** | 带本地图标、进度条游标贴图、Lottie 动效与签名文件的复合包 | 按 `mounts` 自动分流快路径或 QuickJS | 走文件导入或 CLI 局域网直传 |

---

## 2. `manifest` 顶层字段规范

| 字段 | 类型 | 必填 | 校验规则与说明 |
| :--- | :--- | :---: | :--- |
| `bpxVersion` | `1` | 是 | 固定整数 `1` |
| `id` | `string` | 是 | 反向域名格式，正则 `^[a-z0-9]+(\.[a-z0-9_]+){1,4}$`，长度 `5..64`，版本升级保持不变 |
| `name` | `string` | 是 | 展示名称，`1..32` 字符 |
| `version` | `string` | 是 | 标准语义化版本，如 `1.0.0` |
| `description` | `string` | 是 | 一句话核心用途说明，展示在安装核验单与工坊卡片（建议 `≤ 60` 字） |
| `author` | `AuthorInfo` | 是 | 创作者名片信息：`{ name, biliMid?, github?, bio?, avatar? }`；提供 `biliMid` 时，App 内核验单与详情页自动渲染「访问 TA 的 B 站空间」原生跳转按钮 |
| `mounts` | `MountDecl[]` | 是 | 至少声明 1 个挂载插槽，安装核验单据此生成可视化位置预览 |
| `permissions` | `Permissions` | 否 | 最小权限声明；未声明的上下文与网络域名在沙盒层直接拦截 |
| `settings` | `SettingField[]` | 否 | 声明式设置项；宿主自动生成符合 MD3 / Miuix / 液态玻璃规范的配置子页并接入全局设置搜索 |
| `licenseNote` | `string` | 条件 | 当包含 `theme.skin` 插槽或投稿至官方精选源时必填，声明原创或可再分发授权来源 |

---

## 3. 六大宿主插槽（Host Slots）契约总表

| `slot` 枚举值 | 挂载界面位置 | 执行预算与熔断线 | 允许输出的语义模型 |
| :--- | :--- | :--- | :--- |
| `feed.pipe` | 首页推荐、热门、搜索与相关视频流管线 | 批处理 `≤ 16ms` / 20 张卡 | `hide`（隐藏）、`fold`（同类折叠胶囊）、`badge`（毛玻璃角标）、`rewriteTitle`（正则净化标题） |
| `detail.companion` | 视频详情页简介下方「扩展胶囊」→ 半屏玻璃弹窗 | 异步流式 `≤ 15s`（退出页面自动取消） | `markdown`、`timestamps`（点击直跳秒数）、`kv`、`actions`（复制/外部 URI） |
| `player.track` | 播放器进度条区间层、弹幕层、双语字幕轨 | 异步加载 `≤ 10s` | `segments`（跳过/高能标记）、`danmaku`（混流弹幕）、`subtitles`（外挂字幕轨） |
| `home.lens` | 首页顶栏透镜切换器 / 可固定平级频道 Tab | 重排 `≤ 50ms`；跨源请求 `≤ 10s` | `rerank`（候选打分与推荐理由签）或 `channel`（吸顶分类 Chip + 媒体流列表） |
| `community.craft` | 视频评论区顶栏摘要位 & 弹幕选中气泡菜单 | 批处理 `≤ 20ms` | `pinnedCard`（指路/课代表速览卡）、`foldRpids`（刷屏折叠）、弹幕词条释义 |
| `theme.skin` | 首页顶底栏、抽屉、个人页、进度条游标、点赞动效 | 纯资源映射（0 脚本开销） | `light` / `dark` 语义色、`liquidGlass` 折射染色（`tintOpacity: 0.08..0.45`）及声明式素材路径 |

---

## 4. Schema 驱动配置项（`settings`）

无需手写任何 UI 代码，宿主自动按 `AppUiStyle` 与液态玻璃开关渲染原生设置控件：

- `switch`：布尔开关（对应 `AppAdaptiveSwitch`）
- `segment`：2～4 个互斥短选项分段器（对应等宽玻璃/原生分段槽）
- `select`：多选项弹出菜单（对应 `AppGlassMenu`，不在设置页平铺占行）
- `slider`：数值范围滑块（声明 `min`、`max`、`step`、`unit`）
- `text` / `secret`：单行文本或脱敏密钥输入框（`required: true` 时自动出现在安装核验单首屏速配区）
- `stringList`：可增删的关键词/正则词条集合

---

## 5. QuickJS 沙盒桥 API 与安全边界

1. **账号凭证零接触**：`BiliPai.http` 运行在无 Cookie 隔离客户端上，宿主永远不会向第三方扩展暴露 `SESSDATA`、`bili_jct` 或刷新令牌。
2. **主线程与播放器零直连**：扩展无法拿到 `ExoPlayer` 引用；所有进度跳转、轨道挂载均以纯数据指令返回宿主，由宿主切回主线程安全校验后执行。
3. **后台内存自愈**：应用退入后台满 15 秒时，宿主自动销毁空闲 QuickJS Isolate 并释放编译缓存。
