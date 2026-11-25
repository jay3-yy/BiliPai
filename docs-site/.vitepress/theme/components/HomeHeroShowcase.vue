<script setup lang="ts">
import { computed, ref } from 'vue'
import { withBase } from 'vitepress'

type MaterialStyle = 'glass' | 'miuix' | 'md3'

const activeMaterial = ref<MaterialStyle>('glass')

const materials: Array<{ id: MaterialStyle; label: string; desc: string }> = [
  { id: 'glass', label: '液态玻璃', desc: '实时光学折射 · 动态高光边缘 · Android 13+' },
  { id: 'miuix', label: 'Miuix', desc: '柔和多层容器 · 丝滑物理弹簧 · 澎湃质感' },
  { id: 'md3', label: 'Material 3', desc: 'Tonal 色阶表面 · 动态取色 · 原生排版' },
]

const currentMeta = computed(
  () => materials.find((item) => item.id === activeMaterial.value) ?? materials[0]
)
</script>

<template>
  <div class="hero-stage">
    <div class="hero-aura aura-pink" aria-hidden="true" />
    <div class="hero-aura aura-blue" aria-hidden="true" />

    <div class="stage-shell" :class="`mat-${activeMaterial}`">
      <div class="stage-topbar">
        <div class="stage-brand-dot">
          <span class="dot-live" />
          <span>.bpx v1 宿主渲染预览</span>
        </div>
        <div class="mat-switcher" role="tablist" aria-label="切换三材质预览">
          <button
            v-for="m in materials"
            :key="m.id"
            type="button"
            role="tab"
            :aria-selected="activeMaterial === m.id"
            class="mat-tab"
            :class="{ active: activeMaterial === m.id }"
            @click="activeMaterial = m.id"
          >
            {{ m.label }}
          </button>
        </div>
      </div>

      <!-- Simulated native extension slot cards -->
      <div class="stage-canvas">
        <div class="slot-card companion-card">
          <div class="slot-head">
            <span class="slot-badge">detail.companion</span>
            <span class="slot-meta">QuickJS 沙箱 · 0ms 帧阻塞</span>
          </div>
          <div class="slot-title">AI 省流课代表 · 3 点速览</div>
          <ul class="slot-bullets">
            <li><span class="time-chip">00:42</span> 对比三材质光学表面与字阶正交分工</li>
            <li><span class="time-chip">03:18</span> 一句话生成 .bpx，离线 Deflate 压入二维码</li>
            <li><span class="time-chip">06:05</span> 严格白名单联网，完全隔离登录态 Cookie</li>
          </ul>
        </div>

        <div class="slot-row">
          <div class="slot-card mini-card">
            <div class="mini-kicker">feed.pipe</div>
            <div class="mini-title">去标题党过滤</div>
            <div class="mini-stat">已净化 14 条营销标题</div>
          </div>
          <div class="slot-card mini-card">
            <div class="mini-kicker">player.track</div>
            <div class="mini-title">外挂弹幕混流</div>
            <div class="mini-stat">+1,280 条同步轨道</div>
          </div>
        </div>

        <div class="stage-dock">
          <span class="dock-pill active">首页</span>
          <span class="dock-pill">动态</span>
          <span class="dock-pill">工坊</span>
          <a :href="withBase('/playground')" class="dock-cta">立即试玩 →</a>
        </div>
      </div>

      <div class="stage-caption">
        <span class="caption-name">{{ currentMeta.label }}</span>
        <span class="caption-sep">·</span>
        <span class="caption-desc">{{ currentMeta.desc }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hero-stage {
  position: relative;
  width: 100%;
  max-width: 470px;
  margin: 0 auto;
  padding: 6px 0;
}

.hero-aura {
  position: absolute;
  border-radius: 999px;
  filter: blur(56px);
  pointer-events: none;
  z-index: 0;
  opacity: 0.48;
}

.aura-pink {
  width: 240px;
  height: 240px;
  top: -26px;
  right: -18px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.58), rgba(224, 82, 136, 0));
}

.aura-blue {
  width: 220px;
  height: 220px;
  bottom: -24px;
  left: -20px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.52), rgba(59, 130, 246, 0));
}

.dark .hero-aura {
  opacity: 0.56;
}

.stage-shell {
  position: relative;
  z-index: 1;
  border-radius: 26px;
  padding: 16px;
  transition: background 0.28s ease, border-color 0.28s ease, box-shadow 0.28s ease, border-radius 0.28s ease;
}

/* Liquid Glass */
.stage-shell.mat-glass {
  background: linear-gradient(155deg, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0.54));
  border: 1px solid rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(26px) saturate(185%);
  -webkit-backdrop-filter: blur(26px) saturate(185%);
  box-shadow:
    0 24px 56px rgba(15, 23, 42, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.92),
    inset 0 -1px 0 rgba(224, 82, 136, 0.12);
}

