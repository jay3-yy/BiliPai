<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import QRCode from 'qrcode'
import {
  SLOT_META,
  STARTER_RECIPES,
  buildCapsuleAiPrompt,
  type BpxSlotId
} from '../bpxRecipes'
import {
  buildAiFixPrompt,
  encodeBpxOfflineUri,
  validateBpxSource
} from '../bpxValidator'

const selectedSlot = ref<BpxSlotId>('feed.pipe')
const userGoal = ref('')
const promptCopied = ref(false)
const fixPromptCopied = ref(false)
const uriCopied = ref(false)

const activeRecipeId = ref(STARTER_RECIPES[0].id)
const codeInput = ref(STARTER_RECIPES[0].code)

const qrSvg = ref<string>('')
const qrUri = ref<string>('')
const compressedBytes = ref<number>(0)
const withinQrBudget = ref<boolean>(true)

const validation = computed(() => validateBpxSource(codeInput.value))
const errorCount = computed(() => validation.value.issues.filter((i) => i.severity === 'error').length)

function selectRecipe(id: string) {
  const found = STARTER_RECIPES.find((r) => r.id === id)
  if (!found) return
  activeRecipeId.value = found.id
  selectedSlot.value = found.slot
  codeInput.value = found.code
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    const params = new URLSearchParams(window.location.search)
    const recipeParam = params.get('recipe')
    if (recipeParam) selectRecipe(recipeParam)
  }
  refreshQr()
})

async function refreshQr() {
  if (typeof window === 'undefined') return
  const res = validation.value
  if (errorCount.value > 0 || !res.manifest) {
    qrSvg.value = ''
    qrUri.value = ''
    return
  }
  try {
    const encoded = await encodeBpxOfflineUri(codeInput.value, res.format)
    qrUri.value = encoded.uri
    compressedBytes.value = encoded.compressedBytes
    withinQrBudget.value = encoded.withinQrBudget
    if (encoded.withinQrBudget) {
      qrSvg.value = await QRCode.toString(encoded.uri, {
        type: 'svg',
        margin: 1,
        errorCorrectionLevel: 'L',
        color: { dark: '#1e1e26', light: '#ffffff' }
      })
    } else {
      qrSvg.value = ''
    }
  } catch {
    qrSvg.value = ''
  }
}

watch(codeInput, () => {
  refreshQr()
})

async function copyCapsulePrompt() {
  const text = buildCapsuleAiPrompt(selectedSlot.value, userGoal.value)
  await navigator.clipboard.writeText(text)
  promptCopied.value = true
  setTimeout(() => (promptCopied.value = false), 2200)
}

async function copyFixPrompt() {
  const text = buildAiFixPrompt(codeInput.value, validation.value.issues)
  await navigator.clipboard.writeText(text)
  fixPromptCopied.value = true
  setTimeout(() => (fixPromptCopied.value = false), 2200)
}

async function copyDeepLink() {
  if (!qrUri.value) return
  await navigator.clipboard.writeText(qrUri.value)
  uriCopied.value = true
  setTimeout(() => (uriCopied.value = false), 2000)
}

