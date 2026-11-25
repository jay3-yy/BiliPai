<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, withBase } from 'vitepress'
import {
  SLOT_META,
  STARTER_RECIPES,
  buildCapsuleAiPrompt,
  type BpxRecipe,
  type BpxSlotId
} from '../bpxRecipes'

const router = useRouter()
const copiedId = ref<string | null>(null)
const selectedSlot = ref<'all' | BpxSlotId>('all')

const slotAccents: Record<BpxSlotId, { fg: string; bg: string }> = {
  'feed.pipe': { fg: '#e05288', bg: 'rgba(224, 82, 136, 0.13)' },
  'detail.companion': { fg: '#6366f1', bg: 'rgba(99, 102, 241, 0.13)' },
  'player.track': { fg: '#0ea5e9', bg: 'rgba(14, 165, 233, 0.13)' },
  'home.lens': { fg: '#10b981', bg: 'rgba(16, 185, 129, 0.13)' },
  'community.craft': { fg: '#f59e0b', bg: 'rgba(245, 158, 11, 0.14)' },
  'theme.skin': { fg: '#ec4899', bg: 'rgba(236, 72, 153, 0.13)' },
}

const slotFilters = computed(() => [
  { id: 'all' as const, label: `全部食谱 (${STARTER_RECIPES.length})` },
  ...(Object.keys(SLOT_META) as BpxSlotId[]).map((slot) => ({
    id: slot,
    label: SLOT_META[slot].label
  }))
])

const visibleRecipes = computed(() =>
  selectedSlot.value === 'all'
    ? STARTER_RECIPES
    : STARTER_RECIPES.filter((r) => r.slot === selectedSlot.value)
)

function openInPlayground(recipe: BpxRecipe) {
  router.go(withBase(`/playground?recipe=${encodeURIComponent(recipe.id)}`))
}

async function copyAiRemixPrompt(recipe: BpxRecipe) {
  const prompt = buildCapsuleAiPrompt(
    recipe.slot,
    `请参考《${recipe.title}》的结构，帮我改写并定制一个新的版本（例如调整触发条件、提示词或配色）：`
  )
  await navigator.clipboard.writeText(prompt)
  copiedId.value = recipe.id
  setTimeout(() => {
    if (copiedId.value === recipe.id) copiedId.value = null
  }, 2000)
}
</script>

<template>
  <div class="bpx-cookbook-wrap">
    <div class="bpx-filter-bar" role="tablist" aria-label="按宿主插槽筛选食谱">
      <button
        v-for="item in slotFilters"
        :key="item.id"
        type="button"
        role="tab"
        :aria-selected="selectedSlot === item.id"
        class="bpx-filter-pill"
        :class="{ active: selectedSlot === item.id }"
        @click="selectedSlot = item.id"
      >
        <span
          v-if="item.id !== 'all'"
          class="bpx-filter-dot"
          :style="{ background: slotAccents[item.id].fg }"
        />
        {{ item.label }}
      </button>
    </div>

    <div class="bpx-cookbook-grid">
      <article v-for="recipe in visibleRecipes" :key="recipe.id" class="bpx-glass-card bpx-recipe-card">
        <div class="bpx-recipe-top">
          <span
            class="bpx-slot-pill"
            :style="{
              color: slotAccents[recipe.slot].fg,
              background: slotAccents[recipe.slot].bg
            }"
          >
            {{ recipe.slotLabel }}
          </span>
          <span class="bpx-lines-badge">{{ recipe.lines }} 行 · .bpx.{{ recipe.format }}</span>
        </div>

        <h3 class="bpx-recipe-title">{{ recipe.title }}</h3>
        <p class="bpx-recipe-summary">{{ recipe.summary }}</p>

        <div class="bpx-mount-tag">
          <span class="bpx-mount-label">挂载位</span>
          <span class="bpx-mount-val">{{ recipe.mountPreview }}</span>
        </div>

        <ul class="bpx-highlights">
          <li v-for="h in recipe.highlights" :key="h">
            <span class="hl-dot" :style="{ background: slotAccents[recipe.slot].fg }" />
            {{ h }}
          </li>
        </ul>

        <div class="bpx-recipe-actions">
          <button type="button" class="bpx-btn bpx-btn-primary" @click="openInPlayground(recipe)">
            在工坊打开
          </button>
          <button type="button" class="bpx-btn bpx-btn-ghost" @click="copyAiRemixPrompt(recipe)">
            {{ copiedId === recipe.id ? '已复制模板' : '复制 AI 模板' }}
          </button>
          <a :href="withBase(recipe.docLink)" class="bpx-detail-link">详解 →</a>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.bpx-cookbook-wrap {
  margin-top: 8px;
}

