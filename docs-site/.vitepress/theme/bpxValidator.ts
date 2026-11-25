export interface ValidationIssue {
  severity: 'error' | 'warning'
  code: string
  message: string
  fixHint: string
}

export interface ParsedBpxSummary {
  format: 'json' | 'js'
  manifest: Record<string, any> | null
  issues: ValidationIssue[]
  rawBytes: number
}

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

const FORBIDDEN_GLOBALS: Array<{ pattern: RegExp; name: string; replacement: string }> = [
  { pattern: /\bfetch\s*\(/, name: 'fetch()', replacement: '请改用 BiliPai.http.get / post / streamSse' },
  { pattern: /\bXMLHttpRequest\b/, name: 'XMLHttpRequest', replacement: '请改用 BiliPai.http.*' },
  { pattern: /\blocalStorage\b/, name: 'localStorage', replacement: '请改用 await BiliPai.storage.get / set' },
  { pattern: /\bdocument\./, name: 'document.*', replacement: '请改用 BiliPai.html.parse(html) 选择器接口' },
  { pattern: /\bwindow\./, name: 'window.*', replacement: 'QuickJS 沙盒无 window，请使用 globalThis.BPX' },
  { pattern: /\beval\s*\(/, name: 'eval()', replacement: '沙盒严禁动态执行不可信代码字符串' },
  { pattern: /\b_0x[0-9a-fA-F]{3,}\b/, name: '混淆标识符 (_0x...)', replacement: '官方精选仓严禁提交混淆压缩代码，请提供明文源码' }
]

function extractBalancedBlock(source: string, startIdx: number): string | null {
  let depth = 0
  let inStr: '"' | "'" | '`' | null = null
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

function jsObjectLiteralToJson(rawObj: string): string {
  let out = ''
  let i = 0
  const n = rawObj.length
  while (i < n) {
    const ch = rawObj[i]
    const next = rawObj[i + 1]
    // Line comment
    if (ch === '/' && next === '/') {
      while (i < n && rawObj[i] !== '\n') i++
      continue
    }
    // Block comment
    if (ch === '/' && next === '*') {
      i += 2
      while (i < n && !(rawObj[i] === '*' && rawObj[i + 1] === '/')) i++
      i += 2
      continue
    }
    // Strings (' or ")
    if (ch === '"' || ch === "'") {
      const quote = ch
      out += '"'
      i++
      while (i < n && rawObj[i] !== quote) {
        if (rawObj[i] === '\\') {
          const esc = rawObj[i + 1]
          if (esc === "'") {
            out += "'"
          } else {
            out += '\\' + (esc ?? '')
          }
          i += 2
          continue
        }
        if (rawObj[i] === '"') {
          out += '\\"'
        } else {
          out += rawObj[i]
        }
        i++
      }
      out += '"'
      i++
      continue
    }
    // Trailing comma before } or ]
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
    // Unquoted identifier key or boolean/null literal
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

/**
 * Converts a clean JS object literal (used inside manifest: { ... }) into strict JSON
 * without calling eval/Function, keeping browser validation 100% safe.
 */
function parseManifestFromJsSource(source: string): { manifest: Record<string, any> | null; error?: string } {
  const match = /manifest\s*:\s*\{/.exec(source) || /export\s+const\s+manifest\s*=\s*\{/.exec(source)
  if (!match) {
    return {
      manifest: null,
      error: '未找到 globalThis.BPX = { manifest: { ... } } 声明'
    }
  }
  const braceStart = source.indexOf('{', match.index)
  const rawObj = extractBalancedBlock(source, braceStart)
  if (!rawObj) {
    return { manifest: null, error: 'manifest 对象花括号未闭合' }
  }
  try {
    return { manifest: JSON.parse(jsObjectLiteralToJson(rawObj)) }
  } catch (e: any) {
    return {
      manifest: null,
      error: `manifest 需为纯静态字面量对象（${e?.message || '解析失败'}）`
    }
  }
}

export function validateBpxSource(rawSource: string): ParsedBpxSummary {
  const trimmed = rawSource.trim()
  const rawBytes = new TextEncoder().encode(trimmed).byteLength
  const issues: ValidationIssue[] = []

  if (!trimmed) {
    issues.push({
      severity: 'error',
      code: 'EMPTY_SOURCE',
      message: '代码为空',
      fixHint: '请粘贴 .bpx.json 或 .bpx.js 源码，或从上方食谱加载模板。'
    })
    return { format: 'json', manifest: null, issues, rawBytes }
  }

  const isJson = trimmed.startsWith('{')
  const format: 'json' | 'js' = isJson ? 'json' : 'js'
  let manifest: Record<string, any> | null = null

  if (isJson) {
    try {
      manifest = JSON.parse(trimmed)
    } catch (e: any) {
      issues.push({
        severity: 'error',
        code: 'INVALID_JSON',
        message: `JSON 语法错误：${e?.message || '无法解析'}`,
        fixHint: '检查是否有多余逗号、未转义反斜杠（正则中需写 \\\\s）或混入了注释。'
      })
      return { format, manifest: null, issues, rawBytes }
    }
  } else {
    for (const rule of FORBIDDEN_GLOBALS) {
      if (rule.pattern.test(trimmed)) {
        issues.push({
          severity: 'error',
          code: 'FORBIDDEN_GLOBAL',
          message: `检测到沙盒禁用接口或混淆特征：${rule.name}`,
          fixHint: rule.replacement
        })
      }
    }
    const longestLine = trimmed.split('\n').reduce((max, l) => Math.max(max, l.length), 0)
    if (longestLine > 450) {
      issues.push({
        severity: 'error',
        code: 'MINIFIED_CODE',
        message: `检测到单行长度 ${longestLine} 字符，疑似压缩混淆代码`,
        fixHint: '官方精选源要求脚本保持未压缩明文可读，请格式化展开代码。'
      })
    }
    const parsed = parseManifestFromJsSource(trimmed)
    if (!parsed.manifest) {
      issues.push({
        severity: 'error',
        code: 'MANIFEST_PARSE_ERROR',
        message: parsed.error || '无法提取 manifest',
        fixHint: '请使用 globalThis.BPX = { manifest: { ... }, handlers: { ... } } 结构，且 manifest 内仅使用静态键值。'
      })
      return { format, manifest: null, issues, rawBytes }
    }
    manifest = parsed.manifest
  }

  if (manifest.bpxVersion !== 1) {
    issues.push({
      severity: 'error',
      code: 'BPX_VERSION',
      message: `bpxVersion 必须为数字 1（当前为 ${JSON.stringify(manifest.bpxVersion)}）`,
      fixHint: '将顶层 "bpxVersion" 设为 1。'
    })
  }

  if (typeof manifest.id !== 'string' || !ID_REGEX.test(manifest.id) || manifest.id.length > 64) {
    issues.push({
      severity: 'error',
      code: 'INVALID_ID',
      message: `扩展 id "${manifest.id ?? ''}" 不符合反向域名规范`,
      fixHint: '请使用全小写字母/数字/下划线的反向域名格式，例如 "community.author.my_ext"。'
    })
  }

  if (typeof manifest.name !== 'string' || !manifest.name.trim()) {
    issues.push({
      severity: 'error',
      code: 'MISSING_NAME',
      message: '缺少扩展名称 name',
      fixHint: '补充 1..32 字的中文或英文显示名称。'
    })
  }

  if (typeof manifest.version !== 'string' || !SEMVER_REGEX.test(manifest.version)) {
    issues.push({
      severity: 'error',
      code: 'INVALID_SEMVER',
      message: `版本号 "${manifest.version ?? ''}" 不是标准 SemVer`,
      fixHint: '请改为类似 "1.0.0" 的语义化版本号。'
    })
  }

  if (!manifest.author || typeof manifest.author.name !== 'string' || !manifest.author.name.trim()) {
    issues.push({
      severity: 'error',
      code: 'MISSING_AUTHOR',
      message: '缺少创作者署名 author.name',
      fixHint: '请填写 author: { name: "你的昵称", biliMid: 你的B站UID } 以点亮创作者名片。'
    })
  } else if (!manifest.author.biliMid && !manifest.author.github) {
    issues.push({
      severity: 'warning',
      code: 'CREATOR_CARD_LINK',
      message: '建议补充 author.biliMid 或 author.github',
      fixHint: '填上 B 站数字 UID 后，App 内创作者名片会自动生成「访问 TA 的 B 站空间」按钮。'
    })
  }

  if (!Array.isArray(manifest.mounts) || manifest.mounts.length === 0) {
    issues.push({
      severity: 'error',
      code: 'EMPTY_MOUNTS',
      message: 'mounts 不能为空，至少需声明一个宿主挂载插槽',
      fixHint: '在 mounts 中声明 feed.pipe / detail.companion / player.track / home.lens / community.craft / theme.skin 之一。'
    })
  } else {
    for (const m of manifest.mounts) {
      if (!ALLOWED_SLOTS.has(m?.slot)) {
        issues.push({
          severity: 'error',
          code: 'UNKNOWN_SLOT',
          message: `未知挂载插槽 "${m?.slot}"`,
          fixHint: `slot 仅允许：${Array.from(ALLOWED_SLOTS).join(' | ')}`
        })
      }
      if (format === 'js' && m?.slot !== 'theme.skin' && (!m?.entry || typeof m.entry !== 'string')) {
        issues.push({
          severity: 'error',
          code: 'MISSING_ENTRY',
          message: `脚本插槽 "${m?.slot}" 缺少 entry 处理函数名`,
          fixHint: '在 mount 项添加 entry（例如 "onOpenSheet"），并在 handlers 中定义同名函数。'
        })
      }
      if (m?.slot === 'theme.skin') {
        if (!manifest.licenseNote || String(manifest.licenseNote).trim().length < 4) {
          issues.push({
            severity: 'error',
            code: 'SKIN_LICENSE_REQUIRED',
            message: '主题装扮扩展必须填写 licenseNote 版权与授权声明',
            fixHint: '补充 licenseNote 说明原创或 CC/二创可再分发来源。'
          })
        }
        const opacity = manifest.skin?.tokens?.liquidGlass?.tintOpacity
        if (typeof opacity === 'number' && (opacity < 0.08 || opacity > 0.45)) {
          issues.push({
            severity: 'error',
            code: 'GLASS_OPACITY_RANGE',
            message: `liquidGlass.tintOpacity (${opacity}) 超出光学安全区间 0.08..0.45`,
            fixHint: '请将 tintOpacity 调整至 0.08..0.45 之间，避免遮蔽液态玻璃实时背景折射。'
          })
        }
      }
    }
  }

  const declaredDomains = new Set<string>(manifest.permissions?.networkDomains || [])
  for (const d of declaredDomains) {
    if (d.includes('*') || /^\d+\.\d+\.\d+\.\d+$/.test(d) || d.includes('/')) {
      issues.push({
        severity: 'error',
        code: 'INVALID_DOMAIN',
        message: `networkDomains 包含非法域名项 "${d}"`,
        fixHint: '必须填写不带协议路径与通配符的精确域名，例如 "api.deepseek.com"。'
      })
    }
  }

  if (format === 'js') {
    const urlMatches = trimmed.matchAll(/https?:\/\/([a-zA-Z0-9.-]+)(?:[:/]|["'`])/g)
    for (const m of urlMatches) {
      const host = m[1]
      if (host && host !== 'github.com' && !declaredDomains.has(host)) {
        issues.push({
          severity: 'error',
          code: 'UNDECLARED_DOMAIN',
          message: `代码中请求了 "${host}"，但未在 permissions.networkDomains 中声明`,
          fixHint: `请将 "${host}" 加入 manifest.permissions.networkDomains 数组。`
        })
      }
    }
  }

  if (Array.isArray(manifest.settings)) {
    for (const s of manifest.settings) {
      if (!s?.key || !ALLOWED_SETTING_TYPES.has(s?.type)) {
        issues.push({
          severity: 'error',
          code: 'INVALID_SETTING',
          message: `设置项 "${s?.key ?? '未知'}" 的 type "${s?.type}" 无效`,
          fixHint: `settings[].type 仅支持：${Array.from(ALLOWED_SETTING_TYPES).join(' | ')}`
        })
      }
    }
  }

  // ReDoS heuristic scan on regex patterns
  const nestedQuantifierRe = /(\([^)]*[+*][^)]*\))\s*[+*]/
  if (nestedQuantifierRe.test(trimmed)) {
    issues.push({
      severity: 'error',
      code: 'REDOS_RISK',
      message: '检测到嵌套量词正则（存在灾难性回溯卡顿风险）',
      fixHint: '请改用具体字符类限定长度（例如 [^，。]{1,12}），避免 (a+)+ 结构。'
    })
  }

  return { format, manifest, issues, rawBytes }
}

export function buildAiFixPrompt(source: string, issues: ValidationIssue[]): string {
  const problemList = issues
    .map((it, idx) => `${idx + 1}. [${it.code}] ${it.message}\n   修复要求：${it.fixHint}`)
    .join('\n')

  return `我把你生成的 BiliPai 扩展代码放进了官方 .bpx v1 校验台，发现了以下问题，请按修复要求直接输出修正后的完整单文件代码（不要省略任何部分）：

## 校验器报错清单
${problemList}

## 当前待修复代码
\`\`\`
${source}
\`\`\``
}

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export async function encodeBpxOfflineUri(
  source: string,
  format: 'json' | 'js'
): Promise<{ uri: string; compressedBytes: number; withinQrBudget: boolean }> {
  const inputBytes = new TextEncoder().encode(source.trim())
  let compressed = inputBytes
  let codec = 'raw'

  if (typeof CompressionStream !== 'undefined') {
    const cs = new CompressionStream('deflate-raw')
    const writer = cs.writable.getWriter()
    writer.write(inputBytes)
    writer.close()
    const buf = await new Response(cs.readable).arrayBuffer()
    compressed = new Uint8Array(buf)
    codec = 'deflate'
  }

  const payload = bytesToBase64Url(compressed)
  const uri = `bilipai://ext/preview?v=1&f=${format}&c=${codec}&payload=${payload}`
  return {
    uri,
    compressedBytes: compressed.byteLength,
    withinQrBudget: compressed.byteLength <= 2048
  }
}
