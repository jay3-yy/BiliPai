/**
 * BiliPai FCM 无状态中转网关（Cloudflare Workers 模板）
 *
 * 特性：
 * - 不落盘、不存设备列表、不持有用户的 AES 密钥（仅透传端到端加密后的 data-only 载荷）。
 * - 使用 Firebase Service Account 签发短期 OAuth2 Token 调用 FCM HTTP v1 API。
 *
 * 环境变量（Secrets）：
 * - FIREBASE_SERVICE_ACCOUNT_JSON: Firebase 控制台下载的服务账号 JSON 字符串
 * - RELAY_AUTH_TOKEN (可选): 若设置，则要求请求头携带 Authorization: Bearer <token>
 */

const MAX_BODY_BYTES = 3800;
let cachedAccessToken = null;
let cachedTokenExpiryMs = 0;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST" || (url.pathname !== "/push" && url.pathname !== "/")) {
      return jsonResponse({ error: "method_or_path_not_allowed" }, 405);
    }

    if (env.RELAY_AUTH_TOKEN) {
      const auth = request.headers.get("Authorization") || "";
      if (auth !== `Bearer ${env.RELAY_AUTH_TOKEN}`) {
        return jsonResponse({ error: "unauthorized" }, 401);
      }
    }

    const rawBody = await request.text();
    if (new TextEncoder().encode(rawBody).byteLength > MAX_BODY_BYTES) {
      return jsonResponse({ error: "payload_too_large" }, 413);
    }

    let body;
    try {
      body = JSON.parse(rawBody);
    } catch {
      return jsonResponse({ error: "invalid_json" }, 400);
    }

    const token = typeof body.token === "string" ? body.token.trim() : "";
    const payload = body.payload;
    if (!token || !payload || typeof payload !== "object") {
      return jsonResponse({ error: "missing_token_or_payload" }, 400);
    }

    const serializedPayload = JSON.stringify(payload);
    if (new TextEncoder().encode(serializedPayload).byteLength > 3500) {
      return jsonResponse({ error: "fcm_data_limit_exceeded" }, 413);
    }

    const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
    const accessToken = await getGoogleAccessToken(serviceAccount);
    const fcmEndpoint = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`;

    const fcmResponse = await fetch(fcmEndpoint, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json; charset=utf-8",
      },
      body: JSON.stringify({
        message: {
          token,
          android: {
            priority: "HIGH",
          },
          // 必须仅发送 data 字段，确保 BiliPai 无论前后台都走本地解密与富通知派发。
          data: {
            payload: serializedPayload,
          },
        },
      }),
    });

    const fcmText = await fcmResponse.text();
    return new Response(fcmText, {
      status: fcmResponse.status,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
  },
};

async function getGoogleAccessToken(serviceAccount) {
  const nowMs = Date.now();
  if (cachedAccessToken && nowMs < cachedTokenExpiryMs - 60_000) {
    return cachedAccessToken;
  }

  const nowSec = Math.floor(nowMs / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64UrlEncode(
    JSON.stringify({
      iss: serviceAccount.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: nowSec,
      exp: nowSec + 3600,
    }),
  );
  const unsigned = `${header}.${claim}`;
  const privateKey = await importPrivateKey(serviceAccount.private_key);
  const signatureBuffer = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    new TextEncoder().encode(unsigned),
  );
  const assertion = `${unsigned}.${base64UrlEncodeBytes(new Uint8Array(signatureBuffer))}`;

  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!tokenResp.ok) {
    throw new Error(`OAuth token exchange failed: HTTP ${tokenResp.status}`);
  }
  const tokenJson = await tokenResp.json();
  cachedAccessToken = tokenJson.access_token;
  cachedTokenExpiryMs = nowMs + (tokenJson.expires_in || 3600) * 1000;
  return cachedAccessToken;
}

async function importPrivateKey(pem) {
  const cleaned = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(cleaned);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return crypto.subtle.importKey(
    "pkcs8",
    bytes.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function base64UrlEncode(text) {
  return base64UrlEncodeBytes(new TextEncoder().encode(text));
}

function base64UrlEncodeBytes(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function jsonResponse(obj, status) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
