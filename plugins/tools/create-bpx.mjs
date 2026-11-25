#!/usr/bin/env node
/**
 * BiliPai Extension 2.0 (.bpx v1) 工程脚手架（带完整 TypeScript 类型补全）
 *
 * 用法:
 *   node plugins/tools/create-bpx.mjs <扩展ID> [--slot=detail.companion] [输出目录]
 *
 * 示例:
 *   node plugins/tools/create-bpx.mjs community.demo.quick_note --slot=detail.companion
 */
import fs from 'node:fs'
import path from 'node:path'

const ID_REGEX = /^[a-z0-9]+(\.[a-z0-9_]+){1,4}$/
const SLOTS = ['feed.pipe', 'detail.companion', 'player.track', 'home.lens', 'community.craft', 'theme.skin']

const args = process.argv.slice(2)
const slotArg = args.find((a) => a.startsWith('--slot='))
const positional = args.filter((a) => !a.startsWith('--'))

const extId = positional[0]
const slot = slotArg ? slotArg.split('=')[1] : 'detail.companion'

if (!extId || !ID_REGEX.test(extId)) {
  console.error('用法: node plugins/tools/create-bpx.mjs <反向域名ID> [--slot=detail.companion] [输出目录]')
  console.error('示例: node plugins/tools/create-bpx.mjs community.myname.note_helper --slot=detail.companion')
  process.exit(1)
}
if (!SLOTS.includes(slot)) {
  console.error(`不支持的 --slot="${slot}"，可选值: ${SLOTS.join(' | ')}`)
  process.exit(1)
}

const shortName = extId.split('.').pop()
const outDir = path.resolve(positional[1] || shortName)
if (fs.existsSync(outDir)) {
  console.error(`输出目录已存在: ${outDir}`)
  process.exit(1)
}
fs.mkdirSync(outDir, { recursive: true })

const dts = `// BiliPai Extension 2.0 (.bpx v1) 本地类型定义
declare namespace BiliPai {
  const http: {
    get(url: string, headers?: Record<string, string>): Promise<{ status: number; body: string; headers: Record<string, string> }>;
    post(url: string, body: string, headers?: Record<string, string>): Promise<{ status: number; body: string; headers: Record<string, string> }>;
    streamSse(
      url: string,
      init: { method?: 'GET' | 'POST'; headers?: Record<string, string>; body?: string },
      onEvent: (ev: { event?: string; data: string }) => void | Promise<void>
    ): Promise<void>;
  };
  const ws: {
    connect(url: string, options?: { headers?: Record<string, string> }): Promise<{
      send(payload: string): void;
      onMessage(cb: (payload: string) => void): void;
      close(): void;
    }>;
  };
  const html: {
    parse(markup: string): {
      text: string;
      html: string;
      attr(name: string): string | null;
      select(selector: string): any[];
      selectOne(selector: string): any | null;
    };
  };
  const crypto: {
    md5(text: string): string;
    sha256(text: string): string;
    hmacSha256(key: string, msg: string): string;
    base64Encode(text: string): string;
    base64Decode(b64: string): string;
  };
  const storage: {
    get(key: string): Promise<string | null>;
    set(key: string, value: string): Promise<void>;
    remove(key: string): Promise<void>;
  };
  function log(level: 'info' | 'warn' | 'error', message: string): void;
}
`

const isDeclarative = slot === 'feed.pipe' || slot === 'theme.skin'
const mainFileName = isDeclarative ? `${shortName}.bpx.json` : `${shortName}.bpx.js`

const mainContent =
  slot === 'theme.skin'
    ? JSON.stringify(
        {
          bpxVersion: 1,
          id: extId,
          name: shortName,
          version: '1.0.0',
          description: '三材质自适应光学主题示例',
          license: 'CC-BY-4.0',
          licenseNote: '原创配色方案，采用 CC-BY-4.0 协议允许社区分享',
          author: { name: 'YourName', biliMid: 10000 },
          mounts: [{ slot: 'theme.skin', id: 'skin_main', title: shortName }],
          skin: {
            surfaces: ['HOME_TOP_CHROME', 'HOME_BOTTOM_BAR'],
            tokens: {
              light: { topAtmosphereTint: '#F4F8FD', bottomBarTrimTint: '#EAF2FC', bottomBarSelectedTint: '#3668B0' },
              dark: { topAtmosphereTint: '#161B24', bottomBarTrimTint: '#1B2230', bottomBarSelectedTint: '#7DB0FF' },
              liquidGlass: { surfaceTint: '#BDD8FF', refractionAccent: '#7DB0FF', tintOpacity: 0.2 }
            }
          }
        },
        null,
        2
      ) + '\n'
    : slot === 'feed.pipe'
      ? JSON.stringify(
          {
            bpxVersion: 1,
            id: extId,
            name: shortName,
            version: '1.0.0',
            description: '自定义推荐流净化与角标管线',
            license: 'MIT',
            licenseNote: 'MIT 开源规则包',
            author: { name: 'YourName', biliMid: 10000 },
            mounts: [{ slot: 'feed.pipe', id: 'pipe_main', title: shortName }],
            rules: [
              {
                condition: { field: 'durationSec', op: 'ge', value: 1200 },
                action: 'badge',
                text: '长视频',
                tone: 'accent'
              }
            ]
          },
          null,
          2
        ) + '\n'
      : `/// <reference path="./bpx.d.ts" />

globalThis.BPX = {
  manifest: {
    bpxVersion: 1,
    id: "${extId}",
    name: "${shortName}",
    version: "1.0.0",
    description: "一句话说明你的扩展解决什么痛点",
    license: "MIT",
    licenseNote: "MIT 开源脚本",
    author: {
      name: "YourName",
      biliMid: 10000,
      bio: "BiliPai 社区共创者"
    },
    mounts: [
      { slot: "${slot}", id: "main", title: "${shortName}", entry: "handleSlot" }
    ],
    permissions: {
      videoContext: ["metadata"]
    }
  },
  handlers: {
    async handleSlot(ctx, settings) {
      BiliPai.log("info", "Running ${extId} on " + (ctx?.bvid || "context"));
      return {
        blocks: [
          { type: "markdown", content: "### ${shortName}\\n\\n当前视频：**" + (ctx?.title || "") + "**" }
        ]
      };
    }
  }
};
`

fs.writeFileSync(path.join(outDir, 'bpx.d.ts'), dts, 'utf8')
fs.writeFileSync(path.join(outDir, mainFileName), mainContent, 'utf8')
console.log(`✨ 已创建 BiliPai .bpx 扩展工程: ${outDir}`)
console.log(`   主文件: ${path.join(outDir, mainFileName)}`)
console.log(`   本地体检命令: node plugins/tools/validate-bpx.mjs ${outDir}`)
