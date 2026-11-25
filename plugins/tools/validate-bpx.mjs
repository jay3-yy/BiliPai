#!/usr/bin/env node
/**
 * BiliPai Extension 2.0 (.bpx v1) 零依赖静态合规体检工具
 *
 * 用法:
 *   node plugins/tools/validate-bpx.mjs <文件或目录> [--json]
 */
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import zlib from 'node:zlib'

const ALLOWED_SLOTS = new Set([
  'feed.pipe',
  'detail.companion',
  'player.track',
  'home.lens',
  'community.craft',
  'theme.skin'
])

const ALLOWED_SETTING_TYPES = new Set([
  'switch',
  'segment',
  'select',
  'slider',
  'text',
  'secret',
  'stringList'
])

const ID_REGEX = /^[a-z0-9]+(\.[a-z0-9_]+){1,4}$/
const SEMVER_REGEX = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/

const FORBIDDEN_GLOBALS = [
  { pattern: /\bfetch\s*\(/, name: 'fetch()', hint: '请改用 BiliPai.http.get / post / streamSse' },
  { pattern: /\bXMLHttpRequest\b/, name: 'XMLHttpRequest', hint: '请改用 BiliPai.http.*' },
  { pattern: /\blocalStorage\b/, name: 'localStorage', hint: '请改用 await BiliPai.storage.get / set' },
  { pattern: /\bdocument\./, name: 'document.*', hint: '请改用 BiliPai.html.parse(html)' },
  { pattern: /\bwindow\./, name: 'window.*', hint: 'QuickJS 沙盒无 window，请使用 globalThis.BPX' },
  { pattern: /\beval\s*\(/, name: 'eval()', hint: '沙盒严禁动态执行不可信字符串' },
  { pattern: /\b_0x[0-9a-fA-F]{3,}\b/, name: '混淆标识符 (_0x...)', hint: '官方精选源严禁提交混淆压缩代码' }
]

function extractBalancedBlock(source, startIdx) {
  let depth = 0
  let inStr = null
  let escaped = false
  for (let i = startIdx; i < source.length; i++) {
    const ch = source[i]
    if (escaped) {
      escaped = false
      continue
    }
    if (ch === '\\') {
      escaped = true
      continue
    }
    if (inStr) {
      if (ch === inStr) inStr = null
      continue
    }
    if (ch === '"' || ch === "'" || ch === '`') {
      inStr = ch
      continue
    }
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) return source.slice(startIdx, i + 1)
    }
  }
  return null
}

function jsObjectLiteralToJson(rawObj) {
  let out = ''
  let i = 0
  const n = rawObj.length
  while (i < n) {
    const ch = rawObj[i]
    const next = rawObj[i + 1]
    if (ch === '/' && next === '/') {
      while (i < n && rawObj[i] !== '\n') i++
      continue
    }
    if (ch === '/' && next === '*') {
      i += 2
      while (i < n && !(rawObj[i] === '*' && rawObj[i + 1] === '/')) i++
      i += 2
      continue
    }
    if (ch === '"' || ch === "'") {
      const quote = ch
      out += '"'
      i++
      while (i < n && rawObj[i] !== quote) {
        if (rawObj[i] === '\\') {
          const esc = rawObj[i + 1]
          out += esc === "'" ? "'" : '\\' + (esc ?? '')
          i += 2
          continue
        }
        out += rawObj[i] === '"' ? '\\"' : rawObj[i]
        i++
      }
      out += '"'
      i++
      continue
    }
    if (ch === ',') {
      let j = i + 1
      while (j < n && /\s/.test(rawObj[j])) j++
      if (rawObj[j] === '}' || rawObj[j] === ']') {
        i++
        continue
      }
      out += ','
      i++
      continue
    }
    if (/[A-Za-z_$]/.test(ch)) {
      let j = i + 1
      while (j < n && /[A-Za-z0-9_$]/.test(rawObj[j])) j++
      const ident = rawObj.slice(i, j)
      let k = j
      while (k < n && /\s/.test(rawObj[k])) k++
      if (rawObj[k] === ':') {
        out += `"${ident}":`
        i = k + 1
        continue
      }
      out += ident
      i = j
      continue
    }
    out += ch
    i++
  }
  return out
}