.dark .stage-shell.mat-glass {
  background: linear-gradient(155deg, rgba(32, 36, 50, 0.72), rgba(18, 21, 32, 0.62));
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow:
    0 28px 64px rgba(0, 0, 0, 0.52),
    inset 0 1px 0 rgba(255, 255, 255, 0.22),
    inset 0 -1px 0 rgba(255, 122, 168, 0.14);
}

/* Miuix */
.stage-shell.mat-miuix {
  border-radius: 28px;
  background: #f5f6fa;
  border: 1px solid rgba(15, 23, 42, 0.07);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.08);
}

.dark .stage-shell.mat-miuix {
  background: #171920;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 22px 48px rgba(0, 0, 0, 0.45);
}

/* Material 3 */
.stage-shell.mat-md3 {
  border-radius: 22px;
  background: #fff7fa;
  border: 1px solid rgba(224, 82, 136, 0.22);
  box-shadow: 0 12px 28px rgba(224, 82, 136, 0.08);
}

.dark .stage-shell.mat-md3 {
  background: #211920;
  border: 1px solid rgba(255, 148, 185, 0.22);
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.42);
}

.stage-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.stage-brand-dot {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--vp-c-text-2);
}

.dot-live {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.22);
}

.mat-switcher {
  display: inline-flex;
  padding: 3px;
  border-radius: 999px;
  background: rgba(120, 128, 148, 0.12);
  border: 1px solid rgba(120, 128, 148, 0.14);
}

.mat-tab {
  cursor: pointer;
  border: none;
  background: transparent;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--vp-c-text-2);
  transition: background 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
}

.mat-tab.active {
  background: var(--vp-c-bg);
  color: var(--vp-c-brand-1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.dark .mat-tab.active {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}

.stage-canvas {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.slot-card {
  border-radius: 18px;
  padding: 14px;
  transition: background 0.25s ease, border-color 0.25s ease, border-radius 0.25s ease;
}

.mat-glass .slot-card {
  background: rgba(255, 255, 255, 0.56);
  border: 1px solid rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.dark .mat-glass .slot-card {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.11);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.mat-miuix .slot-card {
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.05);
}

.dark .mat-miuix .slot-card {
  background: #222530;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.mat-md3 .slot-card {
  border-radius: 16px;
  background: #fdeef4;
  border: 1px solid transparent;
}

.dark .mat-md3 .slot-card {
  background: #2f232c;
  border: 1px solid rgba(255, 148, 185, 0.12);
}

.slot-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.slot-badge {
  font-family: var(--vp-font-family-mono);
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
}

.slot-meta {
  font-size: 11px;
  color: var(--vp-c-text-3);
}

.slot-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--vp-c-text-1);
  margin-bottom: 8px;
}

.slot-bullets {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--vp-c-text-2);
}

.slot-bullets li {
  display: flex;
  align-items: center;
  gap: 7px;
}

.time-chip {
  flex-shrink: 0;
  font-family: var(--vp-font-family-mono);
  font-size: 10.5px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 6px;
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
}

.slot-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.mini-card {
  padding: 12px;
}

.mini-kicker {
  font-family: var(--vp-font-family-mono);
  font-size: 10.5px;
  color: var(--vp-c-brand-1);
  font-weight: 600;
}

.mini-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--vp-c-text-1);
  margin: 3px 0 2px;
}

.mini-stat {
  font-size: 11.5px;
  color: var(--vp-c-text-2);
}

.stage-dock {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 999px;
  background: rgba(120, 128, 148, 0.1);
  border: 1px solid rgba(120, 128, 148, 0.14);
}

.mat-glass .stage-dock {
  background: rgba(255, 255, 255, 0.62);
  border-color: rgba(255, 255, 255, 0.85);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.dark .mat-glass .stage-dock {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.15);
}

.dock-pill {
  font-size: 11.5px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 999px;
  color: var(--vp-c-text-2);
}

.dock-pill.active {
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
}

.dock-cta {
  margin-left: auto;
  font-size: 11.5px;
  font-weight: 700;
  padding: 5px 12px;
  border-radius: 999px;
  background: linear-gradient(135deg, #f05992, #7c6bff);
  color: #fff !important;
  text-decoration: none !important;
  transition: transform 0.18s ease, opacity 0.18s ease;
}

.dock-cta:hover {
  transform: translateY(-1px);
  opacity: 0.94;
}

.stage-caption {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--vp-c-divider);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 11.5px;
  color: var(--vp-c-text-2);
}

.caption-name {
  font-weight: 700;
  color: var(--vp-c-text-1);
}

.caption-sep {
  opacity: 0.5;
}
</style>
