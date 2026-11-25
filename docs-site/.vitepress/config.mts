import { defineConfig } from 'vitepress'

const rawBase = process.env.VITEPRESS_BASE ?? '/BiliPai/'
const normalizedBase = rawBase.startsWith('/') ? rawBase : `/${rawBase}`
const base = normalizedBase.endsWith('/') ? normalizedBase : `${normalizedBase}/`

export default defineConfig({
  base,
  lang: 'zh-CN',
  title: 'BiliPai 扩展工坊 (Beta)',
  description: 'Beta 公开测试 · 一句话用 AI 造扩展 · 浏览器本地体检 · 手机扫码即装的三材质原生扩展生态',
  cleanUrls: true,
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: `${base}logo.svg` }],
    ['meta', { name: 'theme-color', content: '#e05288' }],
  ],
  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'BiliPai 扩展工坊',
    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索食谱与契约', buttonAriaLabel: '搜索文档' },
          modal: {
            noResultsText: '没有找到相关内容',
            resetButtonTitle: '清空查询',
            footer: { selectText: '跳转', navigateText: '切换', closeText: '关闭' }
          }
        }
      }
    },
    nav: [
      { text: '首页与食谱', link: '/' },
      { text: '在线校验扫码台', link: '/playground' },
      { text: '六大明星食谱', link: '/cookbook/' },
      { text: '场景指南', link: '/guides/quickstart' },
      { text: '零门槛投稿', link: '/community/submit' },
      {
        text: '契约与 AI 资产',
        items: [
          { text: '.bpx v1 契约字典', link: '/reference/spec' },
          { text: 'AI 开发资产 (llms.txt)', link: '/reference/llms' }
        ]
      }
    ],
    sidebar: {
      '/cookbook/': [
        {
          text: '六大明星食谱（Cookbook）',
          items: [
            { text: '食谱总览与选型', link: '/cookbook/' },
            { text: '1. 去标题党与热点折叠', link: '/cookbook/declickbait' },
            { text: '2. 视频页 AI 省流课代表', link: '/cookbook/ai-summary' },
            { text: '3. 弹弹Play 外挂弹幕混流', link: '/cookbook/external-danmaku' },
            { text: '4. 「冷门宝藏」推荐透镜', link: '/cookbook/hidden-gems-lens' },
            { text: '5. 评论区「指路课代表」置顶', link: '/cookbook/comment-rep' },
            { text: '6. 三材质光学主题装扮', link: '/cookbook/three-material-skin' }
          ]
        }
      ],
      '/guides/': [
        {
          text: '从零到真机',
          items: [
            { text: '3 分钟破冰上手（AI / 在线 / CLI）', link: '/guides/quickstart' },
            { text: '六大原位插槽开发指南', link: '/guides/slots' }
          ]
        }
      ],
      '/community/': [
        {
          text: '共创飞轮与分发',
          items: [
            { text: '零 Git 表单投稿与自建源', link: '/community/submit' }
          ]
        }
      ],
      '/reference/': [
        {
          text: '契约规范与 AI 资产',
          items: [
            { text: '.bpx v1 契约与 Schema 字典', link: '/reference/spec' },
            { text: 'llms.txt 与 IDE Skill 指南', link: '/reference/llms' }
          ]
        }
      ]
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/jay3-yy/BiliPai' }
    ],
    outline: {
      level: [2, 3],
      label: '本页目录'
    },
    footer: {
      message: '插件产出结构化语义 · 宿主垄断 MD3 / Miuix / 液态玻璃质感与帧率安全',
      copyright: 'BiliPai Open Extension Ecosystem (.bpx v1)'
    }
  }
})
