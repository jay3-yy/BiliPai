<script setup lang="ts">
import DefaultTheme from 'vitepress/theme'
import HomeHeroShowcase from './components/HomeHeroShowcase.vue'

const { Layout } = DefaultTheme
</script>

<template>
  <Layout>
    <template #home-hero-info-before>
      <div class="bpx-hero-kicker">
        <span class="bpx-kicker-dot" aria-hidden="true" />
        <span>Beta 公开测试</span>
        <span class="bpx-kicker-sep">·</span>
        <span>.bpx v1 扩展生态</span>
      </div>
    </template>

    <template #home-hero-image>
      <HomeHeroShowcase />
    </template>
  </Layout>
</template>
