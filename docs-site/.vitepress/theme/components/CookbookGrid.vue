<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, withBase } from 'vitepress'
import { STARTER_RECIPES, buildCapsuleAiPrompt, type BpxRecipe } from '../bpxRecipes'

const router = useRouter()
const copiedId = ref<string | null>(null)

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
  <div class="bpx-cookbook-grid">
    <article v-for="recipe in STARTER_RECIPES" :key="recipe.id" class="bpx-glass-card bpx-recipe-card">
      <div class="bpx-recipe-top">
        <span class="bpx-pill">{{ recipe.slotLabel }}</span>
        <span class="bpx-lines-badge">{{ recipe.lines }} 行 · .bpx.{{ recipe.format }}</span>
      </div>

      <h3 class="bpx-recipe-title">{{ recipe.title }}</h3>
      <p class="bpx-recipe-summary">{{ recipe.summary }}</p>

      <div class="bpx-mount-tag">
        <span class="bpx-mount-label">原位挂载：</span>{{ recipe.mountPreview }}
      </div>

      <ul class="bpx-highlights">
        <li v-for="h in recipe.highlights" :key="h">✦ {{ h }}</li>
      </ul>

      <div class="bpx-recipe-actions">
        <button type="button" class="bpx-btn bpx-btn-primary" @click="openInPlayground(recipe)">
          ⚡ 在工坊打开并扫码
        </button>
        <button type="button" class="bpx-btn bpx-btn-ghost" @click="copyAiRemixPrompt(recipe)">
          {{ copiedId === recipe.id ? '✓ 已复制 AI 配方' : '🤖 复制 AI 改写模板' }}
        </button>
        <a :href="withBase(recipe.docLink)" class="bpx-detail-link">详解 →</a>
      </div>
    </article>
  </div>
</template>

<style scoped>
.bpx-cookbook-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 20px;
  margin-top: 18px;
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

.bpx-lines-badge {
  font-size: 12px;
  color: var(--vp-c-text-3);
  font-family: var(--vp-font-family-mono);
}

.bpx-recipe-title {
  margin: 14px 0 8px !important;
  font-size: 18px !important;
  line-height: 1.35 !important;
  border: none !important;
  padding: 0 !important;
}

.bpx-recipe-summary {
  margin: 0 0 12px !important;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--vp-c-text-2);
  flex: 1;
}

.bpx-mount-tag {
  font-size: 12px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-2);
  margin-bottom: 10px;
}

.bpx-mount-label {
  font-weight: 600;
  color: var(--vp-c-text-1);
}

.bpx-highlights {
  list-style: none !important;
  padding: 0 !important;
  margin: 0 0 16px !important;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 12px;
  color: var(--vp-c-text-2);
}

.bpx-highlights li {
  margin: 0 !important;
}

.bpx-recipe-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
}

.bpx-btn {
  cursor: pointer;
  border-radius: 999px;
  padding: 7px 14px;
  font-size: 12.5px;
  font-weight: 600;
  transition: opacity 0.15s ease, background 0.15s ease;
}

.bpx-btn-primary {
  background: var(--vp-c-brand-1);
  color: #fff;
  border: 1px solid transparent;
}

.bpx-btn-primary:hover {
  opacity: 0.9;
}

.bpx-btn-ghost {
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-1);
  border: 1px solid var(--vp-c-divider);
}

.bpx-btn-ghost:hover {
  border-color: var(--vp-c-brand-1);
}

.bpx-detail-link {
  margin-left: auto;
  font-size: 12.5px;
  font-weight: 600;
  text-decoration: none !important;
}
</style>
