#!/usr/bin/env python3
"""
BiliPai 自建推送纯 Python 测试发送脚本（零第三方依赖，兼容 Python 3.7+）

优先使用已安装的 cryptography；未安装时自动使用内置纯 Python AES-256-GCM 实现，
无需 pip install 即可直接向 UnifiedPush（如 ntfy.sh）或 FCM 无状态中转发送端到端加密推送。

用法示例（UnifiedPush，免部署 Worker）：
  python3 tools/push-test/send_push.py --config ./config.json \
    --type live --title "测试主播 开播了" --body "点按直达直播间" \
    --url "https://live.bilibili.com/6"
"""

import argparse
import base64
import json
import os
import secrets
import ssl
import sys
import time
import urllib.error
import urllib.request

PROTOCOL_VERSION = 1
CIPHER_ID = "aes-256-gcm"
DEFAULT_AAD = b"bilipai-push-v1"

# --- 内置纯 Python AES-256-GCM（零依赖兜底） ---

_SBOX = (
    0x63, 0x7C, 0x77, 0x7B, 0xF2, 0x6B, 0x6F, 0xC5, 0x30, 0x01, 0x67, 0x2B, 0xFE, 0xD7, 0xAB, 0x76,
    0xCA, 0x82, 0xC9, 0x7D, 0xFA, 0x59, 0x47, 0xF0, 0xAD, 0xD4, 0xA2, 0xAF, 0x9C, 0xA4, 0x72, 0xC0,
    0xB7, 0xFD, 0x93, 0x26, 0x36, 0x3F, 0xF7, 0xCC, 0x34, 0xA5, 0xE5, 0xF1, 0x71, 0xD8, 0x31, 0x15,
    0x04, 0xC7, 0x23, 0xC3, 0x18, 0x96, 0x05, 0x9A, 0x07, 0x12, 0x80, 0xE2, 0xEB, 0x27, 0xB2, 0x75,
    0x09, 0x83, 0x2C, 0x1A, 0x1B, 0x6E, 0x5A, 0xA0, 0x52, 0x3B, 0xD6, 0xB3, 0x29, 0xE3, 0x2F, 0x84,
    0x53, 0xD1, 0x00, 0xED, 0x20, 0xFC, 0xB1, 0x5B, 0x6A, 0xCB, 0xBE, 0x39, 0x4A, 0x4C, 0x58, 0xCF,
    0xD0, 0xEF, 0xAA, 0xFB, 0x43, 0x4D, 0x33, 0x85, 0x45, 0xF9, 0x02, 0x7F, 0x50, 0x3C, 0x9F, 0xA8,
    0x51, 0xA3, 0x40, 0x8F, 0x92, 0x9D, 0x38, 0xF5, 0xBC, 0xB6, 0xDA, 0x21, 0x10, 0xFF, 0xF3, 0xD2,
    0xCD, 0x0C, 0x13, 0xEC, 0x5F, 0x97, 0x44, 0x17, 0xC4, 0xA7, 0x7E, 0x3D, 0x64, 0x5D, 0x19, 0x73,
    0x60, 0x81, 0x4F, 0xDC, 0x22, 0x2A, 0x90, 0x88, 0x46, 0xEE, 0xB8, 0x14, 0xDE, 0x5E, 0x0B, 0xDB,
    0xE0, 0x32, 0x3A, 0x0A, 0x49, 0x06, 0x24, 0x5C, 0xC2, 0xD3, 0xAC, 0x62, 0x91, 0x95, 0xE4, 0x79,
    0xE7, 0xC8, 0x37, 0x6D, 0x8D, 0xD5, 0x4E, 0xA9, 0x6C, 0x56, 0xF4, 0xEA, 0x65, 0x7A, 0xAE, 0x08,
    0xBA, 0x78, 0x25, 0x2E, 0x1C, 0xA6, 0xB4, 0xC6, 0xE8, 0xDD, 0x74, 0x1F, 0x4B, 0xBD, 0x8B, 0x8A,
    0x70, 0x3E, 0xB5, 0x66, 0x48, 0x03, 0xF6, 0x0E, 0x61, 0x35, 0x57, 0xB9, 0x86, 0xC1, 0x1D, 0x9E,
    0xE1, 0xF8, 0x98, 0x11, 0x69, 0xD9, 0x8E, 0x94, 0x9B, 0x1E, 0x87, 0xE9, 0xCE, 0x55, 0x28, 0xDF,
    0x8C, 0xA1, 0x89, 0x0D, 0xBF, 0xE6, 0x42, 0x68, 0x41, 0x99, 0x2D, 0x0F, 0xB0, 0x54, 0xBB, 0x16,
)

