---
layout: home

hero:
  name: "BiliPai 扩展工坊"
  text: "一句话造扩展，扫码即上真机"
  tagline: "无需配置 Android 开发环境，也无需编写复杂 UI。你只管描述想法与逻辑，BiliPai 自动赋予它 Material 3、Miuix 与液态玻璃的原生质感。"
  actions:
    - theme: brand
      text: "打开在线校验扫码台"
      link: /playground
    - theme: alt
      text: "浏览六大成品食谱"
      link: /cookbook/
    - theme: alt
      text: "获取 AI 上下文 (llms-full)"
      link: /llms-full.txt
---

<div class="vp-doc bpx-home-container">

<section class="bpx-section" style="margin-top: 12px;">
  <div class="bpx-pillar-grid">
    <div class="bpx-glass-card bpx-pillar-card">
      <div class="bpx-pillar-metric">0 行 UI 代码</div>
      <div class="bpx-pillar-title">插件产出语义，宿主垄断三材质质感</div>
      <p class="bpx-pillar-desc">扩展只声明结构化数据与插槽挂载，圆角、字阶、毛玻璃折射与高帧率门控全部由 BiliPai 原生接管。</p>
    </div>
    <div class="bpx-glass-card bpx-pillar-card">
      <div class="bpx-pillar-metric">≤ 2 KB 离线扫码</div>
      <div class="bpx-pillar-title">免服务器、免网盘，相机一扫即装</div>
      <p class="bpx-pillar-desc">规则与轻量脚本在浏览器本地 Deflate 压缩进 <code>bilipai://</code> 二维码，分享给朋友扫一下立刻生效。</p>
    </div>
    <div class="bpx-glass-card bpx-pillar-card">
      <div class="bpx-pillar-metric">0 Cookie 接触</div>
      <div class="bpx-pillar-title">QuickJS 沙箱 + 显式域名白名单</div>
      <p class="bpx-pillar-desc">扩展绝不接触 B 站登录态，安装前通过 Trust Sheet 逐项透明公示挂载位与外部联网域名。</p>
    </div>
  </div>
</section>

<section class="bpx-section">
  <div class="bpx-section-eyebrow">Workflow · 四步共创飞轮</div>
  <h2 class="bpx-section-heading">为什么人人都能参与 BiliPai 共创？</h2>
  <p class="bpx-section-lead">从一句大白话到手机真机运行，全程在浏览器内闭环完成。</p>

  <div class="bpx-step-strip">
    <div class="bpx-step-item">
      <div class="bpx-step-num">STEP 01</div>
      <div class="bpx-step-title">选场景，复制防幻觉 AI 配方</div>
      <p class="bpx-step-desc">无需通读字段字典。点选你想增强的宿主位置，一键复制精准 Prompt 发给 DeepSeek / Claude / 豆包。</p>
    </div>
    <div class="bpx-step-item">
      <div class="bpx-step-num">STEP 02</div>
      <div class="bpx-step-title">粘回工坊体检 &amp; 预览挂载位</div>
      <p class="bpx-step-desc">纯前端实时核验 Schema、域名白名单与三条合规红线；发现问题可一键复制修复指令回喂 AI。</p>
    </div>
    <div class="bpx-step-item">
      <div class="bpx-step-num">STEP 03</div>
      <div class="bpx-step-title">离线压缩二维码，真机秒装</div>
      <p class="bpx-step-desc">体检通过即刻生成离线 <code>bilipai://</code> 安装码，用手机相机或 BiliPai 扫一扫直接导入体验。</p>
    </div>
    <div class="bpx-step-item">
      <div class="bpx-step-num">STEP 04</div>
      <div class="bpx-step-title">零 Git 投稿，点亮创作者名片</div>
      <p class="bpx-step-desc">不会命令行也能通过表单提交至官方源，机器人自动校验转 PR，合入后在 App 内直达你的 B 站主页。</p>
    </div>
  </div>
</section>

<section class="bpx-section">
  <div class="bpx-section-eyebrow">Starter Cookbook · 成品样板库</div>
  <h2 class="bpx-section-heading">六大明星成品食谱</h2>
  <p class="bpx-section-lead">每一道食谱都是完整可运行的 <code>.bpx</code> 实战样板。点开即可在工坊修改参数生成你的专属二维码，或复制 AI 改写模板衍生新玩法。</p>

  <CookbookGrid />
</section>

</div>