function parseManifestFromJsSource(source) {
  const match =
    /manifest\s*:\s*\{/.exec(source) || /export\s+const\s+manifest\s*=\s*\{/.exec(source)
  if (!match) {
    return { manifest: null, error: '未找到 globalThis.BPX = { manifest: { ... } } 声明' }
  }
  const braceStart = source.indexOf('{', match.index)
  const rawObj = extractBalancedBlock(source, braceStart)
  if (!rawObj) return { manifest: null, error: 'manifest 对象花括号未闭合' }

  try {
    return { manifest: JSON.parse(jsObjectLiteralToJson(rawObj)) }
  } catch (e) {
    return { manifest: null, error: `manifest 需为纯静态字面量对象（${e.message}）` }
  }
}

export function validateBpxContent(rawSource, fileLabel = '<stdin>') {
  const trimmed = rawSource.trim()
  const issues = []
  const isJson = trimmed.startsWith('{')
  const format = isJson ? 'json' : 'js'
  let manifest = null

  if (!trimmed) {
    issues.push({ severity: 'error', code: 'EMPTY_SOURCE', message: '文件内容为空', hint: '请提供有效 .bpx 源码' })
    return { file: fileLabel, ok: false, format, manifest, issues }
  }

  if (isJson) {
    try {
      manifest = JSON.parse(trimmed)
    } catch (e) {
      issues.push({ severity: 'error', code: 'INVALID_JSON', message: `JSON 解析失败: ${e.message}`, hint: '请检查 JSON 语法与转义字符' })
      return { file: fileLabel, ok: false, format, manifest: null, issues }
    }
  } else {
    for (const r of FORBIDDEN_GLOBALS) {
      if (r.pattern.test(trimmed)) {
        issues.push({ severity: 'error', code: 'FORBIDDEN_GLOBAL', message: `命中禁用接口或混淆特征: ${r.name}`, hint: r.hint })
      }
    }
    const longestLine = trimmed.split('\n').reduce((m, l) => Math.max(m, l.length), 0)
    if (longestLine > 450) {
      issues.push({ severity: 'error', code: 'MINIFIED_CODE', message: `单行达 ${longestLine} 字符，疑似压缩混淆`, hint: '请保持明文可读格式' })
    }
    const parsed = parseManifestFromJsSource(trimmed)
    if (!parsed.manifest) {
      issues.push({ severity: 'error', code: 'MANIFEST_PARSE_ERROR', message: parsed.error, hint: '请检查 globalThis.BPX.manifest 结构' })
      return { file: fileLabel, ok: false, format, manifest: null, issues }
    }
    manifest = parsed.manifest
  }

  if (manifest.bpxVersion !== 1) {
    issues.push({ severity: 'error', code: 'BPX_VERSION', message: `bpxVersion 必须为 1`, hint: '设置 "bpxVersion": 1' })
  }
  if (typeof manifest.id !== 'string' || !ID_REGEX.test(manifest.id) || manifest.id.length > 64) {
    issues.push({ severity: 'error', code: 'INVALID_ID', message: `id "${manifest.id ?? ''}" 格式无效`, hint: '使用小写反向域名如 community.author.ext_name' })
  }
  if (typeof manifest.name !== 'string' || !manifest.name.trim()) {
    issues.push({ severity: 'error', code: 'MISSING_NAME', message: '缺少 name', hint: '补充扩展显示名称' })
  }
  if (typeof manifest.version !== 'string' || !SEMVER_REGEX.test(manifest.version)) {
    issues.push({ severity: 'error', code: 'INVALID_SEMVER', message: `version "${manifest.version ?? ''}" 非标准 SemVer`, hint: '改为 1.0.0 形式' })
  }
  if (!manifest.author || typeof manifest.author.name !== 'string' || !manifest.author.name.trim()) {
    issues.push({ severity: 'error', code: 'MISSING_AUTHOR', message: '缺少 author.name', hint: '填写创作者署名信息' })
  }
  if (!Array.isArray(manifest.mounts) || manifest.mounts.length === 0) {
    issues.push({ severity: 'error', code: 'EMPTY_MOUNTS', message: 'mounts 不能为空', hint: '至少声明 1 个标准插槽' })
  } else {
    for (const m of manifest.mounts) {
      if (!ALLOWED_SLOTS.has(m?.slot)) {
        issues.push({ severity: 'error', code: 'UNKNOWN_SLOT', message: `未知插槽 "${m?.slot}"`, hint: `允许值: ${Array.from(ALLOWED_SLOTS).join(', ')}` })
      }
      if (format === 'js' && m?.slot !== 'theme.skin' && (!m?.entry || typeof m.entry !== 'string')) {
        issues.push({ severity: 'error', code: 'MISSING_ENTRY', message: `脚本插槽 "${m?.slot}" 缺少 entry`, hint: '声明处理函数名并在 handlers 中实现' })
      }
      if (m?.slot === 'theme.skin') {
        if (!manifest.licenseNote || String(manifest.licenseNote).trim().length < 4) {
          issues.push({ severity: 'error', code: 'SKIN_LICENSE_REQUIRED', message: '主题装扮缺少 licenseNote 授权说明', hint: '补充原创或可再分发授权声明' })
        }
        const op = manifest.skin?.tokens?.liquidGlass?.tintOpacity
        if (typeof op === 'number' && (op < 0.08 || op > 0.45)) {
          issues.push({ severity: 'error', code: 'GLASS_OPACITY_RANGE', message: `liquidGlass.tintOpacity (${op}) 超出 0.08..0.45`, hint: '调整至光学安全区间' })
        }
      }
    }
  }

  const declaredDomains = new Set(manifest.permissions?.networkDomains || [])
  for (const d of declaredDomains) {
    if (d.includes('*') || /^\d+\.\d+\.\d+\.\d+$/.test(d) || d.includes('/')) {
      issues.push({ severity: 'error', code: 'INVALID_DOMAIN', message: `非法域名声明 "${d}"`, hint: '仅允许精确域名，禁止通配符或路径' })
    }
  }
  if (format === 'js') {
    for (const m of trimmed.matchAll(/https?:\/\/([a-zA-Z0-9.-]+)(?:[:/]|["'`])/g)) {
      const host = m[1]
      if (host && host !== 'github.com' && !declaredDomains.has(host)) {
        issues.push({ severity: 'error', code: 'UNDECLARED_DOMAIN', message: `请求了未声明域名 "${host}"`, hint: `将 "${host}" 加入 permissions.networkDomains` })
      }
    }
  }
  if (Array.isArray(manifest.settings)) {
    for (const s of manifest.settings) {
      if (!s?.key || !ALLOWED_SETTING_TYPES.has(s?.type)) {
        issues.push({ severity: 'error', code: 'INVALID_SETTING', message: `设置项 "${s?.key}" 类型 "${s?.type}" 无效`, hint: '使用标准 Schema 控件类型' })
      }
    }
  }
  if (/(\([^)]*[+*][^)]*\))\s*[+*]/.test(trimmed)) {
    issues.push({ severity: 'error', code: 'REDOS_RISK', message: '检测到嵌套量词高危回溯正则', hint: '改用有界字符类避免卡死线程' })
  }

  const sha256 = crypto.createHash('sha256').update(trimmed, 'utf8').digest('hex')
  const deflated = zlib.deflateRawSync(Buffer.from(trimmed, 'utf8'))
  const payload = deflated.toString('base64url')
  const previewUri = `bilipai://ext/preview?v=1&f=${format}&c=deflate&payload=${payload}`
  const errors = issues.filter((i) => i.severity === 'error')

  return {
    file: fileLabel,
    ok: errors.length === 0,
    format,
    manifest,
    sha256,
    rawBytes: Buffer.byteLength(trimmed, 'utf8'),
    compressedBytes: deflated.byteLength,
    withinQrBudget: deflated.byteLength <= 2048,
    previewUri: deflated.byteLength <= 2048 ? previewUri : null,
    issues
  }
}

function collectTargets(targetPath) {
  const stat = fs.statSync(targetPath)
  if (stat.isFile()) return [targetPath]
  const out = []
  for (const entry of fs.readdirSync(targetPath, { withFileTypes: true })) {
    const full = path.join(targetPath, entry.name)
    if (entry.isDirectory()) out.push(...collectTargets(full))
    else if (entry.name.endsWith('.bpx.json') || entry.name.endsWith('.bpx.js')) out.push(full)
  }
  return out.sort()
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const args = process.argv.slice(2)
  const jsonOutput = args.includes('--json')
  const targetArg = args.find((a) => !a.startsWith('--'))

  if (!targetArg) {
    console.error('用法: node plugins/tools/validate-bpx.mjs <文件或目录> [--json]')
    process.exit(2)
  }

  const files = collectTargets(path.resolve(targetArg))
  const results = files.map((f) => validateBpxContent(fs.readFileSync(f, 'utf8'), path.relative(process.cwd(), f)))
  const allOk = results.length > 0 && results.every((r) => r.ok)

  if (jsonOutput) {
    console.log(JSON.stringify({ ok: allOk, count: results.length, results }, null, 2))
  } else {
    for (const r of results) {
      const badge = r.ok ? '✅ PASS' : '❌ FAIL'
      console.log(`${badge}  ${r.file}  (${r.manifest?.id || 'unknown'} · 压缩后 ${r.compressedBytes ?? '-'} B)`)
      for (const it of r.issues) {
        console.log(`   [${it.severity.toUpperCase()}:${it.code}] ${it.message} -> ${it.hint}`)
      }
    }
    console.log(`\n共体检 ${results.length} 个扩展包，结果：${allOk ? '全部通过' : '存在未通过项'}`)
  }

  process.exit(allOk ? 0 : 1)
}