_RCON = (0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1B, 0x36)


def _xtime(a):
    return ((a << 1) ^ 0x1B) & 0xFF if (a & 0x80) else (a << 1)


def _expand_key_256(key):
    words = [list(key[i:i + 4]) for i in range(0, 32, 4)]
    for i in range(8, 60):
        temp = words[i - 1][:]
        if i % 8 == 0:
            temp = [_SBOX[b] for b in temp[1:] + temp[:1]]
            temp[0] ^= _RCON[i // 8]
        elif i % 8 == 4:
            temp = [_SBOX[b] for b in temp]
        words.append([words[i - 8][j] ^ temp[j] for j in range(4)])
    return [bytes(b for w in words[r * 4:(r + 1) * 4] for b in w) for r in range(15)]


def _aes256_encrypt_block(block, round_keys):
    s = list(block)
    rk = round_keys[0]
    for i in range(16):
        s[i] ^= rk[i]
    for rnd in range(1, 15):
        s = [_SBOX[b] for b in s]
        # ShiftRows (column-major state)
        s = [
            s[0], s[5], s[10], s[15],
            s[4], s[9], s[14], s[3],
            s[8], s[13], s[2], s[7],
            s[12], s[1], s[6], s[11],
        ]
        if rnd < 14:
            for c in range(0, 16, 4):
                a0, a1, a2, a3 = s[c], s[c + 1], s[c + 2], s[c + 3]
                t = a0 ^ a1 ^ a2 ^ a3
                s[c] ^= t ^ _xtime(a0 ^ a1)
                s[c + 1] ^= t ^ _xtime(a1 ^ a2)
                s[c + 2] ^= t ^ _xtime(a2 ^ a3)
                s[c + 3] ^= t ^ _xtime(a3 ^ a0)
        rk = round_keys[rnd]
        for i in range(16):
            s[i] ^= rk[i]
    return bytes(s)


def _gf_mul(x, y):
    z = 0
    v = x
    for i in range(127, -1, -1):
        if (y >> i) & 1:
            z ^= v
        if v & 1:
            v = (v >> 1) ^ 0xE1000000000000000000000000000000
        else:
            v >>= 1
    return z


def _ghash(h_int, aad, ciphertext):
    y = 0
    for data in (aad, ciphertext):
        pad_len = (-len(data)) % 16
        padded = data + (b"\x00" * pad_len)
        for i in range(0, len(padded), 16):
            block_int = int.from_bytes(padded[i:i + 16], "big")
            y = _gf_mul(y ^ block_int, h_int)
    lengths = ((len(aad) * 8) << 64) | (len(ciphertext) * 8)
    y = _gf_mul(y ^ lengths, h_int)
    return y.to_bytes(16, "big")


def _pure_aes256_gcm_encrypt(key, iv, plaintext, aad):
    round_keys = _expand_key_256(key)
    h_int = int.from_bytes(_aes256_encrypt_block(b"\x00" * 16, round_keys), "big")
    j0 = iv + b"\x00\x00\x00\x01"
    ctr = int.from_bytes(j0[12:], "big")
    ciphertext = bytearray()
    for i in range(0, len(plaintext), 16):
        ctr = (ctr + 1) & 0xFFFFFFFF
        counter_block = iv + ctr.to_bytes(4, "big")
        keystream = _aes256_encrypt_block(counter_block, round_keys)
        chunk = plaintext[i:i + 16]
        ciphertext.extend(a ^ b for a, b in zip(chunk, keystream))
    ct_bytes = bytes(ciphertext)
    s_block = _ghash(h_int, aad, ct_bytes)
    e_j0 = _aes256_encrypt_block(j0, round_keys)
    tag = bytes(a ^ b for a, b in zip(e_j0, s_block))
    return ct_bytes + tag


def encrypt_aes256_gcm(key_bytes, iv_bytes, plaintext_bytes, aad_bytes):
    if len(key_bytes) != 32:
        raise ValueError(f"secretKey 解码后必须是 32 字节，当前为 {len(key_bytes)} 字节")
    if len(iv_bytes) != 12:
        raise ValueError("AES-GCM IV 必须是 12 字节")
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM  # type: ignore

        return AESGCM(key_bytes).encrypt(iv_bytes, plaintext_bytes, aad_bytes)
    except ImportError:
        return _pure_aes256_gcm_encrypt(key_bytes, iv_bytes, plaintext_bytes, aad_bytes)


def build_encrypted_envelope(payload_obj, secret_key_b64, aad_str):
    key_bytes = base64.b64decode(secret_key_b64.strip())
    iv_bytes = secrets.token_bytes(12)
    plaintext_bytes = json.dumps(payload_obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    ct_and_tag = encrypt_aes256_gcm(key_bytes, iv_bytes, plaintext_bytes, aad_str.encode("utf-8"))
    return {
        "v": PROTOCOL_VERSION,
        "enc": CIPHER_ID,
        "iv": base64.b64encode(iv_bytes).decode("ascii"),
        "ct": base64.b64encode(ct_and_tag).decode("ascii"),
    }


def run_nist_self_test():
    """验证纯 Python AES-256-GCM 与 NIST SP 800-38D Test Case 16（含 AAD）完全一致。"""
    key = bytes.fromhex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    iv = bytes.fromhex("cafebabefacedbaddecaf888")
    pt = bytes.fromhex(
        "d9313225f88406e5a55909c5aff5269a"
        "86a7a9531534f7da2e4c303d8a318a72"
        "1c3c0c95956809532fcf0e2449a6b525"
        "b16aedf5aa0de657ba637b39"
    )
    aad = bytes.fromhex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
    expected_ct = bytes.fromhex(
        "522dc1f099567d07f47f37a32a84427d"
        "643a8cdcbfe5c0c97598a2bd2555d1aa"
        "8cb08e48590dbb3da7b08b1056828838"
        "c5f61e6393ba7a0abcc9f662"
    )
    expected_tag = bytes.fromhex("76fc6ece0f4e1768cddf8853bb2d551b")
    actual = _pure_aes256_gcm_encrypt(key, iv, pt, aad)
    assert actual == expected_ct + expected_tag, f"NIST AES-256-GCM 向量校验失败: {actual.hex()}"
    print("NIST SP 800-38D AES-256-GCM (含 AAD) 自检通过")


def _build_ssl_context():
    ctx = ssl.create_default_context()
    if ctx.cert_store_stats().get("x509_ca", 0) == 0:
        for ca_path in ("/etc/ssl/cert.pem", "/etc/ssl/certs/ca-certificates.crt"):
            if os.path.isfile(ca_path):
                ctx.load_verify_locations(cafile=ca_path)
                break
    return ctx


def post_json(url, data_obj, bearer_token=""):
    body = json.dumps(data_obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if bearer_token:
        headers["Authorization"] = f"Bearer {bearer_token}"
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    ssl_ctx = _build_ssl_context()
    try:
        with urllib.request.urlopen(req, timeout=15, context=ssl_ctx) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode("utf-8", errors="replace")


def main():
    parser = argparse.ArgumentParser(description="BiliPai 自建推送纯 Python 测试发送脚本")
    parser.add_argument("--self-test", action="store_true", help="运行 NIST AES-256-GCM 算法自检后退出")
    parser.add_argument("--config", help="BiliPai 导出的服务端配置 JSON 文件路径")
    parser.add_argument("--mode", choices=["unified_push", "fcm_relay"], help="推送通道模式")
    parser.add_argument("--endpoint", help="UnifiedPush Endpoint URL（如 https://ntfy.sh/up...）")
    parser.add_argument("--token", help="FCM 设备 Token")
    parser.add_argument("--relay", help="FCM 无状态中转网关地址")
    parser.add_argument("--relay-auth", default="", help="中转网关可选 Bearer 令牌")
    parser.add_argument("--key", help="Base64 编码的 32 字节 AES-256-GCM 共享密钥")
    parser.add_argument("--type", default="test", choices=["video", "live", "dynamic", "message", "test"])
    parser.add_argument("--id", help="自定义消息去重 ID")
    parser.add_argument("--title", default="BiliPai 远程推送测试")
    parser.add_argument("--body", default="来自纯 Python 脚本的 AES-256-GCM 端到端加密消息")
    parser.add_argument("--url", help="点击通知跳转链接（B 站链接或安全 bilipai:// 路由）")
    parser.add_argument("--cover", help="通知大图封面（仅限 https B 站图床）")
    parser.add_argument("--avatar", help="通知头像（仅限 https B 站图床）")
    parser.add_argument("--group", help="通知分组 ID")
    parser.add_argument("--plaintext", action="store_true", help="发送明文包（需客户端手动开启允许明文调试）")
    args = parser.parse_args()

    if args.self_test:
        run_nist_self_test()
        return

    config = {}
    if args.config:
        with open(args.config, "r", encoding="utf-8") as f:
            config = json.load(f)

    mode = args.mode or config.get("mode") or "unified_push"
    secret_key = args.key or config.get("secretKey") or os.environ.get("BILIPAI_PUSH_KEY", "")
    aad = config.get("aad") or DEFAULT_AAD.decode("ascii")
    endpoint = args.endpoint or config.get("endpoint") or os.environ.get("BILIPAI_PUSH_ENDPOINT", "")
    fcm_token = args.token or config.get("fcmToken") or os.environ.get("BILIPAI_FCM_TOKEN", "")
    relay_url = args.relay or config.get("relayUrl") or os.environ.get("BILIPAI_FCM_RELAY_URL", "")
    relay_auth = args.relay_auth or os.environ.get("BILIPAI_FCM_RELAY_AUTH", "")

    now_sec = int(time.time())
    payload = {
        "v": PROTOCOL_VERSION,
        "id": args.id or f"py_{now_sec}_{secrets.token_hex(3)}",
        "type": args.type,
        "title": args.title,
        "body": args.body,
        "ts": now_sec,
    }
    if args.url:
        payload["url"] = args.url
    if args.cover:
        payload["cover"] = args.cover
    if args.avatar:
        payload["avatar"] = args.avatar
    if args.group:
        payload["group"] = args.group

    if args.plaintext:
        wire_payload = payload
    else:
        if not secret_key:
            sys.exit("错误：缺少共享密钥 secretKey（请通过 --config 或 --key 提供）")
        wire_payload = build_encrypted_envelope(payload, secret_key, aad)

    if mode == "unified_push":
        if not endpoint:
            sys.exit("错误：UnifiedPush 模式缺少 endpoint（请通过 --config 或 --endpoint 提供）")
        status, resp_text = post_json(endpoint, wire_payload)
        print(f"[UnifiedPush] HTTP {status}")
        if resp_text:
            print(resp_text)
        if status < 200 or status >= 300:
            sys.exit(1)
        return

    if mode == "fcm_relay":
        if not relay_url or not fcm_token:
            sys.exit("错误：FCM 中转模式需要 relayUrl 与 fcmToken")
        status, resp_text = post_json(
            relay_url,
            {"token": fcm_token, "payload": wire_payload},
            bearer_token=relay_auth,
        )
        print(f"[FCM Relay] HTTP {status}")
        if resp_text:
            print(resp_text)
        if status < 200 or status >= 300:
            sys.exit(1)
        return

    sys.exit(f"错误：不支持的推送模式 {mode}")


if __name__ == "__main__":
    main()