.bpx-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.bpx-filter-pill {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--vp-c-text-2);
  background: var(--bpx-glass-bg);
  border: 1px solid var(--bpx-glass-border);
  transition: all 0.18s ease;
}

.bpx-filter-pill:hover {
  color: var(--vp-c-text-1);
  border-color: rgba(224, 82, 136, 0.36);
}

.bpx-filter-pill.active {
  background: var(--vp-c-brand-soft);
  border-color: var(--vp-c-brand-1);
  color: var(--vp-c-brand-1);
}

.bpx-filter-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.bpx-cookbook-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(332px, 1fr));
  gap: 20px;
}

.bpx-recipe-card {
  display: flex;
  flex-direction: column;
  padding: 22px;
}

.bpx-recipe-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.bpx-slot-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
}

.bpx-lines-badge {
  font-size: 11.5px;
  padding: 3px 8px;
  border-radius: 8px;
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-3);
  font-family: var(--vp-font-family-mono);
}

.bpx-recipe-title {
  margin: 14px 0 8px !important;
  font-size: 18px !important;
  line-height: 1.35 !important;
  letter-spacing: -0.015em;
  border: none !important;
  padding: 0 !important;
}

.bpx-recipe-summary {
  margin: 0 0 14px !important;
  font-size: 13.5px;
  line-height: 1.62;
  color: var(--vp-c-text-2);
  flex: 1;
}

.bpx-mount-tag {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  padding: 9px 12px;
  border-radius: 12px;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-divider);
  color: var(--vp-c-text-2);
  margin-bottom: 12px;
}

.bpx-mount-label {
  flex-shrink: 0;
  font-weight: 700;
  color: var(--vp-c-text-1);
}

.bpx-mount-val {
  min-width: 0;
}

.bpx-highlights {
  list-style: none !important;
  padding: 0 !important;
  margin: 0 0 18px !important;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 12px;
  color: var(--vp-c-text-2);
}

.bpx-highlights li {
  margin: 0 !important;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.hl-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  opacity: 0.85;
}

.bpx-recipe-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid var(--vp-c-divider);
  margin-top: auto;
}

.bpx-btn {
  cursor: pointer;
  border-radius: 999px;
  padding: 7px 14px;
  font-size: 12.5px;
  font-weight: 600;
  transition: transform 0.16s ease, opacity 0.16s ease, border-color 0.16s ease;
}

.bpx-btn-primary {
  background: linear-gradient(135deg, #eb528c 0%, #7d63ff 100%);
  color: #fff;
  border: 1px solid transparent;
  box-shadow: 0 6px 16px rgba(224, 82, 136, 0.22);
}

.bpx-btn-primary:hover {
  transform: translateY(-1px);
  opacity: 0.94;
}

.bpx-btn-ghost {
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-1);
  border: 1px solid var(--vp-c-divider);
}

.bpx-btn-ghost:hover {
  border-color: var(--vp-c-brand-1);
  color: var(--vp-c-brand-1);
}

.bpx-detail-link {
  margin-left: auto;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--vp-c-text-2) !important;
  text-decoration: none !important;
  transition: color 0.15s ease;
}

.bpx-detail-link:hover {
  color: var(--vp-c-brand-1) !important;
}
</style>
