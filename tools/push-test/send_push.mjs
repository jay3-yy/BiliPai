#!/usr/bin/env node
/**
 * BiliPai 自建推送零服务器测试脚本（零外部依赖，仅需 Node.js 18+）
 *
 * 用法示例：
 *   1. 在 BiliPai「设置 → 消息通知 → 自建推送」点击「复制服务端配置 JSON」，保存为 config.json
 *   2. 运行：
 *      node tools/push-test/send_push.mjs --config ./config.json --type live \
 *        --title "测试主播 开播了" --body "点击直达直播间" \
 *        --url "https://live.bilibili.com/6"
 */

import crypto from "node:crypto";
import fs from "node:fs";

const PROTOCOL_VERSION = 1;
const CIPHER_ID = "aes-256-gcm";
const DEFAULT_AAD = "bilipai-push-v1";

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (!token.startsWith("--")) continue;
    const key = token.slice(2);
    const next = argv[i + 1];
    if (!next || next.startsWith("--")) {
      args[key] = true;
    } else {
      args[key] = next;
      i++;
    }
  }
  return args;
}

function encryptPayload(plaintextObject, secretKeyBase64, aad = DEFAULT_AAD) {
  const key = Buffer.from(secretKeyBase64.trim(), "base64");
  if (key.length !== 32) {
    throw new Error(`secretKey 解码后必须是 32 字节，当前为 ${key.length} 字节`);
  }
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv(CIPHER_ID, key, iv, { authTagLength: 16 });
  cipher.setAAD(Buffer.from(aad, "utf8"));
  const encrypted = Buffer.concat([
    cipher.update(JSON.stringify(plaintextObject), "utf8"),
    cipher.final(),
  ]);
  const tag = cipher.getAuthTag();
  return {
    v: PROTOCOL_VERSION,
    enc: CIPHER_ID,
    iv: iv.toString("base64"),
    ct: Buffer.concat([encrypted, tag]).toString("base64"),
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || args.h) {
    console.log(`用法:
  node send_push.mjs --config ./config.json [--type video|live|dynamic|message|test]
                     [--title 标题] [--body 正文] [--url 跳转链接]
                     [--cover 封面URL] [--avatar 头像URL] [--group 分组ID]
                     [--relay-auth Bearer令牌] [--plaintext]`);
    process.exit(0);
  }

  let config = {};
  if (args.config) {
    config = JSON.parse(fs.readFileSync(args.config, "utf8"));
  }

  const mode = args.mode || config.mode || "unified_push";
  const secretKey = args.key || config.secretKey || process.env.BILIPAI_PUSH_KEY || "";
  const aad = config.aad || DEFAULT_AAD;
  const endpoint = args.endpoint || config.endpoint || process.env.BILIPAI_PUSH_ENDPOINT || "";
  const fcmToken = args.token || config.fcmToken || process.env.BILIPAI_FCM_TOKEN || "";
  const relayUrl = args.relay || config.relayUrl || process.env.BILIPAI_FCM_RELAY_URL || "";
  const relayAuth = args["relay-auth"] || process.env.BILIPAI_FCM_RELAY_AUTH || "";

  const nowSec = Math.floor(Date.now() / 1000);
  const payload = {
    v: PROTOCOL_VERSION,
    id: args.id || `cli_${nowSec}_${crypto.randomBytes(3).toString("hex")}`,
    type: args.type || "test",
    title: args.title || "BiliPai 远程推送测试",
    body: args.body || "来自本机脚本的 AES-256-GCM 端到端加密消息",
    ts: nowSec,
  };
  if (args.url) payload.url = args.url;
  if (args.cover) payload.cover = args.cover;
  if (args.avatar) payload.avatar = args.avatar;
  if (args.group) payload.group = args.group;

  const wirePayload = args.plaintext
    ? payload
    : encryptPayload(payload, secretKey, aad);

  if (mode === "unified_push") {
    if (!endpoint) {
      throw new Error("UnifiedPush 模式缺少 endpoint（请在 config.json 或 --endpoint 指定）");
    }
    const resp = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify(wirePayload),
    });
    const text = await resp.text();
    console.log(`[UnifiedPush] HTTP ${resp.status} ${resp.statusText}`);
    if (text) console.log(text);
    if (!resp.ok) process.exit(1);
    return;
  }

  if (mode === "fcm_relay") {
    if (!relayUrl || !fcmToken) {
      throw new Error("FCM 中转模式需要 relayUrl 和 fcmToken");
    }
    const headers = { "Content-Type": "application/json; charset=utf-8" };
    if (relayAuth) headers.Authorization = `Bearer ${relayAuth}`;
    const resp = await fetch(relayUrl, {
      method: "POST",
      headers,
      body: JSON.stringify({
        token: fcmToken,
        payload: wirePayload,
      }),
    });
    const text = await resp.text();
    console.log(`[FCM Relay] HTTP ${resp.status} ${resp.statusText}`);
    if (text) console.log(text);
    if (!resp.ok) process.exit(1);
    return;
  }

  throw new Error(`不支持的推送模式: ${mode}`);
}

main().catch((err) => {
  console.error("发送失败:", err.message);
  process.exit(1);
});