function downloadBpxFile() {
  const res = validation.value
  const id = res.manifest?.id || 'custom_extension'
  const ext = res.format === 'json' ? 'bpx.json' : 'bpx.js'
  const blob = new Blob([codeInput.value], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${id}.${ext}`
  a.click()
  URL.revokeObjectURL(url)
}

const submitIssueUrl = computed(() => {
  const m = validation.value.manifest
  const base = 'https://github.com/jay3-yy/BiliPai/issues/new?template=submit-extension.yml'
  if (!m) return base
  const title = encodeURIComponent(`[BPX 投稿] ${m.name} (${m.id})`)
  return `${base}&title=${title}`
})
</script>

<template>
  <div class="bpx-studio">
    <!-- 1. AI Capsule Prompt Generator -->
    <section class="bpx-glass-card bpx-prompt-box">
      <div class="bpx-section-head">
        <span class="bpx-pill">Step 1 · AI 胶囊配方生成器</span>
        <span class="bpx-muted">自动注入插槽契约与防幻觉规则，无需自己整理文档</span>
      </div>

      <div class="bpx-slot-chips">
        <button
          v-for="(meta, slotKey) in SLOT_META"
          :key="slotKey"
          type="button"
          class="bpx-chip"
          :class="{ active: selectedSlot === slotKey }"
          @click="selectedSlot = slotKey as BpxSlotId"
        >
          {{ meta.label }}
        </button>
      </div>

      <div class="bpx-goal-row">
        <input
          v-model="userGoal"
          type="text"
          class="bpx-goal-input"
          placeholder="用大白话写下你想做的效果（例：屏蔽含抽奖字样的评论，并置顶带分秒指路的热评）..."
        />
        <button type="button" class="bpx-action-btn primary" @click="copyCapsulePrompt">
          {{ promptCopied ? '✓ 已复制！去发给 DeepSeek / Claude' : '🤖 复制完整 AI 提示词' }}
        </button>
      </div>
    </section>

    <!-- 2. Split Editor & Live Verifier / QR Studio -->
    <div class="bpx-workbench">
      <!-- Left: Editor -->
      <section class="bpx-editor-pane">
        <div class="bpx-toolbar">
          <label class="bpx-select-label">
            切换食谱样板：
            <select :value="activeRecipeId" class="bpx-recipe-select" @change="selectRecipe(($event.target as HTMLSelectElement).value)">
              <option v-for="r in STARTER_RECIPES" :key="r.id" :value="r.id">
                {{ r.title }} (.bpx.{{ r.format }})
              </option>
            </select>
          </label>
          <span class="bpx-byte-tag">源码 {{ validation.rawBytes }} B</span>
        </div>

        <textarea
          v-model="codeInput"
          class="bpx-code-area"
          spellcheck="false"
          placeholder="在此粘贴 AI 生成的 .bpx.json 或 .bpx.js 代码..."
        />

        <div class="bpx-editor-footer">
          <button type="button" class="bpx-action-btn ghost" @click="downloadBpxFile">
            ⬇ 导出 .bpx.{{ validation.format }} 文件
          </button>
          <a
            v-if="errorCount === 0 && validation.manifest"
            :href="submitIssueUrl"
            target="_blank"
            rel="noreferrer"
            class="bpx-action-btn ghost"
          >
            🚀 零 Git 填表投稿至官方源 →
          </a>
        </div>
      </section>

      <!-- Right: Compliance Report + Trust Sheet Preview + Offline QR -->
      <section class="bpx-preview-pane">
        <!-- Validation Status -->
        <div class="bpx-status-card" :class="{ ok: errorCount === 0, err: errorCount > 0 }">
          <div class="bpx-status-header">
            <strong v-if="errorCount === 0">✅ 契约与三条红线体检通过</strong>
            <strong v-else>⚠️ 发现 {{ errorCount }} 处需修正项</strong>

            <button
              v-if="validation.issues.length > 0"
              type="button"
              class="bpx-fix-btn"
              @click="copyFixPrompt"
            >
              {{ fixPromptCopied ? '✓ 已复制修复指令' : '📋 复制 AI 修复指令' }}
            </button>
          </div>

          <ul v-if="validation.issues.length > 0" class="bpx-issue-list">
            <li v-for="(it, idx) in validation.issues" :key="idx" :class="it.severity">
              <div class="issue-msg">[{{ it.code }}] {{ it.message }}</div>
              <div class="issue-hint">建议：{{ it.fixHint }}</div>
            </li>
          </ul>
          <p v-else class="bpx-ok-note">
            未检测到禁用浏览器对象、通配域名、混淆代码或高危回溯正则。
          </p>
        </div>

        <!-- Simulated Trust Sheet & Creator Card -->
        <div v-if="validation.manifest" class="bpx-glass-card bpx-trust-sheet">
          <div class="trust-header">
            <div>
              <div class="trust-title">{{ validation.manifest.name }}</div>
              <div class="trust-sub">{{ validation.manifest.id }} · v{{ validation.manifest.version }}</div>
            </div>
            <span class="bpx-pill">Trust Sheet 预览</span>
          </div>

          <p class="trust-desc">{{ validation.manifest.description }}</p>

          <!-- Creator Card -->
          <div v-if="validation.manifest.author" class="creator-card">
            <div class="creator-avatar">
              {{ String(validation.manifest.author.name || 'B').slice(0, 1) }}
            </div>
            <div class="creator-info">
              <div class="creator-name">{{ validation.manifest.author.name }}</div>
              <div class="creator-bio">
                {{ validation.manifest.author.bio || '在 App 内展示创作者名片与点亮感谢' }}
              </div>
            </div>
            <span v-if="validation.manifest.author.biliMid" class="bili-badge">
              UID: {{ validation.manifest.author.biliMid }} · 直达空间
            </span>
          </div>

          <!-- Mount Locations -->
          <div class="trust-block">
            <div class="trust-label">安装后原位出现在：</div>
            <div class="trust-tags">
              <span v-for="m in validation.manifest.mounts" :key="m.id" class="mount-badge">
                📍 {{ SLOT_META[m.slot as BpxSlotId]?.mountLocation || m.slot }}（{{ m.title }}）
              </span>
            </div>
          </div>

          <!-- Granular Permissions -->
          <div class="trust-block">
            <div class="trust-label">最小权限核验（绝不接触 B 站登录 Cookie）：</div>
            <div class="trust-tags">
              <template v-if="validation.manifest.permissions?.networkDomains?.length">
                <span v-for="d in validation.manifest.permissions.networkDomains" :key="d" class="perm-badge">
                  🌐 仅连 {{ d }}
                </span>
              </template>
              <span v-else class="perm-badge safe">🔒 纯本地运行（0 外部联网）</span>
            </div>
          </div>
        </div>

        <!-- Zero-Backend Deflate QR -->
        <div v-if="errorCount === 0 && validation.manifest" class="bpx-glass-card bpx-qr-card">
          <div v-if="withinQrBudget && qrSvg" class="bpx-qr-inner">
            <div class="bpx-qr-svg" v-html="qrSvg" />
            <div class="bpx-qr-meta">
              <div class="qr-title">📱 手机相机 / BiliPai 扫码秒装</div>
              <p class="qr-desc">
                已通过 <code>deflate-raw</code> 离线编码进 <code>bilipai://</code> 唤起链（压缩后仅
                <strong>{{ compressedBytes }} Bytes</strong> / 上限 2048 B），无需上传任何网盘或服务器。
              </p>
              <button type="button" class="bpx-action-btn ghost small" @click="copyDeepLink">
                {{ uriCopied ? '✓ 已复制唤起链接' : '🔗 复制 bilipai:// 唤起口令' }}
              </button>
            </div>
          </div>
          <div v-else class="qr-over-budget">
            <strong>📦 压缩后体积为 {{ compressedBytes }} Bytes（超过 2KB 离线二维码密度上限）</strong>
            <p>请点击左侧「导出 .bpx 文件」发送至手机安装，或使用本地 CLI 局域网扫码直传。</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.bpx-studio {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 16px;
}

.bpx-prompt-box {
  padding: 20px;
}

.bpx-section-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.bpx-muted {
  font-size: 12.5px;
  color: var(--vp-c-text-2);
}

.bpx-slot-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.bpx-chip {
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12.5px;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-divider);
  color: var(--vp-c-text-2);
  transition: all 0.15s ease;
}

.bpx-chip.active {
  background: var(--vp-c-brand-soft);
  border-color: var(--vp-c-brand-1);
  color: var(--vp-c-brand-1);
  font-weight: 600;
}

.bpx-goal-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.bpx-goal-input {
  flex: 1;
  min-width: 240px;
  padding: 9px 14px;
  border-radius: 12px;
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg);
  color: var(--vp-c-text-1);
  font-size: 13.5px;
}

.bpx-workbench {
  display: grid;
  grid-template-columns: 1.12fr 0.88fr;
  gap: 20px;
  align-items: start;
}

@media (max-width: 960px) {
  .bpx-workbench {
    grid-template-columns: 1fr;
  }
}

.bpx-editor-pane {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bpx-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.bpx-select-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--vp-c-text-2);
}

.bpx-recipe-select {
  margin-left: 6px;
  padding: 5px 10px;
  border-radius: 8px;
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-1);
  font-size: 13px;
}

.bpx-byte-tag {
  font-size: 12px;
  font-family: var(--vp-font-family-mono);
  color: var(--vp-c-text-3);
}

.bpx-code-area {
  width: 100%;
  min-height: 460px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-alt);
  color: var(--vp-c-text-1);
  font-family: var(--vp-font-family-mono);
  font-size: 12.5px;
  line-height: 1.55;
  resize: vertical;
}

.bpx-editor-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.bpx-action-btn {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none !important;
}

.bpx-action-btn.primary {
  background: var(--vp-c-brand-1);
  color: #fff !important;
  border: none;
}

.bpx-action-btn.ghost {
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-1) !important;
  border: 1px solid var(--vp-c-divider);
}

.bpx-action-btn.small {
  padding: 5px 12px;
  font-size: 12px;
}

.bpx-preview-pane {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bpx-status-card {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--vp-c-divider);
}

.bpx-status-card.ok {
  background: rgba(34, 197, 94, 0.08);
  border-color: rgba(34, 197, 94, 0.3);
}

.bpx-status-card.err {
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.35);
}

.bpx-status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.bpx-fix-btn {
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: var(--vp-c-bg);
  border: 1px solid var(--vp-c-divider);
}

.bpx-ok-note {
  margin: 6px 0 0 !important;
  font-size: 12.5px;
  color: var(--vp-c-text-2);
}

.bpx-issue-list {
  margin: 10px 0 0 !important;
  padding-left: 16px !important;
  font-size: 12.5px;
}

.issue-msg {
  font-weight: 600;
}

.issue-hint {
  color: var(--vp-c-text-2);
  font-size: 12px;
}

.bpx-trust-sheet {
  padding: 18px;
}

.trust-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.trust-title {
  font-size: 16px;
  font-weight: 700;
}

.trust-sub {
  font-size: 12px;
  color: var(--vp-c-text-3);
  font-family: var(--vp-font-family-mono);
}

.trust-desc {
  margin: 8px 0 12px !important;
  font-size: 13px;
  color: var(--vp-c-text-2);
}

.creator-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: var(--vp-c-bg-soft);
  margin-bottom: 12px;
}

.creator-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.creator-info {
  flex: 1;
  min-width: 0;
}

.creator-name {
  font-size: 13px;
  font-weight: 700;
}

.creator-bio {
  font-size: 11.5px;
  color: var(--vp-c-text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bili-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
}

.trust-block {
  margin-top: 10px;
}

.trust-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--vp-c-text-2);
  margin-bottom: 6px;
}

.trust-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mount-badge,
.perm-badge {
  font-size: 11.5px;
  padding: 3px 9px;
  border-radius: 8px;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-divider);
}

.perm-badge.safe {
  color: #16a34a;
}

.bpx-qr-card {
  padding: 16px;
}

.bpx-qr-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.bpx-qr-svg {
  width: 128px;
  height: 128px;
  flex-shrink: 0;
  padding: 6px;
  border-radius: 12px;
  background: #fff;
}

.bpx-qr-svg :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

.qr-title {
  font-size: 14px;
  font-weight: 700;
}

.qr-desc {
  margin: 6px 0 10px !important;
  font-size: 12px;
  line-height: 1.5;
  color: var(--vp-c-text-2);
}

.qr-over-budget p {
  margin: 6px 0 0 !important;
  font-size: 12.5px;
  color: var(--vp-c-text-2);
}
</style>
