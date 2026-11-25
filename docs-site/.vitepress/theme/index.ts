import DefaultTheme from 'vitepress/theme'
import type { Theme } from 'vitepress'
import Layout from './Layout.vue'
import CookbookGrid from './components/CookbookGrid.vue'
import BpxPlayground from './components/BpxPlayground.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  Layout,
  enhanceApp({ app }) {
    app.component('CookbookGrid', CookbookGrid)
    app.component('BpxPlayground', BpxPlayground)
  }
} satisfies Theme
