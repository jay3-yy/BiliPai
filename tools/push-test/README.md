# BiliPai 自建推送测试与中转模板

本目录提供零服务器测试脚本和无状态 FCM 中转模板，对应客户端「设置 → 消息通知 → 自建推送」。

## 1. 零服务器测通 UnifiedPush（推荐，30 秒上手）

1. 手机安装开源分发器 **ntfy**（有 GMS 可装 Play 版走 FCM 零常驻耗电；无 GMS 装 F-Droid 版）。
2. 在 BiliPai 打开 **设置 → 消息通知 → 自建推送**：
   - 将「接收通道」切换为 **UnifiedPush（推荐）**；
   - 选择已安装的 **ntfy**，等待出现 `https://ntfy.sh/up...` 端点；
   - 点击 **复制服务端配置 JSON**，在电脑上保存为 `config.json`。
3. 在本机电脑执行（支持 **纯 Python 3 零依赖** 或 **Node.js 18+**，二选一）：

```bash
# 方式 A：纯 Python 3（零 pip 依赖，macOS/Linux 开箱即用）
python3 tools/push-test/send_push.py \
  --config ./config.json \
  --type live \
  --title "测试主播 开播了" \
  --body "锁屏杀后台也能秒达（AES-256-GCM 端到端加密）" \
  --url "https://live.bilibili.com/6"

# 方式 B：Node.js 18+（零 npm 依赖）
node tools/push-test/send_push.mjs \
  --config ./config.json \
  --type live \
  --title "测试主播 开播了" \
  --body "锁屏杀后台也能秒达（AES-256-GCM 端到端加密）" \
  --url "https://live.bilibili.com/6"
```

## 2. 零服务器测通 FCM 无状态中转（Cloudflare Workers 免费版）

1. 在 Cloudflare Workers 新建一个 Worker，贴入 `tools/push-test/fcm-relay-worker.js`。
2. 在 Worker 设置里添加 Secret：
   - `FIREBASE_SERVICE_ACCOUNT_JSON`：Firebase 项目服务账号 JSON
   - `RELAY_AUTH_TOKEN`（可选）：自定义访问令牌
3. 在 BiliPai「自建推送」中将通道切为 **FCM 无状态中转**，填入你的 Worker 地址（如 `https://xxx.workers.dev/push`），点击 **复制服务端配置 JSON** 保存为 `config.json`。
4. 在本机执行：

```bash
node tools/push-test/send_push.mjs \
  --config ./config.json \
  --relay-auth "你的RELAY_AUTH_TOKEN" \
  --type video \
  --title "UP主 发布了新视频" \
  --body "点按通知直达视频详情页" \
  --url "https://www.bilibili.com/video/BV1GJ411x7h7"
```

## 3. 协议与安全约定

- **加密方式**：`AES-256-GCM`，256-bit 密钥、12 字节随机 IV、128-bit Tag，`AAD = "bilipai-push-v1"`。
- **强制不降级**：客户端默认拒绝所有明文包，仅当手动打开「允许明文调试」时才接收明文。
- **防重放**：加密载荷强制校验 `id` 去重与 `ts`（允许 ±10 分钟时间差）。
- **白名单过滤**：
  - `url` 仅放行 B 站内容链接与安全 `bilipai://` 快捷路由，拒绝插件安装链接和任意外部网址；
  - `cover` / `avatar` 仅放行 `https://` 下的 B 站图床域名（`*.hdslb.com`、`*.bilivideo.com`、`*.bilibili.com`）。
